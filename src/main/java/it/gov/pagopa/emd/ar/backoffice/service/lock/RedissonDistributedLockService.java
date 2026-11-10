package it.gov.pagopa.emd.ar.backoffice.service.lock;

import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link DistributedLockService} implementation backed by Redisson's reactive
 * {@link RLockReactive}.
 * <p>
 * It uses a shared Redis instance, only the key namespace defined by {@code role-lock.key-prefix} 
 * belongs to this service, so no application data is shared with the other services.
 * <p>
 * The lock is non-blocking end to end: acquisition, the guarded action and release all run on the
 * reactive pipeline, so the WebFlux event loop is never blocked. A lease time is always applied,
 * so a holder that crashes cannot keep the lock forever.
 * <p>
 * Redisson ties lock ownership to a {@code threadId}. On a reactive pipeline the acquire and
 * release steps may run on different event-loop threads, which would make {@code unlock()} throw
 * {@code IllegalMonitorStateException} ("not locked by current thread"). To avoid this, each
 * invocation generates a stable logical {@code threadId} and passes it explicitly to both
 * {@code tryLock} and {@code unlock}, decoupling ownership from the physical carrier thread.
 */
@Service
@Slf4j
public class RedissonDistributedLockService implements DistributedLockService {

    /** Reactive Redisson client pointing to the shared Redis. */
    private final RedissonReactiveClient redisson;
    /** Dedicated key prefix, to isolate this BFF's locks from other keys on Redis. */
    private final String keyPrefix;
    /** Lease duration in milliseconds: beyond this time the lock auto-expires. */
    private final long leaseMillis;
    /** Maximum wait in milliseconds to acquire the lock before failing. */
    private final long waitMillis;

    /** Source of stable logical thread ids, one per invocation, for reactive lock ownership. */
    private final AtomicLong threadIdSeq = new AtomicLong();

    /**
     * Constructor with dependency and configuration injection.
     *
     * @param redisson    reactive Redisson client
     * @param keyPrefix   lock key prefix ({@code role-lock.key-prefix} property)
     * @param leaseMillis lease duration in ms ({@code role-lock.lease-millis} property)
     * @param waitMillis  maximum acquisition wait in ms ({@code role-lock.wait-millis} property)
     */
    public RedissonDistributedLockService(
            RedissonReactiveClient redisson,
            @Value("${role-lock.key-prefix}") String keyPrefix,
            @Value("${role-lock.lease-millis}") long leaseMillis,
            @Value("${role-lock.wait-millis}") long waitMillis) {
        this.redisson = redisson;
        this.keyPrefix = keyPrefix;
        this.leaseMillis = leaseMillis;
        this.waitMillis = waitMillis;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Acquires the lock, runs {@code action} and releases the lock exactly once on every terminal
     * signal, using a stable logical {@code threadId} for the whole invocation.
     */
    @Override
    public <T> Mono<T> executeWithLock(String lockKey, Mono<T> action) {
        // Build the full key with the dedicated prefix and get the reactive lock.
        String fullKey = keyPrefix + ":" + lockKey;
        RLockReactive lock = redisson.getLock(fullKey);

        long threadId = threadIdSeq.incrementAndGet();

        // usingWhen guarantees the release step runs exactly once on every terminal signal
        return Mono.usingWhen(
                acquire(lock, fullKey, lockKey, threadId),                              
                acquiredLock -> action,                                                 
                acquiredLock -> releaseQuietly(acquiredLock, fullKey, threadId),        
                (acquiredLock, err) -> releaseQuietly(acquiredLock, fullKey, threadId), 
                acquiredLock -> releaseQuietly(acquiredLock, fullKey, threadId));       
    }

    /**
     * Acquires the lock within the configured wait window, or fails fast.
     * Emits the held lock as the resource managed by {@link Mono#usingWhen}.
     *
     * @param lock     reactive lock to acquire
     * @param fullKey  full lock key (for logging)
     * @param lockKey  logical key (for the error message)
     * @param threadId logical ownership id
     * @return a {@link Mono} with the acquired lock, or an error if acquisition fails
     */
    private Mono<RLockReactive> acquire(RLockReactive lock, String fullKey, String lockKey, long threadId) {
        return lock.tryLock(waitMillis, leaseMillis, TimeUnit.MILLISECONDS, threadId)
                .flatMap(acquired -> {
                    if (Boolean.FALSE.equals(acquired)) {
                        log.warn("[AR-BFF][LOCK] Could not acquire lock {} within {} ms", fullKey, waitMillis);
                        return Mono.error(new ExternalServiceException("REDIS", "acquireLock",
                                "Could not acquire lock for " + lockKey + " within " + waitMillis + " ms", 409));
                    }
                    log.debug("[AR-BFF][LOCK] Acquired lock {}", fullKey);
                    return Mono.just(lock);
                });
    }

    /**
     * Releases the lock, swallowing any release error so it never masks the outcome of the guarded
     * action. If the lease has already expired, the unlock is effectively a no-op.
     *
     * @param lock     lock to release
     * @param fullKey  full lock key (for logging)
     * @param threadId logical ownership id (must match the one used on acquisition)
     * @return a {@link Mono} completing when the release is done
     */
    private Mono<Void> releaseQuietly(RLockReactive lock, String fullKey, long threadId) {
        return lock.unlock(threadId)
                .doOnSuccess(v -> log.debug("[AR-BFF][LOCK] Released lock {}", fullKey))
                .onErrorResume(err -> {
                    log.warn("[AR-BFF][LOCK] Failed to release lock {} (likely already expired): {}",
                            fullKey, err.getMessage());
                    return Mono.empty();
                });
    }
}
