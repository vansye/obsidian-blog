package luv.vansye.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class VaultPoller implements SmartInitializingSingleton {
    private static final Logger log = LoggerFactory.getLogger(VaultPoller.class);
    private final SyncService sync;
    private final boolean startup;
    private final boolean polling;

    public VaultPoller(SyncService sync,
                       @Value("${blog.sync.startup-enabled:true}") boolean startup,
                       @Value("${blog.sync.poll-enabled:true}") boolean polling) {
        this.sync = sync;
        this.startup = startup;
        this.polling = polling;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!startup) return;
        var outcome = sync.fullSync("startup:" + UUID.randomUUID());
        if (!(outcome instanceof SyncOutcome.Done done) || !done.complete()) {
            throw new IllegalStateException("Initial vault reconciliation failed; inspect sync logs");
        }
    }

    @Scheduled(fixedDelayString = "${blog.sync.poll-interval:PT10M}",
            initialDelayString = "${blog.sync.poll-initial-delay:PT10M}")
    public void poll() {
        if (!polling) return;
        // Full reconciliation also repairs images committed after the referencing article.
        var outcome = sync.fullSync("poll:" + UUID.randomUUID());
        if (outcome instanceof SyncOutcome.Failed) log.warn("Scheduled reconciliation failed; retry next interval");
    }
}
