package luv.vansye.sync;

import luv.vansye.sync.domain.*;
import luv.vansye.sync.lock.SyncLock;
import luv.vansye.sync.store.SyncLogMapper;
import luv.vansye.sync.store.SyncPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class SyncService {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private final VaultReaderFactory readers;
    private final SyncPipeline pipeline;
    private final SyncPersister persister;
    private final SyncLogMapper logs;
    private final SyncLock lock;
    private final VaultFetcher fetcher;
    private final Clock clock;
    // Supported topology: one application instance per vault/database.
    private final ReentrantLock localLock = new ReentrantLock();

    public SyncService(VaultReaderFactory readers, SyncPersister persister, SyncLogMapper logs,
                       SyncLock lock, VaultFetcher fetcher, PublicationLayout layout, Clock clock) {
        this.readers = readers;
        this.persister = persister;
        this.logs = logs;
        this.lock = lock;
        this.fetcher = fetcher;
        this.pipeline = new SyncPipeline(layout);
        this.clock = clock;
    }

    public SyncOutcome sync(String deliveryId) { return guarded(deliveryId, false); }
    public SyncOutcome fullSync(String deliveryId) { return guarded(deliveryId, true); }

    private SyncOutcome guarded(String deliveryId, boolean full) {
        if (!localLock.tryLock()) return new SyncOutcome.Busy();
        Optional<String> token = Optional.empty();
        try {
            token = lock.tryAcquire();
            if (token.isEmpty()) return new SyncOutcome.Busy();
            if (logs.findByDeliveryId(deliveryId) != null) return new SyncOutcome.AlreadyHandled(deliveryId);
            LocalDateTime startedAt = LocalDateTime.now(clock);
            long startedNanos = System.nanoTime();
            VaultFetcher.Result fetched = fetcher.fetch();
            if (!fetched.ok()) {
                log.warn("sync_fetch_failed delivery={} reason={}", deliveryId, fetched.detail());
                return new SyncOutcome.Failed("fetch failed");
            }
            String fromSha = logs.lastCompleteSha();
            try (VaultReader reader = readers.open()) {
                Optional<VaultDiff> diff = full ? Optional.empty() : reader.diffSince(fromSha);
                SyncReport report = diff.map(delta -> pipeline.runIncremental(reader, delta,
                        persister.postPathsReferencing(delta.allPaths()))).orElseGet(() -> pipeline.run(reader));
                var result = persister.persist(report, deliveryId, fromSha, startedAt, startedNanos);
                boolean complete = !report.hasRetryableFailures() && !result.unpublishSkipped();
                log.info("sync_finished delivery={} status={} added={} updated={} unpublished={} duration_ms={}",
                        deliveryId, complete ? "success" : "partial", result.added(), result.updated(),
                        result.unpublished(), (System.nanoTime() - startedNanos) / 1_000_000);
                return new SyncOutcome.Done(reader.commitSha(), result, complete);
            }
        } catch (Exception e) {
            log.error("sync_failed delivery={}", deliveryId, e);
            return new SyncOutcome.Failed("synchronization failed; inspect application logs");
        } finally {
            try {
                if (token.isPresent() && !lock.release(token.get())) log.warn("sync_lock_expired delivery={}", deliveryId);
            } finally {
                localLock.unlock();
            }
        }
    }
}