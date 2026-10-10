package luv.vansye.asset;

import lombok.Data;

@Data
public class AssetRow {

    private String contentHash;
    private String blobId;
    private String ext;
    private Integer bytes;
}
