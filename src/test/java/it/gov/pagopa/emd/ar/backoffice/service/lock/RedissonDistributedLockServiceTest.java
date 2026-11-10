package it.gov.pagopa.emd.ar.backoffice.service.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonDistributedLockServiceTest {

    private static final String KEY_PREFIX = "ar-bff:lock:roles";
    private static final long LEASE_MS = 10_000;
    private static final long WAIT_MS = 3_000;

    private RedissonReactiveClient redisson;
    private RLockReactive lock;
    private RedissonDistributedLockService service;

    @BeforeEach
    void setUp() {
        redisson = mock(RedissonReactiveClient.class);
        lock = mock(RLockReactive.class);
        when(redisson.getLock(any(String.class))).thenReturn(lock);
        service = new RedissonDistributedLockService(redisson, KEY_PREFIX, LEASE_MS, WAIT_MS);
    }

    @Test
    void executeWithLock_AcquiresRunsAndReleases() {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class), anyLong())).thenReturn(Mono.just(true));
        when(lock.unlock(anyLong())).thenReturn(Mono.empty());

        AtomicBoolean actionRan = new AtomicBoolean(false);
        Mono<String> action = Mono.fromCallable(() -> {
            actionRan.set(true);
            return "done";
        });

        StepVerifier.create(service.executeWithLock("client:user", action))
                .expectNext("done")
                .verifyComplete();

        assertThat(actionRan).isTrue();
        verify(redisson).getLock(KEY_PREFIX + ":client:user");
        verify(lock, times(1)).unlock(anyLong());
    }

    @Test
    void executeWithLock_LockNotAcquired_FailsFastWithoutRunningAction() {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class), anyLong())).thenReturn(Mono.just(false));

        AtomicBoolean actionRan = new AtomicBoolean(false);
        Mono<String> action = Mono.fromCallable(() -> {
            actionRan.set(true);
            return "done";
        });

        StepVerifier.create(service.executeWithLock("client:user", action))
                .expectErrorMatches(ex -> ex.getMessage() != null && ex.getMessage().contains("Could not acquire lock"))
                .verify();

        assertThat(actionRan).isFalse();
        verify(lock, never()).unlock(anyLong());
    }

    @Test
    void executeWithLock_ActionFails_LockStillReleasedAndErrorPropagated() {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class), anyLong())).thenReturn(Mono.just(true));
        when(lock.unlock(anyLong())).thenReturn(Mono.empty());

        Mono<String> action = Mono.error(new IllegalStateException("boom"));

        StepVerifier.create(service.executeWithLock("client:user", action))
                .expectErrorMatches(ex -> ex instanceof IllegalStateException && "boom".equals(ex.getMessage()))
                .verify();

        verify(lock, times(1)).unlock(anyLong());
    }

    @Test
    void executeWithLock_EmptyAction_LockReleasedOnce() {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class), anyLong())).thenReturn(Mono.just(true));
        AtomicInteger releases = new AtomicInteger(0);
        when(lock.unlock(anyLong())).thenReturn(Mono.fromRunnable(releases::incrementAndGet));

        // Mono<Void> that completes empty — the common case for replaceUserRoles.
        StepVerifier.create(service.executeWithLock("client:user", Mono.<Void>empty()))
                .verifyComplete();

        assertThat(releases.get()).isEqualTo(1);
    }

    @Test
    void executeWithLock_ReleaseFailure_DoesNotMaskActionResult() {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class), anyLong())).thenReturn(Mono.just(true));
        when(lock.unlock(anyLong())).thenReturn(Mono.error(new IllegalStateException("lease already expired")));

        StepVerifier.create(service.executeWithLock("client:user", Mono.just("ok")))
                .expectNext("ok")
                .verifyComplete();
    }
}
