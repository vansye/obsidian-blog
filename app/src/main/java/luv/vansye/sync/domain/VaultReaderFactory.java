package luv.vansye.sync.domain;

import java.io.IOException;

public interface VaultReaderFactory {

    VaultReader open() throws IOException;
}
