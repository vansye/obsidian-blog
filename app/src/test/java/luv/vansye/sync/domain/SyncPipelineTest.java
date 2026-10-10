package luv.vansye.sync.domain;

import luv.vansye.sync.domain.PostDraftAssembler.GitTimestamps;
import luv.vansye.sync.domain.SyncReport.RenderedPost;
import luv.vansye.sync.domain.SyncReport.SkippedFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 编排测试。规则本身在各自的类里已经测过了，这里只测「串起来对不对」+ 外部失败的处理。
 *
 * 用例 ④⑤⑥ 是这一部分才有的类型：它们测的不是「输入长什么样」，
 * 而是「外部世界坏掉时管道怎么反应」。
 */
class SyncPipelineTest {

    private static final String ARTICLE = "博客/文章/低空略过Redis的学习.md";

    private final SyncPipeline pipeline = new SyncPipeline();

    // ─── 编排：一篇完整的文章走完全程 ─────────────────────────────────

    @Test
    @DisplayName("① 一篇合格文章走完全程：字段、正文 HTML、图片 URL 都对")
    void runsFullPipelineForOneArticle() {
        byte[] png = {1, 2, 3};
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .sha("abc123")
                .text(ARTICLE, """
                        ---
                        title: 低空略过 Redis 的学习
                        tags: [Redis, 学习复盘]
                        publish: true
                        ---
                        正文开头。

                        ![[shot.png]]

                        详见 [[Redis持久化]]。
                        """)
                .binary("博客/文章/_assets/shot.png", png);

        SyncReport report = pipeline.run(vault);

        assertThat(report.commitSha()).isEqualTo("abc123");
        assertThat(report.published()).hasSize(1);

        RenderedPost post = report.published().get(0);
        assertThat(post.draft().title()).isEqualTo("低空略过 Redis 的学习");
        assertThat(post.draft().tags()).containsExactly("Redis", "学习复盘");
        assertThat(post.bodyHtml()).contains("<p>正文开头。</p>");
        assertThat(post.bodyHtml()).contains("<img src=\"/assets/");
        assertThat(post.bodyHtml()).contains("详见 Redis持久化。");   // 双链去了方括号
        assertThat(post.referencedAssets()).containsExactly("博客/文章/_assets/shot.png");
    }

