package com.employger.service.login;

import com.employger.entity.LoginAttempt;

import java.time.Instant;
import java.util.List;

/**
 * Where LoginAttemptService keeps a copy of its counts and locks.
 *
 * The service always counts in memory, which is fast; a store only has to
 * remember what happened so the counts survive a restart. The service
 * calls these methods without knowing which store it was given.
 */
public interface LoginAttemptStore {

    /** One saved failure, or a lock if lockedUntil is set. */
    record Saved(LoginAttempt.Kind kind, String key, Instant failedAt, Instant lockedUntil) {
    }

    /** Everything still relevant at this moment, oldest first. */
    List<Saved> loadActive(Instant windowStart, Instant now);

    /** A failure was counted. */
    void failureCounted(LoginAttempt.Kind kind, String key, Instant at);

    /** The limit was reached: the key's failures are replaced by one lock. */
    void locked(LoginAttempt.Kind kind, String key, Instant at, Instant until);

    void forget(LoginAttempt.Kind kind, String key);

    void forgetAll(LoginAttempt.Kind kind);

    void forgetAll();
}
