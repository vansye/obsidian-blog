package luv.vansye.sync.domain;

public record AssetDetail(String vaultPath, String contentHash, String blobId, int bytes) {

    public AssetDetail {
        if (vaultPath == null || contentHash == null || blobId == null) {
            throw new IllegalArgumentException("附件的路径 / 内容哈希 / blob id 都不能为空：" + vaultPath);
        }
    }
}
