package luv.vansye.sync.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AssetIndexBuilder {

    private static final int URL_HASH_LENGTH = 16;

    private static final int MAX_BYTES = 20 * 1024 * 1024;

    private static final Set<String> ASSET_EXTENSIONS = AssetMediaTypes.extensions();

    private final PublicationGate gate;

    public AssetIndexBuilder(PublicationGate gate) {
        this.gate = gate;
    }

    public record Result(AssetIndex index, List<AssetDetail> details,
                         List<String> collisions, List<String> unreadable) {}

    public Result build(VaultReader reader) {
        Map<String, String> fullHashByPath = new LinkedHashMap<>();
        Map<String, Integer> bytesByPath = new LinkedHashMap<>();
        Map<String, String> blobIdByPath = new LinkedHashMap<>();
        List<String> unreadable = new ArrayList<>();

        for (String path : reader.listFiles()) {
            if (!isAsset(path) || !gate.inPublishArea(path)) {
                continue;
            }
            Optional<String> blobId = reader.blobIdOf(path);
            Optional<byte[]> bytes = blobId.isPresent() ? reader.readBytes(path) : Optional.empty();
            if (bytes.isEmpty() || bytes.get().length > MAX_BYTES) {
                unreadable.add(path);
                continue;
            }
            fullHashByPath.put(path, sha256(bytes.get()));
            bytesByPath.put(path, bytes.get().length);
            blobIdByPath.put(path, blobId.get());
        }

        return dropCollisions(fullHashByPath, bytesByPath, blobIdByPath, unreadable);
    }

    private Result dropCollisions(Map<String, String> fullHashByPath,
                                  Map<String, Integer> bytesByPath,
                                  Map<String, String> blobIdByPath,
                                  List<String> unreadable) {
        Map<String, List<String>> pathsByHash = new LinkedHashMap<>();
        fullHashByPath.forEach((path, hash) ->
                pathsByHash.computeIfAbsent(hash, key -> new ArrayList<>()).add(path));
        Map<String, List<String>> hashesByPrefix = new LinkedHashMap<>();
        pathsByHash.keySet().forEach(hash ->
                hashesByPrefix.computeIfAbsent(prefixOf(hash), key -> new ArrayList<>()).add(hash));

        Map<String, String> index = new LinkedHashMap<>();
        List<AssetDetail> details = new ArrayList<>();
        List<String> collisions = new ArrayList<>();
        hashesByPrefix.forEach((prefix, hashes) -> {
            boolean collided = hashes.size() > 1;
            hashes.forEach(hash -> pathsByHash.get(hash).forEach(path -> {
                if (collided) {
                    collisions.add(path);
                } else {
                    index.put(path, hash);
                    details.add(new AssetDetail(path, hash, blobIdByPath.get(path), bytesByPath.get(path)));
                }
            }));
        });

        return new Result(new AssetIndex(index), List.copyOf(details),
                List.copyOf(collisions), List.copyOf(unreadable));
    }

    private String prefixOf(String hash) {
        return hash.substring(0, Math.min(URL_HASH_LENGTH, hash.length()));
    }

    private boolean isAsset(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return ASSET_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    static String hashOfText(String text) {
        return new AssetIndexBuilder(new PublicationGate())
                .sha256(text.getBytes(StandardCharsets.UTF_8));
    }
}
