package luv.vansye.sync.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownBody {

    private static final int WORDS_PER_MINUTE = 400;

    private static final Pattern H1 = Pattern.compile("^#\\s+(.+?)\\s*$");

    private final String raw;

    public MarkdownBody(String raw) {
        this.raw = raw == null ? "" : raw;
    }

    public Optional<String> firstHeading() {
        for (String line : contentLines()) {
            Matcher m = H1.matcher(line);
            if (m.matches()) {
                return Optional.of(m.group(1).trim());
            }
        }
        return Optional.empty();
    }

    public String summary(int maxChars) {
        List<String> cleaned = new ArrayList<>();
        for (String line : contentLines()) {
            cleaned.add(stripMarkdown(line));
        }
        String text = String.join(" ", cleaned).replaceAll("\\s+", " ").trim();
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }

    public int wordCount() {
        String text = String.join(" ", contentLines());
        int cjk = (int) text.codePoints().filter(MarkdownBody::isCjk).count();
        int words = 0;
        for (String token : stripCjk(text).split("\\s+")) {
            if (token.codePoints().anyMatch(Character::isLetterOrDigit)) {
                words++;
            }
        }
        return cjk + words;
    }

    public int readMinutes() {
        return Math.max(1, (int) Math.ceil(wordCount() / (double) WORDS_PER_MINUTE));
    }

    private List<String> contentLines() {
        List<String> out = new ArrayList<>();
        CodeFence fence = new CodeFence();
        for (String line : raw.split("\n", -1)) {
            if (fence.consume(line) || fence.isInside()) {
                continue;
            }
            out.add(line.replaceAll("`[^`]*`", " "));
        }
        return out;
    }

    private String stripMarkdown(String line) {
        return line
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "")        // 图片整块去掉
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")     // 链接只留文字
                .replaceAll("!\\[\\[[^\\]]*\\]\\]", "")             // Obsidian 嵌入整块去掉（里面是文件名）
                .replaceAll("\\[\\[(?:[^\\]|]*\\|)?([^\\]]*)\\]\\]", "$1")   // 双链只留显示文字
                .replaceAll("^\\s{0,3}#{1,6}\\s+", "")
                .replaceAll("^\\s{0,3}>\\s?", "")
                .replaceAll("^\\s{0,3}([-*+]|\\d+\\.)\\s+", "")
                .replaceAll("[*_~]", "");
    }

    private String stripCjk(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> out.append(isCjk(cp) ? ' ' : (char) cp));
        return out.toString();
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }
}
