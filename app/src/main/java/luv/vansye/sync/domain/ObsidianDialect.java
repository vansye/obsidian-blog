package luv.vansye.sync.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObsidianDialect {

    private static final Set<String> ASSET_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".bmp", ".pdf");

    private static final Pattern MD_IMAGE  = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]*)\\)");
    private static final Pattern EMBED     = Pattern.compile("!\\[\\[([^\\]]+)\\]\\]");
    private static final Pattern WIKILINK  = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");
    private static final Pattern LOCAL_ABSOLUTE = Pattern.compile("^(?:[A-Za-z]:[/\\\\]|file:|/)");

    public record Result(String markdown, List<String> missingAssets,
                         List<String> blockedEmbeds, List<String> resolvedAssets) {}

    public Result toStandardMarkdown(String markdown, String articlePath, AssetIndex assets) {
        List<String> missing = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        List<String> resolved = new ArrayList<>();

        StringBuilder out = new StringBuilder();
        CodeFence fence = new CodeFence();
        String[] lines = (markdown == null ? "" : markdown).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            boolean markerLine = fence.consume(lines[i]);
            boolean verbatim = markerLine || fence.isInside();
            out.append(verbatim
                    ? lines[i]
                    : transformOutsideInlineCode(lines[i], articlePath, assets, missing, blocked, resolved));
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return new Result(out.toString(), List.copyOf(missing), List.copyOf(blocked), List.copyOf(resolved));
    }

    private String transformOutsideInlineCode(String line, String articlePath, AssetIndex assets,
                                              List<String> missing, List<String> blocked,
                                              List<String> resolved) {
        StringBuilder out = new StringBuilder();
        Matcher code = INLINE_CODE.matcher(line);
        int last = 0;
        while (code.find()) {
            out.append(transform(line.substring(last, code.start()), articlePath, assets, missing, blocked, resolved));
            out.append(code.group());
            last = code.end();
        }
        out.append(transform(line.substring(last), articlePath, assets, missing, blocked, resolved));
        return out.toString();
    }

    private String transform(String text, String articlePath, AssetIndex assets,
                             List<String> missing, List<String> blocked, List<String> resolved) {
        String result = MD_IMAGE.matcher(text).replaceAll(m -> quote(
                standardImage(m.group(1), m.group(2), articlePath, assets, missing, resolved)));
        result = EMBED.matcher(result).replaceAll(m -> quote(
                embed(m.group(1), articlePath, assets, missing, blocked, resolved)));
        return WIKILINK.matcher(result).replaceAll(m -> quote(plainText(m.group(1))));
    }

    private String embed(String target, String articlePath, AssetIndex assets,
                         List<String> missing, List<String> blocked, List<String> resolved) {
        String name = beforePipe(target).trim();      // `![[x.png|500]]` 的尺寸参数丢掉
        if (!isAsset(name)) {
            blocked.add(name);
            return plainText(target);
        }
        return resolve(embedCandidates(name, articlePath), name, altOf(target), assets, missing, resolved);
    }

    private String altOf(String target) {
        int pipe = target.indexOf('|');
        if (pipe < 0) {
            return "";
        }
        String suffix = target.substring(pipe + 1).trim();
        return suffix.matches("\\d+(x\\d+)?") ? "" : suffix;
    }

    private String standardImage(String alt, String url, String articlePath,
                                 AssetIndex assets, List<String> missing, List<String> resolved) {
        String target = url.trim();
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return "![" + alt + "](" + url + ")";
        }
        if (LOCAL_ABSOLUTE.matcher(target).find()) {
            missing.add(target);
            return "";
        }
        return resolve(relativeCandidates(target, articlePath), target, alt, assets, missing, resolved);
    }

    private String resolve(List<String> candidates, String name, String alt,
                           AssetIndex assets, List<String> missing, List<String> resolved) {
        for (String candidate : candidates) {
            Optional<String> url = assets.urlFor(candidate);
            if (url.isPresent()) {
                resolved.add(candidate);
                return "![" + alt + "](" + url.get() + ")";
            }
        }
        missing.add(name);
        return "";
    }

    private List<String> embedCandidates(String name, String articlePath) {
        if (name.contains("/")) {
            return List.of(name);
        }
        String dir = articleDir(articlePath);
        return List.of(dir + "_assets/" + name, dir + name);
    }

    private List<String> relativeCandidates(String url, String articlePath) {
        String cleaned = url.startsWith("./") ? url.substring(2) : url;
        return List.of(articleDir(articlePath) + cleaned);
    }

    private String articleDir(String articlePath) {
        String dir = articlePath == null ? "" : articlePath.replace('\\', '/');
        return dir.substring(0, dir.lastIndexOf('/') + 1);
    }

    private String plainText(String target) {
        int pipe = target.indexOf('|');
        if (pipe >= 0) {
            return target.substring(pipe + 1).trim();
        }
        String text = target;
        for (char marker : new char[]{'#', '^'}) {
            int cut = text.indexOf(marker);
            if (cut > 0) {
                text = text.substring(0, cut);
            }
        }
        return text.trim();
    }

    private boolean isAsset(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return ASSET_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private String beforePipe(String target) {
        int pipe = target.indexOf('|');
        return pipe >= 0 ? target.substring(0, pipe) : target;
    }

    private String quote(String replacement) {
        return Matcher.quoteReplacement(replacement);
    }
}
