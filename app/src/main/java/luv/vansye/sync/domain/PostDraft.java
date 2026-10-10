package luv.vansye.sync.domain;

import java.time.LocalDate;
import java.util.List;

public record PostDraft(
        String vaultPath,
        String slug,
        String title,
        String description,
        List<String> tags,
        String bodyMd,
        LocalDate created,
        LocalDate updated,
        int wordCount,
        int readMinutes,
        String contentHash) {

    public PostDraft {
        tags = List.copyOf(tags);
    }
}
