package luv.vansye.sync.domain;

import luv.vansye.sync.domain.PostDraftAssembler.GitTimestamps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 组装与降级测试 —— 对应 PRD §7.2 那张「缺失时怎么办」的表。
 *
 * 用真的 FrontmatterParser 造输入，而不是手工拼 Map：用例读起来就是一个真实文件，
 * 缺什么字段一眼能看见。代价是这份测试同时依赖解析器——可以接受，两者都在内存里。
 */
class PostDraftAssemblerTest {

    private final FrontmatterParser parser = new FrontmatterParser();
    private final PostDraftAssembler assembler = new PostDraftAssembler();

    private static final GitTimestamps GIT = new GitTimestamps(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 2));

    private PostDraft assemble(String path, String raw, GitTimestamps git) {
        return assembler.assemble(path, parser.parse(raw), git);
    }

    // ─── 字段齐全时，一切以 frontmatter 为准 ─────────────────────────

    @Test
    @DisplayName("① frontmatter 写全了 → 原样采用，不去正文里猜")
    void prefersFrontmatterOverEverythingElse() {
        PostDraft draft = assemble("博客/文章/a.md", """
                ---
                title: 手写的标题
                description: 手写的摘要
                slug: redis-depth
                created: 2026-08-16
                updated: 2026-08-17
                tags: [Redis, 学习复盘]
                ---
                # 正文里的标题

                正文内容。
                """, GIT);

        assertThat(draft.title()).isEqualTo("手写的标题");
        assertThat(draft.description()).isEqualTo("手写的摘要");
        assertThat(draft.slug()).isEqualTo("redis-depth");
        assertThat(draft.created()).isEqualTo(LocalDate.of(2026, 8, 16));
        assertThat(draft.updated()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(draft.tags()).containsExactly("Redis", "学习复盘");
    }

    // ─── §7.2 的降级链 ──────────────────────────────────────────────

    @Test
    @DisplayName("② title 缺失 → 取正文第一个一级标题")
    void fallsBackToFirstHeadingForTitle() {
        PostDraft draft = assemble("博客/文章/a.md", """
                ---
                publish: true
                ---
                # 正文里的标题

                内容。
                """, GIT);

        assertThat(draft.title()).isEqualTo("正文里的标题");
    }

    @Test
    @DisplayName("③ title 和一级标题都没有 → 取文件名去扩展名")
    void fallsBackToFileNameForTitle() {
        PostDraft draft = assemble("博客/文章/低空略过Redis的学习.md", "没有 frontmatter，也没有标题。", GIT);

        assertThat(draft.title()).isEqualTo("低空略过Redis的学习");
    }

    @Test
    @DisplayName("④ description 缺失 → 取正文前 80 字，超长补省略号")
    void fallsBackToSummaryForDescription() {
        PostDraft draft = assemble("博客/文章/a.md", "正" + "文".repeat(200), GIT);

        assertThat(draft.description()).hasSize(81).endsWith("…");
    }

    @Test
    @DisplayName("⑤ created/updated 缺失 → 取 git 的首次/最近提交时间")
    void fallsBackToGitTimestamps() {
        PostDraft draft = assemble("博客/文章/a.md", "只有正文。", GIT);

        assertThat(draft.created()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(draft.updated()).isEqualTo(LocalDate.of(2026, 2, 2));
    }

    @Test
    @DisplayName("⑥ git 时间也取不到 → updated 退回 created，不留两个 null")
    void updatedFallsBackToCreated() {
        PostDraft draft = assemble("博客/文章/a.md", """
                ---
                created: 2026-08-16
                ---
                内容。
                """, GitTimestamps.unknown());

        assertThat(draft.updated()).isEqualTo(LocalDate.of(2026, 8, 16));
    }

    @Test
    @DisplayName("⑦ slug 缺失 → 留 null，交给数据库按图版号生成（§7.3）")
    void leavesSlugNullWhenAbsent() {
        assertThat(assemble("博客/文章/a.md", "内容。", GIT).slug()).isNull();
    }

    // ─── 标签：两个来源的并集 ────────────────────────────────────────

    @Test
    @DisplayName("⑧ tags 只认 frontmatter；正文里的 #标签 不解析，原样留在正文里")
    void takesTagsOnlyFromFrontmatter() {
        PostDraft draft = assemble("博客/文章/a.md", """
                ---
                tags: [Redis, 学习复盘]
                ---
                今天复习了 #Redis 还有 #缓存
                """, GIT);

        assertThat(draft.tags()).containsExactly("Redis", "学习复盘");
        assertThat(draft.bodyMd()).contains("#Redis").contains("#缓存");
    }

    @Test
    @DisplayName("⑨ 正文原样透传，逐字节等于 frontmatter 之后的内容——发布的和 vault 里的必须一致")
    void passesBodyThroughUnchanged() {
        String body = """
                今天复习了 #Redis 很多东西

                ```css
                .brand { color: #7929FF; }
                ```
                """;

        PostDraft draft = assemble("博客/文章/a.md", "---\ntitle: A\n---\n" + body, GIT);

        assertThat(draft.bodyMd()).isEqualTo(body);
    }

    // ─── content_hash：同步幂等性的地基 ──────────────────────────────

    @Test
    @DisplayName("⑩ 输入完全一样 → 哈希一样（不然每次同步都会误判成有改动）")
    void sameInputProducesSameHash() {
        String raw = """
                ---
                title: A
                ---
                内容。
                """;

        assertThat(assemble("博客/文章/a.md", raw, GIT).contentHash())
                .isEqualTo(assemble("博客/文章/a.md", raw, GIT).contentHash());
    }

    @Test
    @DisplayName("⑪ 只改 title、正文一字未动 → 哈希也必须变，否则标题永远更新不了")
    void hashChangesWhenOnlyTitleChanges() {
        String before = """
                ---
                title: 旧标题
                ---
                完全一样的正文。
                """;
        String after = """
                ---
                title: 新标题
                ---
                完全一样的正文。
                """;

        assertThat(assemble("博客/文章/a.md", before, GIT).contentHash())
                .isNotEqualTo(assemble("博客/文章/a.md", after, GIT).contentHash());
    }

    // ─── 字数 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("⑫ 字数按正文算，阅读时长至少 1 分钟")
    void countsWordsOnBody() {
        PostDraft draft = assemble("博客/文章/a.md", "你好世界", GIT);

        assertThat(draft.wordCount()).isEqualTo(4);
        assertThat(draft.readMinutes()).isEqualTo(1);
    }
}
