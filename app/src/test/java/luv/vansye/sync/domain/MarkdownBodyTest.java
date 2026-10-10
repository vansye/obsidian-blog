package luv.vansye.sync.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 正文分析测试。
 *
 * 用例集中在一个问题上：「代码块里的内容不该被当成正文」。
 * 代码里的 # 是注释，代码里的中文是字符串，都不该跑进标题、摘要和字数里。
 */
class MarkdownBodyTest {

    @Test
    @DisplayName("① 取第一个一级标题；二级标题不算")
    void findsFirstLevelOneHeading() {
        MarkdownBody body = new MarkdownBody("""
                ## 二级在前
                # 真正的一级
                # 第二个一级
                """);

        assertThat(body.firstHeading()).contains("真正的一级");
    }

    @Test
    @DisplayName("② 代码块里的 # 是注释，不是标题")
    void ignoresHeadingInsideCodeFence() {
        MarkdownBody body = new MarkdownBody("""
                ```python
                # 这是注释不是标题
                ```
                # 真标题
                """);

        assertThat(body.firstHeading()).contains("真标题");
    }

    @Test
    @DisplayName("③ 没有一级标题 → 空，交给上层降级到文件名")
    void returnsEmptyWhenNoHeading() {
        assertThat(new MarkdownBody("只有正文。").firstHeading()).isEmpty();
    }

    @Test
    @DisplayName("④ 摘要剥掉 md 语法：链接只留文字，图片整个去掉")
    void summaryStripsMarkdownSyntax() {
        MarkdownBody body = new MarkdownBody("""
                # 标题

                这是[一个链接](https://example.com)和 ![图片](a.png) 混在一起的正文。
                """);

        String summary = body.summary(80);

        assertThat(summary).contains("一个链接").contains("混在一起的正文");
        assertThat(summary).doesNotContain("https://").doesNotContain("a.png");
    }

    @Test
    @DisplayName("⑤ 摘要不含代码块内容（否则开头是代码的文章，摘要全是代码）")
    void summaryExcludesCodeFence() {
        MarkdownBody body = new MarkdownBody("""
                ```java
                public class Foo {}
                ```
                这才是正文开头。
                """);

        assertThat(body.summary(80)).isEqualTo("这才是正文开头。");
    }

    @Test
    @DisplayName("⑥ 超长正文截断并补省略号；不超长时不补")
    void summaryTruncatesOnlyWhenTooLong() {
        assertThat(new MarkdownBody("一".repeat(200)).summary(80))
                .hasSize(81)
                .endsWith("…");

        assertThat(new MarkdownBody("很短。").summary(80)).isEqualTo("很短。");
    }

    @Test
    @DisplayName("⑦ 字数：中文按字算，英文按词算；阅读时长至少 1 分钟")
    void countsWordsAndReadMinutes() {
        MarkdownBody body = new MarkdownBody("你好世界 hello world");

        assertThat(body.wordCount()).isEqualTo(6);   // 4 个汉字 + 2 个英文词
        assertThat(body.readMinutes()).isEqualTo(1);
    }

    @Test
    @DisplayName("⑧ 字数不算代码块，不然一篇满是代码的笔记会显示要读半小时")
    void wordCountExcludesCodeFence() {
        MarkdownBody body = new MarkdownBody("""
                你好世界
                ```java
                // 这里有很多很多很多很多很多很多的代码字符
                ```
                """);

        assertThat(body.wordCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("⑨ ``` 块里出现 ~~~ 不该被误认为结束，否则后面的 # 会被当成标题")
    void mismatchedFenceMarkerDoesNotCloseTheBlock() {
        MarkdownBody body = new MarkdownBody("""
                ```
                ~~~
                # 代码块里的注释
                ```
                # 真标题
                """);

        assertThat(body.firstHeading()).contains("真标题");
    }

    @Test
    @DisplayName("⑩ 摘要不能带出附件的原始文件名 —— description 是要发给访客的字段")
    void summaryNeverLeaksAttachmentFileNames() {
        MarkdownBody body = new MarkdownBody("""
                ![[Pasted image 20260815171656.png]]
                >![[Pasted image 20260819202011.png]]
                这才是正文。
                """);

        String summary = body.summary(80);

        // 改回一条 `!?\[\[([^\]]*)\]\] → $1` 的规则时，这里会拿到
        // "Pasted image 20260815171656.png Pasted image 20260819202011.png 这才是正文。"
        assertThat(summary)
                .as("嵌入语法里的方括号装的是文件名，不是给人看的文字")
                .doesNotContain("Pasted image")
                .doesNotContain(".png");
        assertThat(summary).isEqualTo("这才是正文。");
    }

    @Test
    @DisplayName("⑪ 双链留显示文字：`[[目标|别名]]` 只留别名，目标可能是禁区里的笔记名")
    void summaryKeepsWikilinkTextButNotItsTarget() {
        MarkdownBody body = new MarkdownBody("""
                见 [[个人/日记/2026-08-20|那天的记录]] 和 [[Redis 笔记]]。
                """);

        assertThat(body.summary(80))
                .isEqualTo("见 那天的记录 和 Redis 笔记。");
    }
}
