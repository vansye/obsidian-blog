package luv.vansye.sync.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static luv.vansye.sync.domain.PublicationDecision.NOT_MARKDOWN;
import static luv.vansye.sync.domain.PublicationDecision.NOT_PUBLISHED;
import static luv.vansye.sync.domain.PublicationDecision.PATH_FORBIDDEN;
import static luv.vansye.sync.domain.PublicationDecision.PATH_NOT_ALLOWED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 发布闸门测试 —— 对应 PRD 铁律二。
 *
 * 测试方法命名用「行为 + 条件」而不是「测某个方法」：
 *   ✗ testEvaluate1 / testEvaluate2   —— 挂了之后不知道坏了什么业务规则
 *   ✓ rejectsForbiddenPathEvenWhenPublishIsTrue —— 挂了直接知道哪条铁律破了
 *
 * 每个用例都断言 code 而不只断言 publishable。
 * 「结果对但原因错」是这里最容易漏的一类 bug：原因码会进 sync_log 和后台接口，
 * 错了就等于排查「为什么这篇没发布」时被误导。
 */
class PublicationGateTest {

    private final PublicationGate gate = new PublicationGate();

    // ─── 禁止清单：优先级最高，所有用例都故意带 publish=true ───────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "个人/个人信息/玄学/八字结果.md",
            "日记/2026-08-16.md"
    })
    @DisplayName("① 命中禁止的顶层目录 → 拒绝（这是最不可逆的一条，必须最先绿）")
    void rejectsForbiddenRoot(String path) {
        PublicationDecision decision = gate.evaluate(path, true);

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.code()).isEqualTo(PATH_FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "博客/草稿/Drawing 2026-08-16.md",       // 需求明写的位置
            "博客/素材/低空略过Redis的学习·素材问答.md",  // §1.2「素材/ 永不发布」
            "博客/文章/草稿/未完成.md",                // ← 深层草稿：同时命中允许清单前缀
            "学习/JavaWeb/_模板/笔记模板.md"
    })
    @DisplayName("② 禁止目录名出现在**任意层级**都要拦下（含深层草稿，写成前缀会漏）")
    void rejectsForbiddenSegmentAtAnyDepth(String path) {
        PublicationDecision decision = gate.evaluate(path, true);

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.code()).isEqualTo(PATH_FORBIDDEN);
    }

    // ─── 允许清单 ────────────────────────────────────────────────────

    @Test
    @DisplayName("③ 两个清单都不在 → 拒绝（学习/ 下的原始笔记不能漏出去）")
    void rejectsPathOutsidePublishArea() {
        PublicationDecision decision = gate.evaluate("学习/JavaWeb/JavaWeb开发总结.md", true);

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.code()).isEqualTo(PATH_NOT_ALLOWED);
    }

    @Test
    @DisplayName("④ 非 md 文件 → 拒绝 not_markdown（闸门只管文章，附件走另一条路）")
    void rejectsNonMarkdownFile() {
        PublicationDecision decision = gate.evaluate("博客/文章/_assets/Pasted image 20260815171656.png", true);

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.code()).isEqualTo(NOT_MARKDOWN);
    }

    // ─── publish 字段的三态 ──────────────────────────────────────────

    @Test
    @DisplayName("⑤ 路径合法但 publish: false → 拒绝 not_published")
    void rejectsWhenPublishIsFalse() {
        PublicationDecision decision = gate.evaluate("博客/文章/JavaWeb开发总结.md", false);

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.code()).isEqualTo(NOT_PUBLISHED);
    }

    @Test
    @DisplayName("⑥ publish 判定不了（字段缺失 / YAML 挂了）→ 拒绝，和写了 false 同等对待")
    void rejectsWhenPublishIsUndecidable() {
        PublicationDecision decision = gate.evaluate("博客/文章/JavaWeb开发总结.md", null);

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.code()).isEqualTo(NOT_PUBLISHED);
    }

    // ─── 唯一的通过路径 ──────────────────────────────────────────────

    @Test
    @DisplayName("⑦ 路径在允许清单 + 显式 publish: true → 通过")
    void acceptsArticleWithExplicitPublishTrue() {
        PublicationDecision decision =
                gate.evaluate("博客/文章/低空略过Redis的学习，才理解底层的难处.md", true);

        assertThat(decision.publishable()).isTrue();
        assertThat(decision.code()).isNull();
    }

    @Test
    @DisplayName("⑧ 允许清单里的单文件（博客/关于.md）也能过，它不在任何允许目录下")
    void acceptsAllowListedSingleFile() {
        PublicationDecision decision = gate.evaluate("博客/关于.md", true);

        assertThat(decision.publishable()).isTrue();
    }

    // ─── 环境差异 ────────────────────────────────────────────────────

    @Test
    @DisplayName("⑨ 反斜杠路径（Windows 本地遍历）也要命中禁止清单，否则清单静默失效")
    void rejectsForbiddenPathWithBackslashSeparators() {
        PublicationDecision decision = gate.evaluate("个人\\个人信息\\玄学\\八字结果.md", true);

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.code()).isEqualTo(PATH_FORBIDDEN);
    }

    // ─── 对抗性：故意构造能骗过两个清单的路径 ─────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "博客/文章/../个人/八字结果.md",       // startsWith 允许前缀，个人 却在第 4 段
            "博客/文章/../../个人/日记.md",
            "博客/文章/./../个人/x.md",
            "博客/文章/子目录/../草稿/未完成.md"
    })
    @DisplayName("⑩ 带 .. 的路径一律拒绝：它能同时骗过允许清单和禁止清单")
    void rejectsPathTraversal(String path) {
        PublicationDecision decision = gate.evaluate(path, true);

        assertThat(decision.publishable()).isFalse();
        assertThat(decision.code()).isEqualTo(PATH_FORBIDDEN);
        assertThat(gate.inPublishArea(path)).isFalse();
    }

    @Test
    @DisplayName("⑪ 博客/_assets/ 在发布区里，否则关于页的图永远解析不到")
    void aboutPageAssetsAreInPublishArea() {
        assertThat(gate.inPublishArea("博客/_assets/me.png")).isTrue();
        assertThat(gate.inPublishArea("博客/文章/_assets/shot.png")).isTrue();
        assertThat(gate.inPublishArea("个人/证件/身份证.png")).isFalse();
    }
}
