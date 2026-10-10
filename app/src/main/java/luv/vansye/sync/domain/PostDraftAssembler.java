package luv.vansye.sync.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class PostDraftAssembler {

    private static final int SUMMARY_LENGTH = 80;

    public record GitTimestamps(LocalDate firstCommit, LocalDate lastCommit) {
        public static GitTimestamps unknown() {
            return new GitTimestamps(null, null);
        }
    }

    public PostDraft assemble(String vaultPath, Frontmatter fm, GitTimestamps git) {
        String bodyMd = fm.body();
        MarkdownBody body = new MarkdownBody(bodyMd);

        String title = firstNonNull(
                fm.string("title"),
                body.firstHeading().orElse(null),
                fileName(vaultPath));

        String description = firstNonNull(
                fm.string("description"),
                body.summary(SUMMARY_LENGTH));

        List<String> tags = fm.stringList("tags");

        LocalDate created = firstNonNull(fm.date("created"), git.firstCommit());
        LocalDate updated = firstNonNull(fm.date("updated"), git.lastCommit(), created);

        return new PostDraft(
                vaultPath,
                fm.string("slug"),          // 缺失时留 null，由数据库按图版号生成（§7.3）
                title,
                description,
                tags,
                bodyMd,
                created,
                updated,
                body.wordCount(),
                body.readMinutes(),
                contentHash(title, description, tags, bodyMd));
    }

    private String contentHash(String title, String description, List<String> tags, String bodyMd) {
        String sep = Character.toString(0);
        String canonical = String.join(sep, title, description, String.join(",", tags), bodyMd);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    private String fileName(String vaultPath) {
        String name = vaultPath.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... candidates) {
        List<T> present = new ArrayList<>();
        for (T candidate : candidates) {
            if (candidate != null) {
                present.add(candidate);
            }
        }
        return present.isEmpty() ? null : present.get(0);
    }
}
