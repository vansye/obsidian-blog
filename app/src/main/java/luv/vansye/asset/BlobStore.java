package luv.vansye.asset;

import java.util.Optional;

public interface BlobStore {

    Optional<byte[]> readBlob(String blobId);
}
