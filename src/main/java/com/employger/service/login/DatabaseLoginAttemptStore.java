package com.employger.service.login;

import com.employger.entity.LoginAttempt;
import com.employger.repository.LoginAttemptRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Keeps login failures and locks in the login_attempts table. */
@Component
public class DatabaseLoginAttemptStore implements LoginAttemptStore {

    private final LoginAttemptRepository repository;

    public DatabaseLoginAttemptStore(LoginAttemptRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public List<Saved> loadActive(Instant windowStart, Instant now) {
        repository.deleteExpired(utc(windowStart), utc(now));

        return repository.findAllByOrderByFailedAtAsc().stream()
                .map(row -> new Saved(
                        row.getKind(),
                        row.getAttemptKey(),
                        instant(row.getFailedAt()),
                        row.getLockedUntil() != null ? instant(row.getLockedUntil()) : null))
                .toList();
    }

    @Override
    @Transactional
    public void failureCounted(LoginAttempt.Kind kind, String key, Instant at) {
        repository.save(new LoginAttempt(kind, key, utc(at), null));
    }

    @Override
    @Transactional
    public void locked(LoginAttempt.Kind kind, String key, Instant at, Instant until) {
        repository.deleteByKindAndAttemptKey(kind, key);
        repository.save(new LoginAttempt(kind, key, utc(at), utc(until)));
    }

    @Override
    @Transactional
    public void forget(LoginAttempt.Kind kind, String key) {
        repository.deleteByKindAndAttemptKey(kind, key);
    }

    @Override
    @Transactional
    public void forgetAll(LoginAttempt.Kind kind) {
        repository.deleteByKind(kind);
    }

    @Override
    @Transactional
    public void forgetAll() {
        repository.deleteAllInBatch();
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime utc) {
        return utc.toInstant(ZoneOffset.UTC);
    }
}
