package it.gov.pagopa.emd.ar.backoffice.service.lock;

import reactor.core.publisher.Mono;

/**
 * Coordinates the mutually exclusive execution of a critical section across all replicas
 * of the service, using a shared Redis instance as the lock backend.
 * <p>
 * It is used to serialize operations that are not atomic on the remote system, 
 * so two concurrent requests on the same resource cannot interleave with each other.
 */
public interface DistributedLockService {

    /**
     * Executes {@code action} while holding an exclusive lock identified by {@code lockKey}.
     * <p>
     * The lock is acquired before {@code action} is subscribed and released once it terminates,
     * both on success and on failure. If the lock cannot be acquired within the configured wait
     * window, the returned {@link Mono} fails without executing {@code action}.
     *
     * @param lockKey logical identifier of the resource to guard
     * @param action  the critical section to execute under mutual exclusion
     * @param <T>     the type produced by {@code action}
     * @return a {@link Mono} emitting the result of {@code action}, or an error if the lock could
     *         not be acquired or if {@code action} failed
     */
    <T> Mono<T> executeWithLock(String lockKey, Mono<T> action);
}
