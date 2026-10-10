package luv.vansye.asset;

import jakarta.annotation.PreDestroy;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

@Component
public class GitBlobStore implements BlobStore {

    private static final Logger log = LoggerFactory.getLogger(GitBlobStore.class);

    private static final int MAX_BYTES = 20 * 1024 * 1024;

    private final Repository repository;

    public GitBlobStore(@Value("${blog.vault.dir}") String vaultDir) throws IOException {
        if (!new File(vaultDir, ".git").exists()) throw new IOException("VAULT_DIR must be a Git working tree root");
        this.repository = new FileRepositoryBuilder()
                .findGitDir(new File(vaultDir))
                .setMustExist(true)
                .build();
        if (repository.getObjectDatabase() == null) {
            repository.close();
            throw new IOException("不是一个 git 仓库：" + vaultDir);
        }
    }

    @Override
    public Optional<byte[]> readBlob(String blobId) {
        if (blobId == null || blobId.isBlank()) {
            return Optional.empty();
        }
        ObjectId id;
        try {
            id = ObjectId.fromString(blobId.trim());
        } catch (IllegalArgumentException e) {
            log.warn("asset.blob_id 不是合法的 git 对象 id：{}", blobId);
            return Optional.empty();
        }
        try {
            ObjectLoader loader = repository.open(id);
            if (loader.getSize() > MAX_BYTES) {
                return Optional.empty();
            }
            return Optional.of(loader.getBytes());
        } catch (MissingObjectException e) {
            log.warn("对象库里没有这个 blob（服务器是不是浅克隆的？）：{}", blobId);
            return Optional.empty();
        } catch (IOException e) {
            log.warn("读 blob 失败：{}", blobId, e);
            return Optional.empty();
        }
    }

    @PreDestroy
    void close() {
        repository.close();
    }
}
