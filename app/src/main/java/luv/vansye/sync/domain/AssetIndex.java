package luv.vansye.sync.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record AssetIndex(Map<String, String> hashByPath) {

    private static final int URL_HASH_LENGTH = 16;

    public AssetIndex {
        hashByPath = Collections.unmodifiableMap(new LinkedHashMap<>(hashByPath));
    }

    public static AssetIndex empty() {
        return new AssetIndex(Map.of());
    }

    public Optional<String> urlFor(String vaultPath) {
        String hash = hashByPath.get(vaultPath);
        if (hash == null) {
            return Optional.empty();
        }
        int cut = Math.min(URL_HASH_LENGTH, hash.length());
        return Optional.of("/assets/" + hash.substring(0, cut) + extensionOf(vaultPath));
    }

    private String extensionOf(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot).toLowerCase(Locale.ROOT);
    }
}
