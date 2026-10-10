package luv.vansye.sync.domain;

import java.util.Set;

/** One validated repository-relative root shared by publication, sections and assets. */
public record PublicationLayout(String root) {
    public static final PublicationLayout DEFAULT = new PublicationLayout("博客");

    public PublicationLayout {
        if (root == null || root.isBlank() || !root.equals(root.strip())
                || root.startsWith("/") || root.endsWith("/") || root.contains("\\")
                || root.contains(":") || root.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("PUBLISH_ROOT must be a non-empty repository-relative directory");
        }
        for (String segment : root.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")
                    || Set.of("个人", "日记", "草稿", "_模板", "素材", ".git", ".obsidian").contains(segment)) {
                throw new IllegalArgumentException("PUBLISH_ROOT contains a forbidden directory segment");
            }
        }
    }

    public String path(String relative) { return root + "/" + relative; }
}
