package luv.vansye.sync.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 附件索引测试。重点是两条：只收发布区的（C5），以及哈希前缀撞车要能发现。
 */
class AssetIndexBuilderTest {

    private final AssetIndexBuilder builder = new AssetIndexBuilder(new PublicationGate());

    @Test
    @DisplayName("① 发布区内的附件进索引，URL 是哈希前 16 位 + 扩展名")
    void indexesAssetsInPublishArea() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .binary("博客/文章/_assets/shot.png", new byte[]{1, 2, 3});

        AssetIndexBuilder.Result result = builder.build(vault);

        assertThat(result.index().urlFor("博客/文章/_assets/shot.png"))
                .get().asString().startsWith("/assets/").endsWith(".png").hasSize(8 + 16 + 4);
        assertThat(result.collisions()).isEmpty();
    }

    @Test
    @DisplayName("② 发布区外的附件根本不进索引 —— 安全边界由构造范围决定，不靠解析时判断")
    void excludesAssetsOutsidePublishArea() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .binary("个人/证件/身份证.png", new byte[]{1})
                .binary("学习/JavaWeb/截图.png", new byte[]{2})
                .binary("博客/素材/草图.png", new byte[]{3});

        AssetIndexBuilder.Result result = builder.build(vault);

        assertThat(result.index().hashByPath()).isEmpty();
    }

    @Test
    @DisplayName("③ 内容相同的两个附件哈希相同 —— 天然去重，URL 也是同一个")
    void identicalContentSharesTheSameUrl() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .binary("博客/文章/_assets/a.png", new byte[]{7, 7})
                .binary("博客/文章/_assets/b.png", new byte[]{7, 7});

        AssetIndexBuilder.Result result = builder.build(vault);

        // 内容一样 → 全哈希一样 → 前缀一样。这**不是**碰撞，是去重，两个都要能用
        assertThat(result.collisions()).isEmpty();
        assertThat(result.index().urlFor("博客/文章/_assets/a.png"))
                .isEqualTo(result.index().urlFor("博客/文章/_assets/b.png"));
    }

    @Test
    @DisplayName("④ 非附件扩展名不进索引")
    void ignoresNonAssetFiles() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .text("博客/文章/a.md", "正文")
                .text("博客/文章/_assets/note.txt", "文本");

        assertThat(builder.build(vault).index().hashByPath()).isEmpty();
    }

    @Test
    @DisplayName("⑤ 读不到的附件记进 unreadable（retryable），不是当作不存在")
    void reportsUnreadableAsset() {
        InMemoryVaultReader vault = new InMemoryVaultReader()
                .unreadable("博客/文章/_assets/broken.png");

        AssetIndexBuilder.Result result = builder.build(vault);

        assertThat(result.unreadable()).containsExactly("博客/文章/_assets/broken.png");
        assertThat(result.index().hashByPath()).isEmpty();
    }

    @Test
    @DisplayName("⑥ 空仓库不崩")
    void handlesEmptyRepository() {
        AssetIndexBuilder.Result result = builder.build(new InMemoryVaultReader());

        assertThat(result.index().hashByPath()).isEmpty();
        assertThat(result.collisions()).isEmpty();
        assertThat(result.unreadable()).isEmpty();
    }
}
