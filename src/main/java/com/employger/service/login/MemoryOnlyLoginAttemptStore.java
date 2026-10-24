package com.employger.service.login;

import com.employger.entity.LoginAttempt;

import java.time.Instant;
import java.util.List;

/**
 * Remembers nothing: counts last only as long as the service object. Used
 * by unit tests that check the counting rules without a database.
 */
public class MemoryOnlyLoginAttemptStore implements LoginAttemptStore {

    @Override
    public List<Saved> loadActive(Instant windowStart, Instant now) {
        return List.of();
    }

    @Override
    public void failureCounted(LoginAttempt.Kind kind, String key, Instant at) {
        // nothing to keep
    }

    @Override
    public void locked(LoginAttempt.Kind kind, String key, Instant at, Instant until) {
        // nothing to keep
    }

    @Override
    public void forget(LoginAttempt.Kind kind, String key) {
        // nothing to keep
    }

    @Override
    public void forgetAll(LoginAttempt.Kind kind) {
        // nothing to keep
    }

    @Override
    public void forgetAll() {
        // nothing to keep
    }
}
