package luv.vansye.sync.store;

import lombok.Data;

@Data
public class PostAssetEntity {

    private Long postId;
    private String assetPath;
    private String contentHash;
}
