package luv.vansye.sync.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sync_log")
public class SyncLogEntity {

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_PARTIAL = "partial";
    public static final String STATUS_FAILED = "failed";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deliveryId;
    private String fromSha;
    private String toSha;
    private LocalDateTime startedAt;
    private Integer durationMs;

    private Integer added;
    private Integer updated;
    private Integer unpublished;

    private String skippedJson;

    private String status;
    private String error;
}
