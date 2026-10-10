package luv.vansye.sync.git;

import luv.vansye.sync.domain.PostDraftAssembler.GitTimestamps;
import luv.vansye.sync.domain.VaultDiff;
import luv.vansye.sync.domain.VaultReader;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GitVaultReader implements VaultReader, AutoCloseable {

    private static final int MAX_BYTES = 20 * 1024 * 1024;

    private final Repository repository;
    private final RevCommit commit;

    public GitVaultReader(File vaultDir, String revision) throws IOException {
        if (!new File(vaultDir, ".git").exists()) throw new IOException("VAULT_DIR must be a Git working tree root");
        this.repository = new FileRepositoryBuilder()
                .findGitDir(vaultDir)
                .setMustExist(true)
                .build();
        ObjectId id = repository.resolve(revision);
        if (id == null) {
            repository.close();
            throw new IOException("解析不出版本：" + revision + "，检查 " + vaultDir + " 是不是 git 仓库");
        }
        try (RevWalk walk = new RevWalk(repository)) {
            this.commit = walk.parseCommit(id);
        }
    }

    @Override
    public String commitSha() {
        return commit.getName();
    }

    @Override
    public List<String> listFiles() {
        List<String> paths = new ArrayList<>();
        try (TreeWalk walk = new TreeWalk(repository)) {
            walk.addTree(commit.getTree());
            walk.setRecursive(true);
            while (walk.next()) {
                paths.add(walk.getPathString());
            }
        } catch (IOException e) {
            throw new IllegalStateException("遍历 commit tree 失败：" + commitSha(), e);
        }
        return List.copyOf(paths);
    }

    @Override
    public Optional<String> readText(String path) {
        return readBytes(path).flatMap(this::decodeUtf8);
    }

    @Override
    public Optional<byte[]> readBytes(String path) {
        return objectIdOf(path).flatMap(id -> {
            try {
                ObjectLoader loader = repository.open(id);
                if (loader.getSize() > MAX_BYTES) {
                    return Optional.empty();
                }
                return Optional.of(loader.getBytes());
            } catch (IOException e) {
                return Optional.empty();   // 单个文件读不到 → 这次跳过，retryable
            }
        });
    }

    @Override
    public Optional<String> blobIdOf(String path) {
        return objectIdOf(path).map(ObjectId::getName);
    }

    private Optional<ObjectId> objectIdOf(String path) {
        try (TreeWalk walk = new TreeWalk(repository)) {
            walk.addTree(commit.getTree());
            walk.setRecursive(true);
            walk.setFilter(PathFilter.create(path));
            if (!walk.next()) {
                return Optional.empty();
            }
            return Optional.of(walk.getObjectId(0));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<GitTimestamps> timestamps(String path) {
        try (RevWalk walk = new RevWalk(repository)) {
            walk.markStart(walk.parseCommit(commit.getId()));
            walk.sort(RevSort.COMMIT_TIME_DESC);
            walk.setTreeFilter(AndTreeFilter.create(PathFilter.create(path), TreeFilter.ANY_DIFF));

            LocalDate newest = null;
            LocalDate oldest = null;
            for (RevCommit touched : walk) {
                LocalDate date = dateOf(touched);
                if (newest == null) {
                    newest = date;
                }
                oldest = date;      // 循环结束时停在最早那个
            }
            return oldest == null ? Optional.empty() : Optional.of(new GitTimestamps(oldest, newest));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<VaultDiff> diffSince(String fromSha) {
        if (fromSha == null || fromSha.isBlank()) {
            return Optional.empty();
        }
        try {
            ObjectId from = repository.resolve(fromSha);
            if (from == null) {
                return Optional.empty();      // 那个 commit 不在了 → 退回全量
            }
            RevCommit fromCommit;
            try (RevWalk walk = new RevWalk(repository)) {
                fromCommit = walk.parseCommit(from);
            }
            return Optional.of(scanTrees(fromCommit));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private VaultDiff scanTrees(RevCommit fromCommit) throws IOException {
        List<String> touched = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        try (ObjectReader objects = repository.newObjectReader();
             DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(objects, fromCommit.getTree());
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(objects, commit.getTree());

            diff.setRepository(repository);
            diff.setDetectRenames(false);

            for (DiffEntry entry : diff.scan(oldTree, newTree)) {
                if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                    deleted.add(entry.getOldPath());
                } else {
                    touched.add(entry.getNewPath());
                }
            }
        }
        return new VaultDiff(touched, deleted);
    }

    private Optional<String> decodeUtf8(byte[] bytes) {
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }

    private LocalDate dateOf(RevCommit revCommit) {
        PersonIdent author = revCommit.getAuthorIdent();
        return author.getWhenAsInstant().atZone(author.getZoneId()).toLocalDate();
    }

    @Override
    public void close() {
        repository.close();
    }
}
