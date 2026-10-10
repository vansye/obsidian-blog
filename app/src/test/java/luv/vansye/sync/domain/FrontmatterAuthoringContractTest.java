package luv.vansye.sync.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「作者写成什么样能解析」的契约测试 —— docs/frontmatter写作要求.md 的可执行版本。
 *
 * 上面那份文档里的每一条「能这么写 / 不能这么写」，在这里都有一条对应的用例。
 * 文档会过期，测试不会：改了解析器而这里变红，说明文档需要跟着改。
 */
class FrontmatterAuthoringContractTest {

    private final FrontmatterParser parser = new FrontmatterParser();

    // ─── 冒号：最容易踩的一类 ────────────────────────────────────────

    @Test
    @DisplayName("① 冒号后面必须有空格。写成 title:标题 → 整篇解析不出键值对")
    void colonNeedsTrailingSpace() {
        Frontmatter fm = parser.parse("---\ntitle:标题\n---\n正文。\n");

        assertThat(fm.ok()).isFalse();
        assertThat(fm.error()).isEqualTo(Frontmatter.ERR_NOT_A_MAP);
    }

    @Test
    @DisplayName("② 必须是英文冒号。中文全角「：」不是 YAML 分隔符")
    void fullWidthColonIsNotASeparator() {
        Frontmatter fm = parser.parse("---\ntitle：标题\n---\n正文。\n");

        assertThat(fm.ok()).isFalse();
    }

    @Test
    @DisplayName("③ 值里再出现冒号 → 解析失败，除非整个值加引号")
    void colonInsideValueNeedsQuotes() {
        Frontmatter bad = parser.parse("---\ntitle: Redis: 从入门到放弃\n---\n正文。\n");
        assertThat(bad.ok()).isFalse();
        assertThat(bad.error()).isEqualTo(Frontmatter.ERR_YAML);

        Frontmatter good = parser.parse("---\ntitle: \"Redis: 从入门到放弃\"\n---\n正文。\n");
        assertThat(good.ok()).isTrue();
        assertThat(good.string("title")).isEqualTo("Redis: 从入门到放弃");
    }

    // ─── 井号：不报错，但会悄悄截断 ──────────────────────────────────

    @Test
    @DisplayName("④ 值里的 # 前面有空格时是 YAML 注释，后面的内容会被**静默**吃掉")
    void hashStartsACommentAndTruncatesValue() {
        Frontmatter fm = parser.parse("---\ntitle: 学习 #Redis 心得\n---\n正文。\n");

        assertThat(fm.ok()).isTrue();               // 不报错，这才是危险的地方
        assertThat(fm.string("title")).isEqualTo("学习");

        Frontmatter quoted = parser.parse("---\ntitle: \"学习 #Redis 心得\"\n---\n正文。\n");
        assertThat(quoted.string("title")).isEqualTo("学习 #Redis 心得");
    }

    // ─── 位置与缩进 ──────────────────────────────────────────────────

    @Test
    @DisplayName("⑤ 开头的 --- 必须在**第一行**。前面有空行就等于没有 frontmatter")
    void delimiterMustBeOnTheVeryFirstLine() {
        Frontmatter fm = parser.parse("\n---\npublish: true\n---\n正文。\n");

        assertThat(fm.ok()).isTrue();
        assertThat(fm.values()).isEmpty();          // 没报错，但字段一个都没读到
        assertThat(fm.publish()).isNull();          // → 不发布
    }

    @Test
    @DisplayName("⑥ 不要用 Tab 缩进，YAML 不接受")
    void tabIndentationIsRejected() {
        Frontmatter fm = parser.parse("---\ntags:\n\t- Redis\n---\n正文。\n");

        assertThat(fm.ok()).isFalse();
        assertThat(fm.error()).isEqualTo(Frontmatter.ERR_YAML);
    }

    // ─── 列表：两种写法都支持 ────────────────────────────────────────

    @Test
    @DisplayName("⑦ tags 可以写成一行方括号，也可以写成多行短横线，效果一样")
    void bothListStylesWork() {
        Frontmatter inline = parser.parse("---\ntags: [Redis, 学习复盘]\n---\n正文。\n");
        Frontmatter block = parser.parse("---\ntags:\n  - Redis\n  - 学习复盘\n---\n正文。\n");

        assertThat(inline.stringList("tags")).containsExactly("Redis", "学习复盘");
        assertThat(block.stringList("tags")).containsExactly("Redis", "学习复盘");
    }

    @Test
    @DisplayName("⑧ 只有一个标签时不写方括号也行")
    void singleValueIsTreatedAsOneElementList() {
        Frontmatter fm = parser.parse("---\ntags: Redis\n---\n正文。\n");

        assertThat(fm.stringList("tags")).containsExactly("Redis");
    }

    // ─── 日期 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("⑨ 日期必须是 2026-08-16 这种格式；写成 2026/08/16 会当作没写")
    void onlyIsoDateIsUnderstood() {
        assertThat(parser.parse("---\ncreated: 2026-08-16\n---\n正文。\n").date("created"))
                .isEqualTo(java.time.LocalDate.of(2026, 8, 16));

        assertThat(parser.parse("---\ncreated: 2026/08/16\n---\n正文。\n").date("created"))
                .isNull();
    }

    // ─── publish 开关 ────────────────────────────────────────────────

    @Test
    @DisplayName("⑩ publish 不能加引号。写成 \"true\" 就是字符串，等于没开")
    void publishMustNotBeQuoted() {
        assertThat(parser.parse("---\npublish: true\n---\n正文。\n").publish()).isTrue();
        assertThat(parser.parse("---\npublish: \"true\"\n---\n正文。\n").publish()).isNull();
    }

    @Test
    @DisplayName("⑪ 同一个 key 写两遍 → 整篇解析失败，不会「后面的覆盖前面的」")
    void duplicateKeyFailsTheWholeFile() {
        Frontmatter fm = parser.parse("---\ntags: [A]\npublish: true\ntags: [B]\n---\n正文。\n");

        assertThat(fm.ok()).isFalse();
        assertThat(fm.detail()).isNotBlank();
    }
}
