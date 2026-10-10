package luv.vansye.sync.domain;

import luv.vansye.sync.domain.PostDraftAssembler.GitTimestamps;
import luv.vansye.sync.domain.SyncReport.RenderedPost;
import luv.vansye.sync.domain.SyncReport.SkippedFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SyncPipeline {

    private final PublicationGate gate;
    private final FrontmatterParser frontmatterParser = new FrontmatterParser();
    private final PostDraftAssembler assembler = new PostDraftAssembler();
    private final ObsidianDialect dialect = new ObsidianDialect();
    private final MarkdownRenderer renderer = new MarkdownRenderer();
    private final AssetIndexBuilder assetIndexBuilder;

    public SyncPipeline() { this(PublicationLayout.DEFAULT); }

    public SyncPipeline(PublicationLayout layout) {
        this.gate = new PublicationGate(layout);
        this.assetIndexBuilder = new AssetIndexBuilder(gate);
    }

    public SyncReport run(VaultReader reader) {
        return run(reader, reader.listFiles(), List.of(), SyncReport.Coverage.FULL_TREE);
    }

    public SyncReport runIncremental(VaultReader reader, VaultDiff diff, List<String> extraTouched) {
        Set<String> deleted = new LinkedHashSet<>(markdownOf(diff.deleted()));

        Set<String> toProcess = new LinkedHashSet<>(markdownOf(diff.touched()));
        toProcess.addAll(markdownOf(extraTouched));
        toProcess.removeAll(deleted);

        Set<String> scope = new LinkedHashSet<>(toProcess);
        scope.addAll(deleted);

        return run(reader, List.copyOf(toProcess), List.copyOf(scope),
                SyncReport.Coverage.CHANGED_FILES);
    }

    private SyncReport run(VaultReader reader, List<String> paths, List<String> scope,
                           SyncReport.Coverage coverage) {
        AssetIndexBuilder.Result assets = assetIndexBuilder.build(reader);

        List<RenderedPost> published = new ArrayList<>();
        List<SkippedFile> skipped = new ArrayList<>();

        for (String path : paths) {
            if (!isMarkdown(path)) {
                continue;   // 附件走上面的索引，其余非 md 文件不是候选
            }
            process(path, reader, assets.index(), published, skipped);
        }
        assets.unreadable().forEach(path ->
                skipped.add(SkippedFile.retryable(path, SkippedFile.READ_FAILED, "附件这次没读到或超过大小上限")));

        dropDuplicateSlugs(published, skipped);

        return new SyncReport(reader.commitSha(), coverage, scope, List.copyOf(published),
                List.copyOf(skipped), assets.collisions(), assets.index(), assets.details());
    }

    private List<String> markdownOf(List<String> paths) {
        return paths.stream().filter(this::isMarkdown).toList();
    }

    private void dropDuplicateSlugs(List<RenderedPost> published, List<SkippedFile> skipped) {
        Map<String, List<RenderedPost>> bySlug = new LinkedHashMap<>();
        published.stream()
                .filter(post -> post.draft().slug() != null)
                .forEach(post -> bySlug
                        .computeIfAbsent(post.draft().slug(), key -> new ArrayList<>())
                        .add(post));

        Set<String> dropped = new LinkedHashSet<>();
        bySlug.forEach((slug, posts) -> {
            if (posts.size() > 1) {
                posts.forEach(post -> {
                    dropped.add(post.draft().vaultPath());
                    skipped.add(SkippedFile.permanent(post.draft().vaultPath(),
                            SkippedFile.DUPLICATE_SLUG,
                            "和另外 " + (posts.size() - 1) + " 篇写了同一个 slug：" + slug));
                });
            }
        });
        published.removeIf(post -> dropped.contains(post.draft().vaultPath()));
    }

    private void process(String path, VaultReader reader, AssetIndex assets,
                         List<RenderedPost> published, List<SkippedFile> skipped) {
        Optional<String> raw = reader.readText(path);
        if (raw.isEmpty()) {
            report(skipped, SkippedFile.retryable(path, SkippedFile.READ_FAILED, "这次没读到文件内容"), path);
            return;
        }

        Frontmatter frontmatter = frontmatterParser.parse(raw.get());
        PublicationDecision decision = gate.evaluate(path, frontmatter.publish());

        if (!decision.publishable()) {
            report(skipped, describe(path, decision, frontmatter), path);
            return;
        }

        PostDraft draft = assembler.assemble(path, frontmatter, timestamps(reader, path));
        ObsidianDialect.Result converted = dialect.toStandardMarkdown(draft.bodyMd(), path, assets);
        String html = renderer.toHtml(converted.markdown());

        published.add(new RenderedPost(draft, html,
                converted.missingAssets(), converted.blockedEmbeds(), converted.resolvedAssets()));
    }

    private SkippedFile describe(String path, PublicationDecision decision, Frontmatter frontmatter) {
        boolean yamlBroken = !frontmatter.ok()
                && PublicationDecision.NOT_PUBLISHED.equals(decision.code());
        return yamlBroken
                ? SkippedFile.permanent(path, frontmatter.error(), frontmatter.detail())
                : SkippedFile.permanent(path, decision.code(), null);
    }

    private void report(List<SkippedFile> skipped, SkippedFile entry, String path) {
        if (gate.isPublishCandidate(path)) {
            skipped.add(entry);
        }
    }

    private GitTimestamps timestamps(VaultReader reader, String path) {
        return reader.timestamps(path).orElseGet(GitTimestamps::unknown);
    }

    private boolean isMarkdown(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(".md");
    }
}
