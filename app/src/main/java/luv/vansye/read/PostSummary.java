package luv.vansye.read;

import java.time.LocalDate;
import java.util.List;

public record PostSummary(
        String plate,
        String slug,
        String section,
        String title,
        String description,
        LocalDate date,
        int wordCount,
        int readMinutes,
        List<String> tags,
        String canonicalPath) {}
