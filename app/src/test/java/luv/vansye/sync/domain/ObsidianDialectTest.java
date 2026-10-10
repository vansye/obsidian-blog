package luv.vansye.sync.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Obsidian 方言转换测试 —— 对应 PRD F-02。
 *
 * 最重要的是用例 ③④：`![[笔记]]` 必须降级为纯文本。
 * 那不是排版问题，是发布闸门的旁路——照 Obsidian 的语义嵌入笔记全文，
 * 等于让一篇没过闸门的笔记整篇公开。
 */
class ObsidianDialectTest {

    private static final String ARTICLE = "博客/文章/低空略过Redis的学习.md";
    private static final String HASH = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";

    private final ObsidianDialect dialect = new ObsidianDialect();

    private final AssetIndex assets = new AssetIndex(Map.of(
            "博客/文章/_assets/Pasted image 20260815171656.png", HASH));

    private ObsidianDialect.Result convert(String md) {
        return dialect.toStandardMarkdown(md, ARTICLE, assets);
    }

    // ─── 图片内嵌 ────────────────────────────────────────────────────

    @Test
    @DisplayName("① ![[图片.png]] → 标准 md 图片，URL 用内容哈希（§6.3），**alt 为空不带文件名**")
    void convertsImageEmbedToHashedUrl() {
        ObsidianDialect.Result result = convert("![[Pasted image 20260815171656.png]]");

        // 原来 alt 传的是文件名，实测每篇文章页都带着
        // `alt="Pasted image 20260815171656.png"`——src 精心做成内容寻址不暴露路径，
        // alt 又把原名送了出去。空 alt 也是「装饰性图片」的标准写法
        assertThat(result.markdown()).isEqualTo("![](/assets/a1b2c3d4e5f60718.png)");
        assertThat(result.markdown()).doesNotContain("Pasted image");
        assertThat(result.missingAssets()).isEmpty();
    }

    @Test
    @DisplayName("①b 竖线后写了说明文字就当 alt；写的是尺寸就不当 —— 同一个语法位置两种意思")
    void pipeSuffixBecomesAltOnlyWhenItIsNotASize() {
        assertThat(convert("![[Pasted image 20260815171656.png|Redis 集群结构]]").markdown())
                .isEqualTo("![Redis 集群结构](/assets/a1b2c3d4e5f60718.png)");

        // `|300` 和 `|300x200` 是 Obsidian 的尺寸参数，不是说明文字
        assertThat(convert("![[Pasted image 20260815171656.png|300]]").markdown())
                .isEqualTo("![](/assets/a1b2c3d4e5f60718.png)");
        assertThat(convert("![[Pasted image 20260815171656.png|300x200]]").markdown())
                .isEqualTo("![](/assets/a1b2c3d4e5f60718.png)");
    }

    @Test
    @DisplayName("② 附件不在发布区索引里 → 不输出裂图，记进 missingAssets")
    void recordsMissingAssetInsteadOfEmittingBrokenImage() {
        ObsidianDialect.Result result = convert("正文\n![[个人证件照.png]]\n结尾");

        assertThat(result.markdown()).isEqualTo("正文\n\n结尾");
        assertThat(result.missingAssets()).containsExactly("个人证件照.png");
    }

    @Test
    @DisplayName("③ ![[笔记]] 绝不嵌入内容，降级为纯文本 —— 这是闸门旁路的封堵")
    void neverTranscludesANote() {
        ObsidianDialect.Result result = convert("参考 ![[Redis学习小结]] 这一篇");

        assertThat(result.markdown()).isEqualTo("参考 Redis学习小结 这一篇");
        assertThat(result.blockedEmbeds()).containsExactly("Redis学习小结");
    }

    @Test
    @DisplayName("④ 带路径的笔记内嵌同样拦下，哪怕它指向 学习/ 之外")
    void blocksNoteEmbedWithExplicitPath() {
        ObsidianDialect.Result result = convert("![[学习/数据库/Redis/Redis学习小结]]");

        assertThat(result.markdown()).isEqualTo("学习/数据库/Redis/Redis学习小结");
        assertThat(result.blockedEmbeds()).hasSize(1);
    }

    @Test
    @DisplayName("⑤ ![[图片.png|500]] 的尺寸参数不影响解析")
    void ignoresEmbedSizeParameter() {
        ObsidianDialect.Result result = convert("![[Pasted image 20260815171656.png|500]]");

        assertThat(result.markdown()).contains("/assets/a1b2c3d4e5f60718.png");
    }

    // ─── 双链降级 ────────────────────────────────────────────────────

