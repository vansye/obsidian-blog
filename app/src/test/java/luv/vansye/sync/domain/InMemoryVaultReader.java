package luv.vansye.sync.domain;

import luv.vansye.sync.domain.PostDraftAssembler.GitTimestamps;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 测试用的 VaultReader：一个 Map 就是一个仓库。
 *
 * 有了它，SyncPipeline 的测试不需要造 git 仓库、不需要写文件，
 * 「publish: false 的文章要被跳过」这种断言毫秒级就能跑完。
 *
 * `unreadablePaths` 用来模拟外部失败——这是内存实现的关键价值：
 * 「文件读不到」在真实 git 上很难稳定复现，在这里就是往集合里加一个路径。
 */
public class InMemoryVaultReader implements VaultReader {

    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private final Map<String, GitTimestamps> timestamps = new LinkedHashMap<>();
    private final Map<String, VaultDiff> diffs = new LinkedHashMap<>();
    private final Set<String> unreadablePaths = new java.util.LinkedHashSet<>();
    private String commitSha = "0000000000000000000000000000000000000000";

    public InMemoryVaultReader text(String path, String content) {
        files.put(path, content.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    public InMemoryVaultReader binary(String path, byte[] content) {
        files.put(path, content);
        return this;
    }

    /** 文件存在（出现在 listFiles 里），但读取时失败——模拟外部世界的暂时性故障。 */
    public InMemoryVaultReader unreadable(String path) {
        files.put(path, new byte[0]);
        unreadablePaths.add(path);
        return this;
    }

    public InMemoryVaultReader gitDates(String path, GitTimestamps stamps) {
        timestamps.put(path, stamps);
        return this;
    }

    public InMemoryVaultReader sha(String sha) {
        this.commitSha = sha;
        return this;
    }

    /**
     * 预置「从 fromSha 到现在变了什么」。没预置的起点返回 empty，
     * 也就是「算不出来，请退回全量」——两种情况都要能表达，
     * 因为下游对它们的处置完全不同。
     */
    public InMemoryVaultReader diff(String fromSha, VaultDiff diff) {
        diffs.put(fromSha, diff);
        return this;
    }

    @Override
    public Optional<VaultDiff> diffSince(String fromSha) {
        return fromSha == null ? Optional.empty() : Optional.ofNullable(diffs.get(fromSha));
    }

    @Override
    public List<String> listFiles() {
        return List.copyOf(files.keySet());
    }

    @Override
    public Optional<String> readText(String path) {
        return readBytes(path).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public Optional<byte[]> readBytes(String path) {
        if (unreadablePaths.contains(path)) {
            return Optional.empty();
        }
        return Optional.ofNullable(files.get(path));
    }

    /**
     * 假的 blob id：内容的 SHA-1 前 40 位十六进制。
     *
     * **不用真的 git blob id 算法**（`blob <len>\0<bytes>` 的 SHA-1），
     * 因为这里要的性质只有两条：同内容同 id、不同内容不同 id。
     * 算得和 git 一样只会让人以为可以拿它去和真仓库对照。
     *
     * 读不到的文件返回 empty——和 readBytes 一致，这样
     * 「blob id 取不到 → 归到 unreadable」那条逻辑在内存实现下也能被测到。
     */
    @Override
    public Optional<String> blobIdOf(String path) {
        return readBytes(path).map(bytes -> {
            try {
                return java.util.HexFormat.of()
                        .formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(bytes));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("JVM 不支持 SHA-1", e);
            }
        });
    }

    @Override
    public Optional<GitTimestamps> timestamps(String path) {
        return Optional.ofNullable(timestamps.get(path));
    }

    @Override
    public String commitSha() {
        return commitSha;
    }
}
