package luv.vansye.read;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import luv.vansye.sync.domain.Section;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReadService {

    public static final int MAX_PAGE_SIZE = 100;

    static final int MAX_OFFSET = 1_000_000;

    private final ReadMapper mapper;
    private final ObjectMapper objectMapper;

    public ReadService(ReadMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public record Page(List<PostSummary> items, int total, int page, int size) {}

    public record Neighbors(PostSummary newer, PostSummary older) {}

    public Optional<PostDetail> findByPath(String pathSegment) {
        if (pathSegment == null || pathSegment.isBlank()) {
            return Optional.empty();
        }
        PostRow row = mapper.findBySlug(pathSegment);
        if (row == null) {
            row = mapper.findByPlate(pathSegment);
        }
        return Optional.ofNullable(row).map(this::toDetail);
    }

    public Optional<PostDetail> about() {
        return Optional.ofNullable(mapper.findOneBySection(Section.ABOUT.code())).map(this::toDetail);
    }

    public Optional<PostDetail> links() {
        return Optional.ofNullable(mapper.findOneBySection(Section.LINKS.code())).map(this::toDetail);
    }

    public Page index(int page, int size) {
        int limit = clampSize(size);
        int offset = offsetOf(page, limit);
        return new Page(toSummaries(mapper.listIndex(limit, offset)),
                mapper.countIndex(), Math.max(page, 0), limit);
    }

    public Neighbors neighborsOf(String plate) {
        if (plate == null || plate.isBlank()) {
            return new Neighbors(null, null);
        }
        return new Neighbors(
                Optional.ofNullable(mapper.findNewerInSection(plate)).map(this::toSummary).orElse(null),
                Optional.ofNullable(mapper.findOlderInSection(plate)).map(this::toSummary).orElse(null));
    }

    public Page bySection(Section section, int page, int size) {
        int limit = clampSize(size);
        int offset = offsetOf(page, limit);
        return new Page(toSummaries(mapper.listBySection(section.code(), limit, offset)),
                mapper.countBySection(section.code()), Math.max(page, 0), limit);
    }

    private List<PostSummary> toSummaries(List<PostRow> rows) {
        return rows.stream().map(this::toSummary).toList();
    }

    private PostSummary toSummary(PostRow row) {
        return new PostSummary(
                row.getPlate(),
                row.getSlug(),
                row.getSection(),
                row.getTitle(),
                row.getDescription(),
                dateOf(row.getCreatedAt()),
                orZero(row.getWordCount()),
                orZero(row.getReadMinutes()),
                parseTags(row.getTags()),
                canonicalPathOf(row));
    }

    private PostDetail toDetail(PostRow row) {
        return new PostDetail(
                row.getPlate(),
                row.getSlug(),
                row.getSection(),
                row.getTitle(),
                row.getDescription(),
                row.getBodyHtml(),
                dateOf(row.getCreatedAt()),
                dateOf(row.getUpdatedAt()),
                orZero(row.getWordCount()),
                orZero(row.getReadMinutes()),
                parseTags(row.getTags()),
                canonicalPathOf(row));
    }

    private String canonicalPathOf(PostRow row) {
        String tail = (row.getSlug() == null || row.getSlug().isBlank())
                ? row.getPlate()
                : row.getSlug();
        return "/p/" + tail;
    }

    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private LocalDate dateOf(LocalDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private int clampSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) throw new IllegalArgumentException("size must be 1..100");
        return size;
    }

    private int offsetOf(int page, int limit) {
        long offset = (long) page * limit;
        if (page < 0 || offset > MAX_OFFSET) throw new IllegalArgumentException("page offset must be 0..1000000");
        return (int) offset;
    }
}
