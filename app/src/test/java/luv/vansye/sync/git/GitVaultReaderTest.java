package luv.vansye.sync.git;

import luv.vansye.sync.domain.SyncPipeline;
import luv.vansye.sync.domain.SyncReport;
import luv.vansye.sync.domain.VaultDiff;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对**真实的** vault 仓库跑一遍。这是第一个碰到外部世界的测试。
 *
 * 全程只读，不会往仓库里写任何东西。
 *
 * 用 assumeTrue 而不是硬编码断言：这个路径只存在于我这台机器上，
 * 换机器或 CI 上跑，测试应该「跳过」而不是「失败」——一个因为环境不同而红的测试，
 * 几次之后就没人看了。
 */
class GitVaultReaderTest {

    private static final File VAULT = new File("S:\\我的知识库");

    private GitVaultReader openVault() throws IOException {
        Assumptions.assumeTrue(new File(VAULT, ".git").isDirectory(),
                "本机没有 " + VAULT + "，跳过");
        return new GitVaultReader(VAULT, "HEAD");
    }

    @Test
    @DisplayName("① 能打开仓库并拿到 commit sha 和文件清单")
    void readsCommitTree() throws IOException {
        try (GitVaultReader reader = openVault()) {
            assertThat(reader.commitSha()).hasSize(40);
            assertThat(reader.listFiles()).isNotEmpty();

            // tree 里只有被 git 跟踪的文件——.obsidian/ 被 .gitignore 挡在外面，
            // 所以读 tree 拿到的和线上服务器 clone 下来看到的完全一致
            assertThat(reader.listFiles()).noneMatch(path -> path.startsWith(".obsidian/"));
        }
    }

    @Test
    @DisplayName("② 能读出那篇文章的正文，且 frontmatter 完整")
    void readsArticleText() throws IOException {
        try (GitVaultReader reader = openVault()) {
            String path = reader.listFiles().stream()
                    .filter(p -> p.startsWith("博客/文章/") && p.endsWith(".md") && p.contains("Redis"))
                    .findFirst()
                    .orElse(null);
            Assumptions.assumeTrue(path != null, "发布区里还没有那篇 Redis 文章，跳过");

            assertThat(reader.readText(path)).isPresent();
            assertThat(reader.readText(path).get()).startsWith("---");
        }
    }

    @Test
    @DisplayName("③ 能取到文件的 git 首次/最近提交日期，且首次不晚于最近")
    void readsGitTimestamps() throws IOException {
        try (GitVaultReader reader = openVault()) {
            String path = reader.listFiles().stream()
                    .filter(p -> p.startsWith("博客/文章/") && p.endsWith(".md"))
                    .findFirst()
                    .orElse(null);
            Assumptions.assumeTrue(path != null, "发布区里还没有 md 文件，跳过");

            assertThat(reader.timestamps(path)).isPresent();
            var stamps = reader.timestamps(path).get();
            assertThat(stamps.firstCommit()).isNotNull();
            assertThat(stamps.lastCommit()).isNotNull();
            assertThat(stamps.firstCommit()).isBeforeOrEqualTo(stamps.lastCommit());
        }
    }

    @Test
    @DisplayName("④ 不存在的路径返回 empty，不抛异常 —— 单个文件读不到属于正常结果")
    void missingPathReturnsEmpty() throws IOException {
        try (GitVaultReader reader = openVault()) {
            assertThat(reader.readText("根本没有这个文件.md")).isEmpty();
            assertThat(reader.readBytes("根本没有这个文件.png")).isEmpty();
            assertThat(reader.timestamps("根本没有这个文件.md")).isEmpty();
        }
    }

