package luv.vansye.sync;

import luv.vansye.sync.domain.*;
import luv.vansye.sync.lock.SyncLock;
import luv.vansye.sync.store.*;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SyncCoordinatorTest {
    @Test
    void aWebhookAndTimerCannotFetchConcurrentlyAndDedupeRunsBeforeFetch() throws Exception {
        var readers = mock(VaultReaderFactory.class);
        var logs = mock(SyncLogMapper.class);
        var lock = mock(SyncLock.class);
        var fetcher = mock(VaultFetcher.class);
        when(lock.tryAcquire()).thenReturn(Optional.of("token"));
        when(lock.release("token")).thenReturn(true);
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        when(fetcher.fetch()).thenAnswer(inv -> {
            entered.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timeout");
            return new VaultFetcher.Result(false, "offline");
        });
        var sync = new SyncService(readers, mock(SyncPersister.class), logs, lock, fetcher,
                PublicationLayout.DEFAULT, Clock.systemUTC());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var running = executor.submit(() -> sync.sync("first"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(sync.fullSync("timer")).isInstanceOf(SyncOutcome.Busy.class);
            finish.countDown();
            assertThat(running.get(5, TimeUnit.SECONDS)).isInstanceOf(SyncOutcome.Failed.class);
            when(logs.findByDeliveryId("duplicate")).thenReturn(new SyncLogEntity());
            assertThat(sync.sync("duplicate")).isInstanceOf(SyncOutcome.AlreadyHandled.class);
            verify(fetcher, times(1)).fetch();
            verifyNoInteractions(readers);
            verify(lock, times(2)).release("token");
        } finally {
            finish.countDown();
            executor.shutdownNow();
        }
    }
}
