package luv.vansye.sync.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post")
public class PostEntity {

    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_UNPUBLISHED = "unpublished";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String plate;

    private String slug;
    private String vaultPath;

    private String section;

    private String title;
    private String description;
    private String bodyMd;
    private String bodyHtml;

    private String tags;

    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private LocalDateTime publishedAt;

    private String contentHash;
    private String gitCommitSha;
    private Integer wordCount;
    private Integer readMinutes;
}
