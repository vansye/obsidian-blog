package luv.vansye.sync.domain;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class FrontmatterParser {

    private static final String DELIMITER = "---";

    public Frontmatter parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Frontmatter.of(Map.of(), "");
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = text.split("\n", -1);

        if (!DELIMITER.equals(lines[0].trim())) {
            return Frontmatter.of(Map.of(), text);      // 没有 frontmatter，整篇都是正文
        }

        int end = indexOfClosingDelimiter(lines);
        if (end < 0) {
            return Frontmatter.failed(Frontmatter.ERR_UNTERMINATED,
                    "第 1 行有 --- 但整篇找不到配对的结束行", text);
        }

        String yamlBlock = join(lines, 1, end);
        String body = join(lines, end + 1, lines.length);

        try {
            Object parsed = newYaml().load(yamlBlock);
            if (parsed == null) {
                return Frontmatter.of(Map.of(), body);  // 空的 frontmatter 块，合法
            }
            if (!(parsed instanceof Map<?, ?> map)) {
                return Frontmatter.failed(Frontmatter.ERR_NOT_A_MAP,
                        "frontmatter 的根节点是 " + parsed.getClass().getSimpleName() + "，应该是键值对", text);
            }
            Frontmatter result = Frontmatter.of(toStringKeyed(map), body);
            String slug = result.string("slug");
            if (slug != null && (slug.length() > 128 || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))) {
                return Frontmatter.failed("invalid_slug", "slug must be 1..128 lowercase letters, digits and internal hyphens", text);
            }
            return result;
        } catch (YAMLException e) {
            return Frontmatter.failed(Frontmatter.ERR_YAML, oneLine(e.getMessage()), text);
        }
    }

    private int indexOfClosingDelimiter(String[] lines) {
        for (int i = 1; i < lines.length; i++) {
            if (DELIMITER.equals(lines[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    private String oneLine(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll("\\s+", " ").trim();
    }

    private String join(String[] lines, int from, int to) {
        return String.join("\n", Arrays.copyOfRange(lines, from, to));
    }

    private Yaml newYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
    }

    private Map<String, Object> toStringKeyed(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
