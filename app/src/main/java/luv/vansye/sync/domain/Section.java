package luv.vansye.sync.domain;

import java.util.Locale;
import java.util.Optional;

public enum Section {

    ARCHIVE("文章/", "A"),

    NOTES("随笔/", "N"),

    LAB("项目/", "L"),

    ABOUT("关于.md", "P"),

    LINKS("友链.md", "P");

    private final String source;
    private final String platePrefix;

    Section(String source, String platePrefix) {
        this.source = source;
        this.platePrefix = platePrefix;
    }

    public static Optional<Section> of(String rawPath) {
        return of(rawPath, PublicationLayout.DEFAULT);
    }

    public static Optional<Section> of(String rawPath, PublicationLayout layout) {
        if (rawPath == null) {
            return Optional.empty();
        }
        String path = rawPath.replace('\\', '/');
        for (Section section : values()) {
            boolean matches = section.source.endsWith("/")
                    ? path.startsWith(layout.path(section.source))
                    : path.equals(layout.path(section.source));
            if (matches) {
                return Optional.of(section);
            }
        }
        return Optional.empty();
    }

    public String code() {
        return name();
    }

    public String platePrefix() {
        return platePrefix;
    }

    public static Optional<Section> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(code.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