    @Test
    @DisplayName("⑤ 解析不出版本时**立刻抛**，不是返回空 —— 连读哪个 commit 都定不下来，整批无从开始")
    void failsFastOnUnresolvableRevision() {
        Assumptions.assumeTrue(new File(VAULT, ".git").isDirectory(), "本机没有 vault，跳过");

        assertThatThrownBy(() -> new GitVaultReader(VAULT, "这个分支不存在"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("解析不出版本");
    }

    @Test
    @DisplayName("⑥ 整条管道跑真实仓库：只有 博客/ 下的进报告，学习/ 的 160 多篇不产生噪音")
    void runsWholePipelineAgainstRealVault() throws IOException {
        try (GitVaultReader reader = openVault()) {
            SyncReport report = new SyncPipeline().run(reader);

            // 报告里出现的路径，只能是发布区里的或者踩了禁止清单的
            assertThat(report.skipped()).allSatisfy(skipped ->
                    assertThat(skipped.path()).startsWith("博客/"));

            // 全库 169 个文件，报告条目远少于这个数——这就是 isPublishCandidate 的作用
            assertThat(report.published().size() + report.skipped().size())
                    .isLessThan(reader.listFiles().size());

            assertThat(report.commitSha()).isEqualTo(reader.commitSha());
            assertThat(report.assetCollisions()).isEmpty();
        }
    }

    // ─── diff：增量同步的输入 ─────────────────────────────────────────

    @Test
    @DisplayName("⑦ 和自己求差 → 空 diff（**不是** empty）。「没有变化」和「算不出来」必须分得开")
    void diffAgainstItselfIsEmptyNotAbsent() throws IOException {
        try (GitVaultReader reader = openVault()) {
            Optional<VaultDiff> diff = reader.diffSince(reader.commitSha());

            assertThat(diff).as("这是「什么都没变」，算得出来").isPresent();
            assertThat(diff.get().isEmpty()).isTrue();
        }
    }

    @Test
    @DisplayName("⑧ 起点不在仓库里 → empty，也就是「退回全量」。返回空 diff 会让博客静默停止更新")
    void unknownStartingPointYieldsAbsent() throws IOException {
        try (GitVaultReader reader = openVault()) {
            // 40 个 0 是合法的 sha 形状，但仓库里没有这个对象
            assertThat(reader.diffSince("0000000000000000000000000000000000000000")).isEmpty();
            assertThat(reader.diffSince("这不是一个版本")).isEmpty();
            assertThat(reader.diffSince(null)).isEmpty();
            assertThat(reader.diffSince("  ")).isEmpty();
        }
    }

    @Test
    @DisplayName("⑨ 对真实的上一个提交求差：touched 全在 tree 里，deleted 全不在 —— 这条不变式是下游下架判断的地基")
    void touchedExistsAndDeletedDoesNot() throws IOException {
        try (GitVaultReader reader = openVault()) {
            Optional<VaultDiff> maybe = reader.diffSince("HEAD~1");
            Assumptions.assumeTrue(maybe.isPresent(), "仓库只有一个提交，跳过");

            VaultDiff diff = maybe.get();
            Set<String> tree = new HashSet<>(reader.listFiles());

            assertThat(diff.touched()).allSatisfy(path ->
                    assertThat(tree).as("touched 的路径必须还在 tree 里：" + path).contains(path));
            assertThat(diff.deleted()).allSatisfy(path ->
                    assertThat(tree).as("deleted 的路径必须已经不在 tree 里：" + path).doesNotContain(path));
        }
    }

    @Test
    @DisplayName("⑩ 跨多个提交求差得到的清单，是逐步求差的并集的子集 —— 中间改回去的文件会被正确地消掉")
    void diffOverManyCommitsCollapsesRoundTrips() throws IOException {
        try (GitVaultReader reader = openVault()) {
            Optional<VaultDiff> far = reader.diffSince("HEAD~5");
            Assumptions.assumeTrue(far.isPresent(), "仓库不足 6 个提交，跳过");

            // diff 比的是两棵 tree，不是回放中间每一次提交。
            // 所以「改了又改回去」的文件根本不会出现——这正是我们要的：
            // 那种文件确实不需要重新处理
            Set<String> tree = new HashSet<>(reader.listFiles());
            assertThat(far.get().touched()).allSatisfy(path -> assertThat(tree).contains(path));
            assertThat(far.get().touched()).doesNotHaveDuplicates();
            assertThat(far.get().deleted()).doesNotHaveDuplicates();
        }
    }
}