    @Test
    @DisplayName("⑥ [[双链]] 去掉方括号只留文字，不留 [[ ]] 裸字符")
    void stripsWikilinkBrackets() {
        assertThat(convert("详见 [[Redis持久化]] 那篇").markdown())
                .isEqualTo("详见 Redis持久化 那篇");
    }

    @Test
    @DisplayName("⑦ [[目标|显示文字]] 取显示文字")
    void usesDisplayTextWhenPresent() {
        assertThat(convert("详见 [[Redis持久化|持久化机制]]").markdown())
                .isEqualTo("详见 持久化机制");
    }

    @Test
    @DisplayName("⑧ [[笔记#章节]] 去掉章节锚点")
    void dropsHeadingAnchor() {
        assertThat(convert("[[Redis持久化#过期策略]]").markdown()).isEqualTo("Redis持久化");
    }

    // ─── 代码块：这里最容易改坏 ───────────────────────────────────────

    @Test
    @DisplayName("⑨ bash 的 [[ -f x ]] 是语法不是双链，代码块里的内容一律不动")
    void neverTouchesCodeFenceContent() {
        String md = """
                正文 [[真双链]]

                ```bash
                if [[ -f config.yaml ]]; then
                  echo "found"
                fi
                ```
                """;

        String out = convert(md).markdown();

        assertThat(out).contains("if [[ -f config.yaml ]]; then");
        assertThat(out).contains("正文 真双链");
    }

    @Test
    @DisplayName("⑩ 行内代码里的 [[x]] 同样不动")
    void neverTouchesInlineCode() {
        assertThat(convert("双链写法是 `[[标题]]` 这样，比如 [[Redis]]").markdown())
                .isEqualTo("双链写法是 `[[标题]]` 这样，比如 Redis");
    }

    // ─── 标准 md 图片 ────────────────────────────────────────────────

    @Test
    @DisplayName("⑪ Typora 的本机绝对路径 → 判为缺图，不输出 <img src=\"C:...\">")
    void rejectsLocalAbsolutePath() {
        String md = "![image-20260723144323272](C:\\Users\\VANSYE\\AppData\\Roaming\\Typora\\x.png)";

        ObsidianDialect.Result result = convert(md);

        assertThat(result.markdown()).isEmpty();
        assertThat(result.missingAssets()).hasSize(1);
        assertThat(result.markdown()).doesNotContain("VANSYE");
    }

    @Test
    @DisplayName("⑫ 外链图片原样放行")
    void passesExternalImageThrough() {
        String md = "![封面](https://example.com/a.png)";

        assertThat(convert(md).markdown()).isEqualTo(md);
    }

    @Test
    @DisplayName("⑬ 相对路径图片也走索引，解析成哈希 URL")
    void resolvesRelativeImagePath() {
        String md = "![图](_assets/Pasted image 20260815171656.png)";

        assertThat(convert(md).markdown()).isEqualTo("![图](/assets/a1b2c3d4e5f60718.png)");
    }

    // ─── 对抗性：故意用嵌套栅栏去骗代码块判定 ─────────────────────────

    @Test
    @DisplayName("⑭ ``` 开的代码块里出现 ~~~，不该被误认为结束 —— 写 markdown 教程时的常态")
    void mismatchedFenceMarkerDoesNotCloseTheBlock() {
        String md = "```\n~~~\n[[不该转的双链]]\n```\n之后 [[真双链]]\n";

        String out = convert(md).markdown();

        assertThat(out).contains("[[不该转的双链]]");   // 代码块里的原样保留
        assertThat(out).contains("之后 真双链");        // 代码块外的照常转
    }

    @Test
    @DisplayName("⑮ 四个反引号开的栅栏，里面的三反引号不该关掉它")
    void shorterFenceDoesNotCloseALongerOne() {
        String md = "````\n```\n[[不该转]]\n```\n````\n之后 [[真双链]]\n";

        String out = convert(md).markdown();

        assertThat(out).contains("[[不该转]]");
        assertThat(out).contains("之后 真双链");
    }

    @Test
    @DisplayName("⑯ 博客/关于.md 的图在 博客/_assets/ 下也要能解析")
    void resolvesAboutPageAsset() {
        AssetIndex aboutAssets = new AssetIndex(Map.of("博客/_assets/me.png", HASH));

        ObsidianDialect.Result result =
                dialect.toStandardMarkdown("![[me.png]]", "博客/关于.md", aboutAssets);

        assertThat(result.missingAssets()).isEmpty();
        assertThat(result.markdown()).contains("/assets/a1b2c3d4e5f60718.png");
    }
}
