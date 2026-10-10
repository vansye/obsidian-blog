package luv.vansye.asset;

import luv.vansye.sync.domain.AssetMediaTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    private static final int URL_HASH_LENGTH = 16;

    private final AssetLookupMapper mapper;
    private final BlobStore blobStore;

    public AssetService(AssetLookupMapper mapper, BlobStore blobStore) {
        this.mapper = mapper;
        this.blobStore = blobStore;
    }

    public record Asset(byte[] bytes, String mime, String contentHash) {}

    public Optional<Asset> find(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return Optional.empty();   // 没有扩展名：不是我们发出去的 URL
        }
        String prefix = fileName.substring(0, dot).toLowerCase(Locale.ROOT);
        String ext = fileName.substring(dot).toLowerCase(Locale.ROOT);
        if (!isHexOfLength(prefix, URL_HASH_LENGTH)) {
            return Optional.empty();
        }

        List<AssetRow> rows = mapper.findByHashPrefix(prefix, ext);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() > 1) {
            log.error("哈希前缀撞车，两份内容都不发：{}", prefix);
            return Optional.empty();
        }

        AssetRow row = rows.get(0);
        if (!ext.equals(row.getExt())) {
            return Optional.empty();
        }
        String blobId = row.getBlobId();
        if (blobId == null || blobId.isBlank()) {
            log.warn("附件还没有 blob_id，等下一次全量同步补：{}", row.getContentHash());
            return Optional.empty();
        }

        return blobStore.readBlob(blobId)
                .map(bytes -> new Asset(bytes, AssetMediaTypes.mimeOf(ext), row.getContentHash()));
    }

    private boolean isHexOfLength(String value, int length) {
        if (value.length() != length) {
            return false;
        }
        return value.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'));
    }
}
