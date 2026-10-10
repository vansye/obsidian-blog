package luv.vansye.sync.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class PublicationGate {

    private static final Set<String> FORBIDDEN_ROOTS = Set.of("个人", "日记");

    private static final Set<String> FORBIDDEN_SEGMENTS = Set.of("草稿", "_模板", "素材");

    private final PublicationLayout layout;
    private final List<String> allowedPrefixes;
    private final Set<String> allowedFiles;

    public PublicationGate() { this(PublicationLayout.DEFAULT); }

    public PublicationGate(PublicationLayout layout) {
        this.layout = layout;
        this.allowedPrefixes = List.of(layout.path("文章/"), layout.path("随笔/"),
                layout.path("项目/"), layout.path("_assets/"));
        this.allowedFiles = Set.of(layout.path("关于.md"), layout.path("友链.md"));
    }
    public PublicationDecision evaluate(String rawPath, Boolean publish) {
        String path = normalize(Objects.requireNonNull(rawPath, "path"));

        if (isForbidden(path)) {
            return PublicationDecision.reject(PublicationDecision.PATH_FORBIDDEN);
        }
        if (!isMarkdown(path)) {
            return PublicationDecision.reject(PublicationDecision.NOT_MARKDOWN);
        }
        if (!isAllowed(path)) {
            return PublicationDecision.reject(PublicationDecision.PATH_NOT_ALLOWED);
        }
        if (Section.of(path, layout).isEmpty()) {
            return PublicationDecision.reject(PublicationDecision.PATH_NOT_ALLOWED);
        }
        if (!Boolean.TRUE.equals(publish)) {
            return PublicationDecision.reject(PublicationDecision.NOT_PUBLISHED);
        }
        return PublicationDecision.accept();
    }

    public boolean inPublishArea(String rawPath) {
        String path = normalize(Objects.requireNonNull(rawPath, "path"));
        return !isForbidden(path) && isAllowed(path);
    }

    public boolean isPublishCandidate(String rawPath) {
        String path = normalize(Objects.requireNonNull(rawPath, "path"));
        return isForbidden(path) || path.startsWith(layout.root() + "/");
    }

    private String normalize(String rawPath) {
        return rawPath.replace('\\', '/');
    }

    private boolean isForbidden(String path) {
        String[] segments = path.split("/");
        if (hasTraversal(segments)) {
            return true;
        }
        if (FORBIDDEN_ROOTS.contains(segments[0])) {
            return true;
        }
        for (int i = 0; i < segments.length - 1; i++) {
            if (FORBIDDEN_SEGMENTS.contains(segments[i])) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTraversal(String[] segments) {
        for (String segment : segments) {
            if ("..".equals(segment) || ".".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMarkdown(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(".md");
    }

    private boolean isAllowed(String path) {
        return allowedFiles.contains(path)
                || allowedPrefixes.stream().anyMatch(path::startsWith);
    }
}