    @Test
    @DisplayName("② git 时间在 frontmatter 缺 created/updated 时兜底")
    void fallsBackToGitTimestamps() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .text(ARTICLE, "---\npublish: true\n---\n正文。\n")
                .gitDates(ARTICLE, new GitTimestamps(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 2)));

        RenderedPost post = pipeline.run(vault).published().get(0);

        assertThat(post.draft().created()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(post.draft().updated()).isEqualTo(LocalDate.of(2026, 2, 2));
    }

    // ─── 日志的信噪比 ────────────────────────────────────────────────

    @Test
    @DisplayName("③ 学习/ 下的笔记不进 skipped —— 它们从来不是候选，逐条记录只会埋掉真问题")
    void doesNotReportFilesOutsidePublishArea() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .text("学习/JavaWeb/开发总结.md", "正文。")
                .text("学习/数据库/Redis学习小结.md", "正文。")
                .text("博客/文章/忘了写publish.md", "---\ntitle: A\n---\n正文。\n");

        SyncReport report = pipeline.run(vault);

        assertThat(report.published()).isEmpty();
        assertThat(report.skipped()).hasSize(1);
        assertThat(report.skipped().get(0).path()).isEqualTo("博客/文章/忘了写publish.md");
        assertThat(report.skipped().get(0).reason()).isEqualTo(PublicationDecision.NOT_PUBLISHED);
    }

    @Test
    @DisplayName("④ 踩了禁止清单的一律记录，哪怕在 博客/ 之外 —— 那是护栏在响，必须看见")
    void alwaysReportsForbiddenPaths() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .text("个人/个人信息/玄学/八字结果.md", "---\npublish: true\n---\n内容。\n");

        SyncReport report = pipeline.run(vault);

        assertThat(report.skipped()).hasSize(1);
        assertThat(report.skipped().get(0).reason()).isEqualTo(PublicationDecision.PATH_FORBIDDEN);
        assertThat(report.skipped().get(0).retryable()).isFalse();
    }

    @Test
    @DisplayName("⑤ YAML 挂了时报的是语法错和行号，不是「没写 publish」")
    void reportsYamlErrorRatherThanMissingPublish() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .text("博客/文章/写坏了.md", "---\ntitle: [没关括号\npublish: true\n---\n正文。\n");

        SkippedFile skipped = pipeline.run(vault).skipped().get(0);

        assertThat(skipped.reason()).isEqualTo(Frontmatter.ERR_YAML);
        assertThat(skipped.detail()).isNotBlank();
        assertThat(skipped.retryable()).isFalse();      // 要去改文件，重试没用
    }

    // ─── 外部失败：这一部分才有的用例类型 ─────────────────────────────

    @Test
    @DisplayName("⑥ 文件这次读不到 → retryable=true，和「写错了」区分开")
    void marksReadFailureAsRetryable() {
        InMemoryVaultReader vault = new InMemoryVaultReader().unreadable("博客/文章/读不到.md");

        SkippedFile skipped = vault.readText("博客/文章/读不到.md").isPresent()
                ? null
                : pipeline.run(vault).skipped().get(0);

        assertThat(skipped).isNotNull();
        assertThat(skipped.reason()).isEqualTo(SkippedFile.READ_FAILED);
        assertThat(skipped.retryable()).isTrue();
    }

    @Test
    @DisplayName("⑦ 一篇坏掉不能拖垮整批：坏的记下来，好的照样发出去")
    void oneBadFileDoesNotAbortTheBatch() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .unreadable("博客/文章/读不到.md")
                .text("博客/文章/写坏了.md", "---\ntitle: [坏\n---\n正文。\n")
                .text("博客/文章/好的.md", "---\ntitle: 好文章\npublish: true\n---\n正文。\n");

        SyncReport report = pipeline.run(vault);

        assertThat(report.published()).hasSize(1);
        assertThat(report.published().get(0).draft().title()).isEqualTo("好文章");
        assertThat(report.skipped()).hasSize(2);
    }

    @Test
    @DisplayName("⑧ 附件在 个人/ 下 → 不进索引，引用它的文章记 missingAssets（C5 的落点）")
    void neverIndexesAssetOutsidePublishArea() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .binary("个人/证件/身份证.png", new byte[]{9, 9, 9})
                .text(ARTICLE, "---\npublish: true\n---\n![[身份证.png]]\n");

        RenderedPost post = pipeline.run(vault).published().get(0);

        assertThat(post.missingAssets()).containsExactly("身份证.png");
        assertThat(post.bodyHtml()).doesNotContain("<img");
    }

    @Test
    @DisplayName("⑨ ![[笔记]] 被拦下并记录，HTML 里不会出现那篇笔记的内容")
    void blocksNoteTransclusionAndRecordsIt() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .text("学习/数据库/Redis学习小结.md", "这是私密笔记的正文，绝不能出现在页面上。")
                .text(ARTICLE, "---\npublish: true\n---\n参考 ![[Redis学习小结]]\n");

        RenderedPost post = pipeline.run(vault).published().get(0);

        assertThat(post.blockedEmbeds()).containsExactly("Redis学习小结");
        assertThat(post.bodyHtml()).doesNotContain("私密笔记的正文");
    }

    @Test
    @DisplayName("⑩ 空仓库不崩，返回空报告")
    void handlesEmptyRepository() {
        SyncReport report = pipeline.run(new InMemoryVaultReader());

        assertThat(report.published()).isEmpty();
        assertThat(report.skipped()).isEmpty();
        assertThat(report.assetCollisions()).isEmpty();
    }

    // ─── 对抗性：跨文件的冲突，单篇处理时看不出来 ─────────────────────

    @Test
    @DisplayName("⑪ 两篇写了同一个 slug → 两篇都不发，不能等到入库撞 unique 约束才发现")
    void dropsBothPostsOnDuplicateSlug() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .text("博客/文章/a.md", "---\ntitle: A\nslug: same\npublish: true\n---\nA 的正文。\n")
                .text("博客/文章/b.md", "---\ntitle: B\nslug: same\npublish: true\n---\nB 的正文。\n")
                .text("博客/文章/c.md", "---\ntitle: C\nslug: other\npublish: true\n---\nC 的正文。\n");

        SyncReport report = pipeline.run(vault);

        assertThat(report.published()).hasSize(1);
        assertThat(report.published().get(0).draft().title()).isEqualTo("C");
        assertThat(report.skipped()).hasSize(2);
        assertThat(report.skipped()).allSatisfy(skipped -> {
            assertThat(skipped.reason()).isEqualTo(SkippedFile.DUPLICATE_SLUG);
            assertThat(skipped.detail()).contains("same");
            assertThat(skipped.retryable()).isFalse();
        });
    }

    @Test
    @DisplayName("⑫ 多篇都没写 slug 不算冲突 —— null 不参与判重")
    void missingSlugIsNotACollision() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .text("博客/文章/a.md", "---\npublish: true\n---\nA。\n")
                .text("博客/文章/b.md", "---\npublish: true\n---\nB。\n");

        SyncReport report = pipeline.run(vault);

        assertThat(report.published()).hasSize(2);
        assertThat(report.skipped()).isEmpty();
    }

    @Test
    @DisplayName("⑬ 路径穿越的文件即使带 publish: true 也发不出去")
    void rejectsPathTraversalEndToEnd() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .text("博客/文章/../个人/八字结果.md", "---\npublish: true\n---\n私密内容。\n");

        SyncReport report = pipeline.run(vault);

        assertThat(report.published()).isEmpty();
        assertThat(report.skipped()).hasSize(1);
        assertThat(report.skipped().get(0).reason()).isEqualTo(PublicationDecision.PATH_FORBIDDEN);
    }

    // ─── 增量：负责范围 ───────────────────────────────────────────────

    @Test
    @DisplayName("⑭ 增量只处理变更清单里的文件，没变的那篇不进报告 —— 但附件索引仍然是全的")
    void incrementalProcessesOnlyChangedFiles() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .text("博客/文章/变了.md", "---\npublish: true\n---\n新正文。\n\n![[shot.png]]\n")
                .text("博客/文章/没变.md", "---\npublish: true\n---\n老正文。\n")
                .binary("博客/文章/_assets/shot.png", new byte[]{1, 2, 3});

        SyncReport report = pipeline.runIncremental(vault,
                new VaultDiff(List.of("博客/文章/变了.md"), List.of()), List.of());

        assertThat(report.coverage()).isEqualTo(SyncReport.Coverage.CHANGED_FILES);
        assertThat(report.published()).hasSize(1);
        assertThat(report.published().get(0).draft().vaultPath()).isEqualTo("博客/文章/变了.md");

        // 索引扫的是全量，所以这次没进清单的附件照样解析得到
        assertThat(report.published().get(0).referencedAssets())
                .containsExactly("博客/文章/_assets/shot.png");
        assertThat(report.published().get(0).missingAssets()).isEmpty();
    }

    @Test
    @DisplayName("⑮ 被删掉的文件**不去读**：只进负责范围，不产生 read_failed —— 这是「被删」和「读不到」的分界")
    void deletedFilesAreNotRead() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .text("博客/文章/还在.md", "---\npublish: true\n---\n正文。\n");

        SyncReport report = pipeline.runIncremental(vault,
                new VaultDiff(List.of("博客/文章/还在.md"), List.of("博客/文章/被删了.md")), List.of());

        assertThat(report.published()).hasSize(1);
        // 如果去读了被删的那个文件，这里会多一条 read_failed（retryable），
        // 而 retryable 的路径恰恰是**不许下架**的 —— 一读就把该下架的保护起来了
        assertThat(report.skipped()).isEmpty();
        assertThat(report.retryableFailurePaths()).isEmpty();
        assertThat(report.scope()).contains("博客/文章/被删了.md");
    }

    @Test
    @DisplayName("⑯ 一个路径同时出现在 touched 和 deleted 里 → 按已删除处理，不去读它")
    void deletedWinsOverTouched() {
        SyncReport report = pipeline.runIncremental(new InMemoryVaultReader(),
                new VaultDiff(List.of("博客/文章/x.md"), List.of("博客/文章/x.md")), List.of());

        assertThat(report.published()).isEmpty();
        assertThat(report.skipped()).isEmpty();
        assertThat(report.scope()).containsExactly("博客/文章/x.md");
    }

    @Test
    @DisplayName("⑰ extraTouched 里的文章也会被重新渲染（D7：它引用的图变了，可它自己没变）")
    void extraTouchedPathsAreProcessed() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .text("博客/文章/引用了图.md", "---\npublish: true\n---\n正文。\n\n![[shot.png]]\n")
                .binary("博客/文章/_assets/shot.png", new byte[]{9, 9, 9});

        SyncReport report = pipeline.runIncremental(vault,
                new VaultDiff(List.of("博客/文章/_assets/shot.png"), List.of()),
                List.of("博客/文章/引用了图.md"));

        assertThat(report.published()).hasSize(1);
        assertThat(report.published().get(0).draft().vaultPath()).isEqualTo("博客/文章/引用了图.md");
        // 附件路径本身不进 scope —— scope 是给下架用的，而下架的对象只有文章
        assertThat(report.scope()).containsExactly("博客/文章/引用了图.md");
    }

    @Test
    @DisplayName("⑱ 全量报告的 scope 恒为空 —— 全量的负责范围是无界的，靠「不在 present 里」反推，不靠枚举")
    void fullTreeReportHasNoScope() {
        SyncReport report = pipeline.run(new InMemoryVaultReader()
                .text("博客/文章/a.md", "---\npublish: true\n---\nA。\n"));

        assertThat(report.coverage()).isEqualTo(SyncReport.Coverage.FULL_TREE);
        assertThat(report.scope()).isEmpty();
    }
}
