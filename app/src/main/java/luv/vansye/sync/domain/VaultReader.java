package luv.vansye.sync.domain;

import java.util.List;
import java.util.Optional;

public interface VaultReader extends AutoCloseable {

    List<String> listFiles();

    Optional<String> readText(String path);

    Optional<byte[]> readBytes(String path);

    Optional<String> blobIdOf(String path);

    Optional<PostDraftAssembler.GitTimestamps> timestamps(String path);

    String commitSha();

    Optional<VaultDiff> diffSince(String fromSha);

    @Override
    default void close() {}
}
