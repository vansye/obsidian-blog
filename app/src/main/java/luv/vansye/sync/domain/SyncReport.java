package luv.vansye.sync.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record SyncReport(
        String commitSha,
        Coverage coverage,
        List<String> scope,
        List<RenderedPost> published,
        List<SkippedFile> skipped,
        List<String> assetCollisions,
        AssetIndex assets,
        List<AssetDetail> assetDetails) {

    public enum Coverage {
        FULL_TREE,
        CHANGED_FILES
    }

    public List<String> scope() {
        return scope;
    }

    public boolean hasRetryableFailures() {
        return skipped.stream().anyMatch(SkippedFile::retryable);
    }

    public Set<String> retryableFailurePaths() {
        return skipped.stream()
                .filter(SkippedFile::retryable)
                .map(SkippedFile::path)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public record RenderedPost(
            PostDraft draft,
            String bodyHtml,
            List<String> missingAssets,
            List<String> blockedEmbeds,
            List<String> referencedAssets) {}

    public record SkippedFile(String path, String reason, String detail, boolean retryable) {

        public static final String READ_FAILED = "read_failed";
        public static final String DUPLICATE_SLUG = "duplicate_slug";

        public static SkippedFile permanent(String path, String reason, String detail) {
            return new SkippedFile(path, reason, detail, false);
        }

        public static SkippedFile retryable(String path, String reason, String detail) {
            return new SkippedFile(path, reason, detail, true);
        }
    }
}
