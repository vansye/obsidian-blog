package luv.vansye.sync.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import luv.vansye.sync.domain.AssetDetail;
import luv.vansye.sync.domain.AssetIndex;
import luv.vansye.sync.domain.AssetMediaTypes;
import luv.vansye.sync.domain.PostDraft;
import luv.vansye.sync.domain.PublicationLayout;
import luv.vansye.sync.domain.Section;
import luv.vansye.sync.domain.SyncReport;
import luv.vansye.sync.domain.SyncReport.RenderedPost;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SyncPersister {

    private enum Outcome { ADDED, UPDATED, UNCHANGED, SKIPPED }

    private final PostMapper postMapper;
    private final PlateSeqMapper plateSeqMapper;
    private final AssetMapper assetMapper;
    private final PostAssetMapper postAssetMapper;
    private final SyncLogMapper syncLogMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PublicationLayout layout;

    public SyncPersister(PostMapper postMapper, PlateSeqMapper plateSeqMapper,
                         AssetMapper assetMapper, PostAssetMapper postAssetMapper,
                         SyncLogMapper syncLogMapper, ObjectMapper objectMapper, Clock clock, PublicationLayout layout) {
        this.postMapper = postMapper;
        this.plateSeqMapper = plateSeqMapper;
        this.assetMapper = assetMapper;
        this.postAssetMapper = postAssetMapper;
        this.syncLogMapper = syncLogMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.layout = layout;
    }

    public record PersistResult(int added, int updated, int unchanged,
                               int unpublished, int conflicted, boolean unpublishSkipped) {}

    private record Removal(int count, boolean skipped) {}

    @Transactional
    public PersistResult persist(SyncReport report, String deliveryId, String fromSha,
                                 LocalDateTime startedAt, long startedNanos) {

        upsertReferencedAssets(report);

        Set<String> presentPaths = new LinkedHashSet<>();
        report.published().forEach(post -> presentPaths.add(post.draft().vaultPath()));
        Removal removal = unpublish(report, presentPaths);

        int added = 0;
        int updated = 0;
        int unchanged = 0;
        int conflicted = 0;
        List<SyncReport.SkippedFile> extraSkipped = new ArrayList<>();
        for (RenderedPost post : report.published()) {
            switch (upsertPost(post, report, extraSkipped)) {
                case ADDED -> added++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
                case SKIPPED -> conflicted++;
            }
        }

        PersistResult result = new PersistResult(added, updated, unchanged,
                removal.count(), conflicted, removal.skipped());

        List<SyncReport.SkippedFile> allSkipped = new ArrayList<>(report.skipped());
        allSkipped.addAll(extraSkipped);
        writeLog(report, deliveryId, fromSha, startedAt, startedNanos, result, allSkipped);
        return result;
    }

    private Removal unpublish(SyncReport report, Set<String> presentPaths) {
        if (report.coverage() == SyncReport.Coverage.FULL_TREE) {
            Set<String> unreadablePaths = report.retryableFailurePaths();
            int count = 0;
            for (String path : postMapper.publishedVaultPaths()) {
                if (!presentPaths.contains(path) && !unreadablePaths.contains(path)) {
                    count += postMapper.unpublish(path);
                }
            }
            return new Removal(count, false);
        }

        Set<String> candidates = new LinkedHashSet<>(report.scope());
        candidates.removeAll(presentPaths);
        candidates.removeAll(report.retryableFailurePaths());   // 读失败 ≠ 被删除
        int count = 0;
        for (String path : candidates) {
            count += postMapper.unpublish(path);   // 库里没有或已下架的，影响 0 行
        }
        return new Removal(count, false);
    }

    private void upsertReferencedAssets(SyncReport report) {
        Map<String, String> referenced = new LinkedHashMap<>();
        report.published().forEach(post -> referenced.putAll(referencedAssetHashes(post, report.assets())));
        Map<String, AssetDetail> detailByPath = new LinkedHashMap<>();
        report.assetDetails().forEach(detail -> detailByPath.put(detail.vaultPath(), detail));

        referenced.forEach((path, hash) -> {
            AssetDetail detail = detailByPath.get(path);
            if (detail == null) {
                return;
            }
            String ext = extensionOf(path);
            String mime = AssetMediaTypes.mimeOf(ext);
            if (assetMapper.findByContentHashAndExt(hash, ext) == null) {
                AssetEntity row = new AssetEntity();
                row.setContentHash(hash);
                row.setBlobId(detail.blobId());
                row.setVaultPath(path);
                row.setExt(ext);
                row.setBytes(detail.bytes());
                row.setMime(mime);
                assetMapper.insert(row);
            } else {
                assetMapper.updateLocation(hash, detail.blobId(), path, ext, detail.bytes(), mime);
            }
        });
    }

    private Outcome upsertPost(RenderedPost post, SyncReport report,
                               List<SyncReport.SkippedFile> extraSkipped) {
        PostDraft draft = post.draft();
        PostEntity existing = postMapper.findByVaultPath(draft.vaultPath());
        Map<String, String> currentAssets = referencedAssetHashes(post, report.assets());

        if (!claimSlug(draft, extraSkipped)) {
            return Outcome.SKIPPED;
        }

        if (existing == null) {
            PostEntity row = toRow(post, report.commitSha());
            row.setPlate(nextPlate(sectionOf(draft)));
            row.setStatus(PostEntity.STATUS_PUBLISHED);
            row.setPublishedAt(LocalDateTime.now(clock));
            postMapper.insert(row);
            replaceAssetLinks(row.getId(), currentAssets);
            return Outcome.ADDED;
        }

        boolean contentSame = draft.contentHash().equals(existing.getContentHash());
        boolean stillPublished = PostEntity.STATUS_PUBLISHED.equals(existing.getStatus());
        boolean assetsSame = currentAssets.equals(storedAssetHashes(existing.getId()));
        boolean renderSame = post.bodyHtml().equals(existing.getBodyHtml());
        boolean metadataSame = java.util.Objects.equals(draft.slug(), existing.getSlug())
                && java.util.Objects.equals(atStartOfDay(draft.created()), existing.getCreatedAt())
                && java.util.Objects.equals(atStartOfDay(draft.updated()), existing.getUpdatedAt());
        if (contentSame && stillPublished && assetsSame && renderSame && metadataSame) {
            return Outcome.UNCHANGED;      // 一条 SQL 都不发，这才叫幂等
        }

        PostEntity row = toRow(post, report.commitSha());
        row.setId(existing.getId());
        row.setStatus(PostEntity.STATUS_PUBLISHED);        // 下架过的重新上架
        row.setPublishedAt(existing.getPublishedAt());     // 首发时间永不变
        postMapper.overwriteById(row);                     // plate 不在覆盖列表里，永不变
        replaceAssetLinks(existing.getId(), currentAssets);
        return Outcome.UPDATED;
    }

    private boolean claimSlug(PostDraft draft, List<SyncReport.SkippedFile> extraSkipped) {
        if (draft.slug() == null) {
            return true;      // 没写 slug 的走 plate 兜底，不参与竞争
        }
        PostEntity owner = postMapper.findBySlug(draft.slug());
        if (owner == null || draft.vaultPath().equals(owner.getVaultPath())) {
            return true;
        }
        if (!PostEntity.STATUS_PUBLISHED.equals(owner.getStatus())) {
            postMapper.clearSlug(owner.getId());
            return true;
        }
        extraSkipped.add(SyncReport.SkippedFile.permanent(draft.vaultPath(),
                SyncReport.SkippedFile.DUPLICATE_SLUG,
                "slug「" + draft.slug() + "」已被仍在发布中的 " + owner.getVaultPath() + " 占用"));
        return false;
    }

    private Section sectionOf(PostDraft draft) {
        return Section.of(draft.vaultPath(), layout)
                .orElseThrow(() -> new IllegalStateException(
                        "闭门放行了一篇归不进板块的文章，闸门和 Section 定义脱钩了：" + draft.vaultPath()));
    }

    private String nextPlate(Section section) {
        String seq = PlateSeqMapper.seqNameOf(section);
        if (plateSeqMapper.increment(seq) == 0) {
            throw new IllegalStateException(
                    "Missing Flyway baseline sequence: " + seq);
        }
        return section.platePrefix() + "-" + String.format("%03d", plateSeqMapper.current(seq));
    }

    private Map<String, String> referencedAssetHashes(RenderedPost post, AssetIndex assets) {
        Map<String, String> hashes = new LinkedHashMap<>();
        post.referencedAssets().forEach(path -> {
            String hash = assets.hashByPath().get(path);
            if (hash != null) {
                hashes.put(path, hash);
            }
        });
        return hashes;
    }

    private Map<String, String> storedAssetHashes(Long postId) {
        Map<String, String> hashes = new LinkedHashMap<>();
        postAssetMapper.findByPostId(postId)
                .forEach(row -> hashes.put(row.getAssetPath(), row.getContentHash()));
        return hashes;
    }

    private void replaceAssetLinks(Long postId, Map<String, String> assets) {
        postAssetMapper.deleteByPostId(postId);
        assets.forEach((path, hash) -> postAssetMapper.insert(postId, path, hash));
    }

    private void writeLog(SyncReport report, String deliveryId, String fromSha,
                          LocalDateTime startedAt, long startedNanos, PersistResult result,
                          List<SyncReport.SkippedFile> skipped) {
        SyncLogEntity log = new SyncLogEntity();
        log.setDeliveryId(deliveryId);
        log.setFromSha(fromSha);
        log.setToSha(report.commitSha());
        log.setStartedAt(startedAt);
        log.setDurationMs((int) ((System.nanoTime() - startedNanos) / 1_000_000));
        log.setAdded(result.added());
        log.setUpdated(result.updated());
        log.setUnpublished(result.unpublished());
        log.setSkippedJson(toJson(skipped));
        log.setStatus(logStatus(report, result));
        syncLogMapper.insert(log);
    }

    private String logStatus(SyncReport report, PersistResult result) {
        boolean incomplete = report.hasRetryableFailures() || result.unpublishSkipped();
        return incomplete ? SyncLogEntity.STATUS_PARTIAL : SyncLogEntity.STATUS_SUCCESS;
    }

    private PostEntity toRow(RenderedPost post, String commitSha) {
        PostDraft draft = post.draft();
        PostEntity row = new PostEntity();
        row.setSlug(draft.slug());
        row.setVaultPath(draft.vaultPath());
        row.setSection(sectionOf(draft).code());
        row.setTitle(draft.title());
        row.setDescription(draft.description());
        row.setBodyMd(draft.bodyMd());
        row.setBodyHtml(post.bodyHtml());
        row.setTags(toJson(draft.tags()));
        row.setCreatedAt(atStartOfDay(draft.created()));
        row.setUpdatedAt(atStartOfDay(draft.updated()));
        row.setContentHash(draft.contentHash());
        row.setGitCommitSha(commitSha);
        row.setWordCount(draft.wordCount());
        row.setReadMinutes(draft.readMinutes());
        return row;
    }

    private LocalDateTime atStartOfDay(LocalDate date) {
        return (date == null ? LocalDate.now(clock) : date).atStartOfDay();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new ArrayList<>() : value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String extensionOf(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot).toLowerCase(Locale.ROOT);
    }

    public List<String> postPathsReferencing(List<String> assetPaths) {
        if (assetPaths.isEmpty()) {
            return List.of();      // 空列表进 IN (...) 是 SQL 语法错误
        }
        return postAssetMapper.findPostPathsByAssetPaths(assetPaths);
    }
}
