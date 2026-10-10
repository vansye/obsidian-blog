package luv.vansye.sync;

import luv.vansye.sync.store.SyncPersister.PersistResult;

public sealed interface SyncOutcome {

    record Done(String commitSha, PersistResult result, boolean complete) implements SyncOutcome {}

    record AlreadyHandled(String deliveryId) implements SyncOutcome {}

    record Busy() implements SyncOutcome {}

    record Failed(String message) implements SyncOutcome {}
}
