package luv.vansye.asset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 取图的判定逻辑，纯内存跑。
 *
 * 这一层全是 fail-closed 的判断，每一条不成立都要 404。测试的重心是
 * **「有没有哪条能被绕过」**，不是「能不能取到图」——后者一个用例就够了。
 *
 * 特别是 ④：前缀会被拼进 SQL 的 LIKE 模式串，而 `%` 和 `_` 在 LIKE 里是通配符。
 */
class AssetServiceTest {

    private static final String HASH = "7a55bdcba79c0efd" + "0".repeat(48);
    private static final String BLOB = "a".repeat(40);
    private static final byte[] BYTES = {1, 2, 3};

    private final FakeMapper mapper = new FakeMapper();
    private final FakeBlobStore blobs = new FakeBlobStore();
    private final AssetService service = new AssetService(mapper, blobs);

    @Test
    @DisplayName("① 前缀和扩展名都对上 → 拿到字节、MIME 和全长哈希（ETag 用）")
    void servesTheAssetWhenEverythingMatches() {
        mapper.add(HASH, BLOB, ".png", BYTES.length);
        blobs.put(BLOB, BYTES);

        Optional<AssetService.Asset> asset = service.find("7a55bdcba79c0efd.png");

        assertThat(asset).isPresent();
        assertThat(asset.get().bytes()).isEqualTo(BYTES);
        assertThat(asset.get().mime()).isEqualTo("image/png");
        assertThat(asset.get().contentHash()).isEqualTo(HASH);
    }

    @Test
    @DisplayName("② 扩展名和库里不一致 → 404。否则 `<png的哈希>.svg` 能拿 PNG 字节配 svg 类型")
    void rejectsMismatchedExtension() {
        mapper.add(HASH, BLOB, ".png", BYTES.length);
        blobs.put(BLOB, BYTES);

        assertThat(service.find("7a55bdcba79c0efd.svg")).isEmpty();
    }

    @Test
    @DisplayName("③ 哈希差一位 → 404（前缀查询查不到，不该退化成模糊匹配）")
    void rejectsUnknownHash() {
        mapper.add(HASH, BLOB, ".png", BYTES.length);

        assertThat(service.find("7a55bdcba79c0efe.png")).isEmpty();
    }

    @Test
    @DisplayName("④ 前缀里的 LIKE 通配符要挡在查库之前 —— `%` 不能变成「匹配全表」")
    void rejectsLikeWildcardsBeforeQuerying() {
        mapper.add(HASH, BLOB, ".png", BYTES.length);
        blobs.put(BLOB, BYTES);

        assertThat(service.find("%.png")).isEmpty();
        assertThat(service.find("7a55bdcba79c0ef%.png")).isEmpty();
        assertThat(service.find("________________.png")).isEmpty();   // `_` 也是通配符
        assertThat(mapper.queries)
                .as("形状不对的前缀根本不该发出查询")
                .isEmpty();
    }

    @Test
    @DisplayName("⑤ 前缀长度不是 16 → 404。短前缀会匹配到多行，长前缀是伪造的")
    void rejectsWrongPrefixLength() {
        mapper.add(HASH, BLOB, ".png", BYTES.length);

        assertThat(service.find("7a55bd.png")).isEmpty();
        assertThat(service.find((HASH + ".png"))).isEmpty();
        assertThat(service.find(".png")).isEmpty();
        assertThat(service.find("7a55bdcba79c0efd")).as("没有扩展名").isEmpty();
    }

    @Test
    @DisplayName("⑥ 两份内容撞同一个 16 位前缀 → 两个都不发。给哪一个都是错的")
    void refusesToGuessOnPrefixCollision() {
        mapper.add("7a55bdcba79c0efd" + "1".repeat(48), BLOB, ".png", BYTES.length);
        mapper.add("7a55bdcba79c0efd" + "2".repeat(48), "b".repeat(40), ".png", BYTES.length);
        blobs.put(BLOB, BYTES);

        assertThat(service.find("7a55bdcba79c0efd.png")).isEmpty();
    }

    @Test
    @DisplayName("⑦ blob_id 还是空的旧行 → 404，**不退回按路径取**。那正是要去掉的东西")
    void rejectsRowsWithoutBlobId() {
        mapper.add(HASH, null, ".png", BYTES.length);

        assertThat(service.find("7a55bdcba79c0efd.png")).isEmpty();
    }

    @Test
    @DisplayName("⑧ 登记过但对象库里没有（浅克隆） → 404，不抛")
    void missingBlobIsNotFoundNotAnError() {
        mapper.add(HASH, BLOB, ".png", BYTES.length);
        // blobs 里刻意不放

        assertThat(service.find("7a55bdcba79c0efd.png")).isEmpty();
    }

    // ─── 替身 ─────────────────────────────────────────────────────────

    /** 只实现前缀查询，顺带记下「发过哪些查询」，好断言用例 ④ 的短路。 */
    private static class FakeMapper implements AssetLookupMapper {

        private final Map<String, AssetRow> rows = new LinkedHashMap<>();
        private final List<String> queries = new ArrayList<>();

        void add(String contentHash, String blobId, String ext, int bytes) {
            AssetRow row = new AssetRow();
            row.setContentHash(contentHash);
            row.setBlobId(blobId);
            row.setExt(ext);
            row.setBytes(bytes);
            rows.put(contentHash, row);
        }

        @Override
        public List<AssetRow> findByHashPrefix(String prefix, String ext) {
            queries.add(prefix);
            return rows.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .map(Map.Entry::getValue)
                    .limit(2)                       // 和 SQL 里的 LIMIT 2 对齐
                    .toList();
        }
    }

    private static class FakeBlobStore implements BlobStore {

        private final Map<String, byte[]> blobs = new LinkedHashMap<>();

        void put(String blobId, byte[] bytes) {
            blobs.put(blobId, bytes);
        }

        @Override
        public Optional<byte[]> readBlob(String blobId) {
            return Optional.ofNullable(blobs.get(blobId));
        }
    }
}
