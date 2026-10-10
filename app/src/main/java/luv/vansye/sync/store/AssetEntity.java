package luv.vansye.sync.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("asset")
public class AssetEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String contentHash;
    private String blobId;
    private String vaultPath;
    private String ext;

    private Integer bytes;
    private String mime;
    private Integer width;
    private Integer height;
}
