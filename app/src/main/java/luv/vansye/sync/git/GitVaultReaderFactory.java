package luv.vansye.sync.git;

import luv.vansye.sync.domain.VaultReader;
import luv.vansye.sync.domain.VaultReaderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class GitVaultReaderFactory implements VaultReaderFactory {

    private final File vaultDir;
    private final String revision;

    public GitVaultReaderFactory(@Value("${blog.vault.dir}") String vaultDir,
                                 @Value("${blog.vault.revision}") String revision) {
        this.vaultDir = new File(vaultDir);
        this.revision = revision;
    }

    @Override
    public VaultReader open() throws IOException {
        return new GitVaultReader(vaultDir, revision);
    }
}
