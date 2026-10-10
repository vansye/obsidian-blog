package luv.vansye.sync.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Markdown 渲染测试 —— 对应 PRD F-01。
 *
 * 除了「语法能不能渲染」，还钉两件事：
 *   代码块**不带高亮**（视觉规格 §6.1 禁止），以及原始 HTML 不放行。
 */
class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    @DisplayName("① 基本语法：标题、段落、强调、链接")
    void rendersBasicMarkdown() {
        String html = renderer.toHtml("""
                # 标题

                这是**加粗**和[链接](https://example.com)。
                """);

        assertThat(html).contains("<h1>标题</h1>");
        assertThat(html).contains("<strong>加粗</strong>");
        assertThat(html).contains("href=\"https://example.com\"").contains(">链接</a>");
    }

    @Test
    @DisplayName("①b commonmark 0.30 会给链接自动加 rel=\"nofollow\"，站内链接也一样")
    void addsNofollowToEveryLink() {
        assertThat(renderer.toHtml("[外链](https://example.com)")).contains("rel=\"nofollow\"");
        assertThat(renderer.toHtml("[站内](/p/014)")).contains("rel=\"nofollow\"");
    }

    @Test
    @DisplayName("② GFM 表格")
    void rendersGfmTable() {
        String html = renderer.toHtml("""
                | 字段 | 说明 |
                |---|---|
                | publish | 发布开关 |
                """);

        assertThat(html).contains("<table>").contains("<th>字段</th>").contains("<td>publish</td>");
    }

    @Test
    @DisplayName("③ 删除线和任务列表")
    void rendersStrikethroughAndTaskList() {
        assertThat(renderer.toHtml("~~废弃~~")).contains("<del>废弃</del>");

        String html = renderer.toHtml("""
                - [x] 已完成
                - [ ] 未完成
                """);
        assertThat(html).contains("type=\"checkbox\"").contains("checked");
    }

    @Test
    @DisplayName("④ 代码块只标语言，**不做彩色高亮**（视觉规格 §6.1）")
    void rendersCodeBlockWithoutHighlighting() {
        String html = renderer.toHtml("""
                ```java
                public record Foo(String bar) {}
                ```
                """);

        assertThat(html).contains("<code class=\"language-java\">");
        assertThat(html).doesNotContain("<span");     // 没有高亮用的 span
    }

    @Test
    @DisplayName("⑤ 正文里的原始 HTML 被转义成文字，不放行")
    void escapesRawHtml() {
        String html = renderer.toHtml("正文 <script>alert(1)</script> 结尾");

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("⑥ javascript: 链接被过滤掉")
    void sanitizesDangerousUrl() {
        String html = renderer.toHtml("[点我](javascript:alert(1))");

        assertThat(html).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("⑦ 中文和标点正常输出")
    void handlesChineseText() {
        assertThat(renderer.toHtml("低空略过 Redis 的学习，才理解底层的难处。"))
                .contains("低空略过 Redis 的学习，才理解底层的难处。");
    }

    @Test
    @DisplayName("⑧ 空输入和 null 不崩")
    void handlesEmptyInput() {
        assertThat(renderer.toHtml("")).isEmpty();
        assertThat(renderer.toHtml(null)).isEmpty();
    }
}
