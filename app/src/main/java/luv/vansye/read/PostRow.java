package luv.vansye.read;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostRow {

    private String plate;
    private String slug;
    private String section;
    private String title;
    private String description;

    private String bodyHtml;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer wordCount;
    private Integer readMinutes;

    private String tags;
}
