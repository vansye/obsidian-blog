package luv.vansye.sync.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * frontmatter 解析测试 —— 对应 PRD F-03。
 *
 * 用例来自对输入画取值谱：一个 md 文件的开头能长成哪些**本质不同**的样子。
 * 需求原文只写了「读取 title/created/updated/tags/description/publish/slug」，
 * 下面九成的用例在需求里都没出现——它们藏在「文件可能没有 YAML 块」
 * 「YAML 可能写错」这两句话里，而 bug 全在这些地方。
 */
class FrontmatterParserTest {

    private final FrontmatterParser parser = new FrontmatterParser();

    // ─── 正常路径 ────────────────────────────────────────────────────

    @Test
    @DisplayName("① 标准 frontmatter → 字段读得出来，正文被完整剥离")
    void parsesStandardFrontmatter() {
        String raw = """
                ---
                title: 低空略过 Redis 的学习
                publish: true
                tags: [Redis, 学习复盘]
                ---
                正文第一段。
                """;

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.ok()).isTrue();
        assertThat(fm.values()).containsEntry("title", "低空略过 Redis 的学习");
        assertThat(fm.publish()).isTrue();
        assertThat(fm.values().get("tags")).asList().containsExactly("Redis", "学习复盘");
        assertThat(fm.body()).isEqualTo("正文第一段。\n");
    }

    @Test
    @DisplayName("② 没有 frontmatter → 不是错误，只是 values 为空、整篇都是正文")
    void treatsMissingFrontmatterAsEmptyNotError() {
        String raw = """
                # 标题
                正文。
                """;

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.ok()).isTrue();
        assertThat(fm.values()).isEmpty();
        assertThat(fm.publish()).isNull();
        assertThat(fm.body()).isEqualTo(raw);
    }

    @Test
    @DisplayName("③ 正文里的 --- 分隔线不会被当成结束标记（只取第一个）")
    void doesNotMistakeHorizontalRuleForClosingDelimiter() {
        String raw = """
                ---
                title: A
                ---
                上半段

                ---

                下半段
                """;

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.ok()).isTrue();
        assertThat(fm.values()).containsEntry("title", "A");
        assertThat(fm.body()).contains("上半段").contains("---").contains("下半段");
    }

    // ─── 解析失败：三种都必须能区分，因为要写进 sync_log 让我知道为什么没发 ───

    @Test
    @DisplayName("④ 只有开头 --- 没有结尾 → 失败，不猜边界")
    void failsOnUnterminatedFrontmatter() {
        String raw = """
                ---
                title: 没写结尾
                正文。
                """;

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.ok()).isFalse();
        assertThat(fm.error()).isEqualTo(Frontmatter.ERR_UNTERMINATED);
        assertThat(fm.publish()).isNull();
    }

    @Test
    @DisplayName("⑤ YAML 语法错误 → 失败（而不是把半截结果当成解析成功）")
    void failsOnMalformedYaml() {
        String raw = """
                ---
                title: [没关括号
                publish: true
                ---
                正文。
                """;

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.ok()).isFalse();
        assertThat(fm.error()).isEqualTo(Frontmatter.ERR_YAML);
    }

    @Test
    @DisplayName("⑥ 重复 key → 失败，不静默取最后一个（publish 写两遍时后果不可接受）")
    void failsOnDuplicateKeys() {
        String raw = """
                ---
                publish: false
                title: A
                publish: true
                ---
                正文。
                """;

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.ok()).isFalse();
        assertThat(fm.error()).isEqualTo(Frontmatter.ERR_YAML);
    }

    @Test
    @DisplayName("⑦ YAML 合法但根节点是列表 → 失败（读不出字段就等于判定不了）")
    void failsWhenRootIsNotAMap() {
        String raw = """
                ---
                - a
                - b
                ---
                正文。
                """;

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.ok()).isFalse();
        assertThat(fm.error()).isEqualTo(Frontmatter.ERR_NOT_A_MAP);
    }

    // ─── publish 的三态与 fail-closed ────────────────────────────────

    @Test
    @DisplayName("⑧ 「publish:」后面留空 → 值是 null，要能读出来且不能崩")
    void handlesNullValuedKeyWithoutCrashing() {
        String raw = """
                ---
                publish:
                title: A
                ---
                正文。
                """;

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.ok()).isTrue();
        assertThat(fm.values()).containsKey("publish");
        assertThat(fm.publish()).isNull();
    }

    /*
     * ⑨⑩ 这两个用例是**跑出来**的，不是想出来的。
     *
     * 我原本断言「只有 true 才算 true，yes 应该判定不了」，结果测试红了：
     * SnakeYAML 2.4 沿用 YAML 1.1 的布尔词表，yes/on 也是货真价实的 Boolean。
     * 这时候有两条路——改代码去迎合我的假设，或者改断言去承认事实。
     * 选后者：yes 同样是作者的**显式**声明，铁律二要挡的是「没说」和「说不清」，
     * 不是「换了个词说」。硬把 YAML 的词表砍掉，等于给自己造一套方言。
     *
     * 但 ⑩ 那条必须留着：publish: "true" 加了引号就是字符串，**不发布**。
     * 这是最容易踩且最反直觉的一个坑，所以要有测试把它钉死成契约。
     */
    @ParameterizedTest
    @ValueSource(strings = {"true", "True", "TRUE", "yes", "Yes", "on"})
    @DisplayName("⑨ YAML 布尔的各种写法都算显式声明（含 yes/on，这是实测结果）")
    void recognizesAllYamlBooleanSpellings(String publishValue) {
        String raw = "---\npublish: " + publishValue + "\n---\n正文。\n";

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.publish())
                .as("publish: %s 应该被认成布尔 true", publishValue)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"true\"", "'yes'", "1", "是"})
    @DisplayName("⑩ 长得像 true 但不是布尔的值 → 判定不了（加引号就是字符串，不发布）")
    void rejectsBooleanLookalikes(String publishValue) {
        String raw = "---\npublish: " + publishValue + "\n---\n正文。\n";

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.publish())
                .as("publish: %s 不该被当作 true", publishValue)
                .isNull();
    }

    // ─── 环境差异与退化输入 ──────────────────────────────────────────

    @Test
    @DisplayName("⑩ CRLF 换行（Windows git 检出）要和 LF 得到完全一样的结果")
    void handlesCrlfLineEndings() {
        String raw = "---\r\ntitle: A\r\npublish: true\r\n---\r\n正文。\r\n";

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.ok()).isTrue();
        assertThat(fm.publish()).isTrue();
        assertThat(fm.body()).isEqualTo("正文。\n");
    }

    @Test
    @DisplayName("⑪ 空文件 / null 不崩，按「没有 frontmatter」处理")
    void handlesEmptyAndNullInput() {
        assertThat(parser.parse("").ok()).isTrue();
        assertThat(parser.parse("").values()).isEmpty();
        assertThat(parser.parse(null).ok()).isTrue();
        assertThat(parser.parse(null).publish()).isNull();
    }

    @Test
    @DisplayName("⑫ 空的 frontmatter 块（--- 紧跟 ---）是合法的，不是错误")
    void treatsEmptyFrontmatterBlockAsValid() {
        String raw = """
                ---
                ---
                正文。
                """;

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.ok()).isTrue();
        assertThat(fm.values()).isEmpty();
        assertThat(fm.body()).isEqualTo("正文。\n");
    }

    @Test
    @DisplayName("⑬ 失败时 detail 要带上具体位置——只有原因码的话，查得到哪篇没发，查不到哪行写错")
    void reportsActionableDetailOnFailure() {
        String raw = """
                ---
                title: [没关括号
                ---
                正文。
                """;

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.error()).isEqualTo(Frontmatter.ERR_YAML);
        assertThat(fm.detail()).isNotBlank().doesNotContain("\n");
    }

    @Test
    @DisplayName("⑭ 日期字段：SnakeYAML 把 2026-08-16 解析成 Date，取出来要是 LocalDate")
    void readsDateFieldsAsLocalDate() {
        String raw = """
                ---
                created: 2026-08-16
                tags: [Redis, 学习复盘]
                ---
                正文。
                """;

        Frontmatter fm = parser.parse(raw);

        assertThat(fm.date("created")).isEqualTo(java.time.LocalDate.of(2026, 8, 16));
        assertThat(fm.date("updated")).isNull();
        assertThat(fm.stringList("tags")).containsExactly("Redis", "学习复盘");
        assertThat(fm.string("没有这个字段")).isNull();
    }

    // ─── 对抗性：YAML 本身的攻击面 ───────────────────────────────────

    @Test
    @DisplayName("⑮ !!java 标签构造不出对象 —— SafeConstructor 的作用在这里")
    void rejectsJavaTypeTags() {
        Frontmatter fm = parser.parse("---\npublish: !!java.lang.Boolean true\n---\n正文。\n");

        assertThat(fm.publish()).isNull();
        assertThat(fm.ok()).isFalse();
    }

    @Test
    @DisplayName("⑯ 别名膨胀（billion laughs）不会把进程拖死 —— SnakeYAML 有别名上限")
    void survivesAliasExpansion() {
        String raw = """
                ---
                a: &a [1,2,3]
                b: &b [*a,*a,*a,*a,*a,*a,*a,*a,*a]
                c: &c [*b,*b,*b,*b,*b,*b,*b,*b,*b]
                d: [*c,*c,*c,*c,*c,*c,*c,*c,*c]
                ---
                正文。
                """;

        Frontmatter fm = parser.parse(raw);

        assertThat(fm).isNotNull();          // 不卡死、不 OOM 就算过
        assertThat(fm.publish()).isNull();   // 没有 publish → 照样不发布
    }
}
