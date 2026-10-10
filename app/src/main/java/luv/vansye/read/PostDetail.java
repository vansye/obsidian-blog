package luv.vansye.read;

import java.time.LocalDate;
import java.util.List;

public record PostDetail(
        String plate,
        String slug,
        String section,
        String title,
        String description,
        String bodyHtml,
        LocalDate date,
        LocalDate updated,
        int wordCount,
        int readMinutes,
        List<String> tags,
        String canonicalPath) {}
