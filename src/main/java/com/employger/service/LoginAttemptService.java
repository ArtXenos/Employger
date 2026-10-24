package com.employger.service;

import com.employger.entity.LoginAttempt;
import com.employger.service.login.LoginAttemptStore;
import com.employger.service.login.MemoryOnlyLoginAttemptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing on the login endpoint.
 *
 * Failed logins are counted per username and per client IP address within a
 * sliding window. When either count reaches its limit, further logins for
 * that username or from that address are refused without checking the
 * password until the lock expires.
 *
 * Usernames are counted whether or not they exist, so a lock never reveals
 * which usernames are real. Counting happens in memory; every change is also
 * written to a LoginAttemptStore (the login_attempts table in the app) and
 * read back at startup, so a restart doesn't clear a lock early.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES_PER_USERNAME = 5;
    static final int MAX_FAILURES_PER_ADDRESS = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    // Upper bound on tracked keys, so random usernames cannot exhaust memory.
    private static final int MAX_TRACKED_KEYS = 10_000;

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final Clock clock;
    private final LoginAttemptStore store;
    private final Map<String, Tracker> usernames = new ConcurrentHashMap<>();
    private final Map<String, Tracker> addresses = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(LoginAttemptStore store) {
        this(Clock.systemUTC(), store);
    }

    LoginAttemptService(Clock clock) {
        this(clock, new MemoryOnlyLoginAttemptStore());
    }

    LoginAttemptService(Clock clock, LoginAttemptStore store) {
        this.clock = clock;
        this.store = store;
        restore();
    }

    /** Why a login attempt is refused before the password is checked. */
    public record Lock(String reason, Duration remaining) {

        /** Whole minutes left, rounded up, at least 1. */
        public long minutesRemaining() {
            return Math.max(1, (remaining.toSeconds() + 59) / 60);
        }
    }

    /**
     * Returns a lock if this username or address may not try to log in now.
     */
    public Optional<Lock> checkLocked(String username, String address) {

        Instant now = clock.instant();

        Optional<Lock> addressLock = lockOf(addresses.get(address), now, "address");

        if (addressLock.isPresent()) {
            return addressLock;
        }

        return lockOf(usernames.get(normalize(username)), now, "username");
    }

    /**
     * Records a failed login. Returns true if this failure locked the
     * username or the address.
     */
    public boolean recordFailure(String username, String address) {

        Instant now = clock.instant();

        evictExpiredIfFull(now);

        boolean usernameLocked = fail(LoginAttempt.Kind.USERNAME, normalize(username), now);
        boolean addressLocked = fail(LoginAttempt.Kind.ADDRESS, String.valueOf(address), now);

        return usernameLocked || addressLocked;
    }

    private boolean fail(LoginAttempt.Kind kind, String key, Instant now) {

        int limit = kind == LoginAttempt.Kind.USERNAME ? MAX_FAILURES_PER_USERNAME : MAX_FAILURES_PER_ADDRESS;
        Tracker tracker = trackers(kind).computeIfAbsent(key, k -> new Tracker());

        Outcome outcome = tracker.fail(now, limit);

        switch (outcome) {
            case COUNTED -> save(() -> store.failureCounted(kind, key, now));
            case LOCKED -> save(() -> store.locked(kind, key, now, now.plus(LOCK_DURATION)));
            case IGNORED -> {
                // already locked: nothing changes
            }
        }

        return outcome == Outcome.LOCKED;
    }

    /**
     * Clears the failure count for a username after a successful login.
     * The address count is kept, so one valid account cannot be used to
     * reset the limit while guessing others.
     */
    public void recordSuccess(String username) {
        String key = normalize(username);

        if (usernames.remove(key) != null) {
            save(() -> store.forget(LoginAttempt.Kind.USERNAME, key));
        }
    }

    /** Forgets all failures and locks. Intended for tests. */
    public void reset() {
        usernames.clear();
        addresses.clear();
        save(store::forgetAll);
    }

    // Usernames are matched case-insensitively by the database collation,
    // so "Admin" and "admin" must share one counter.
    static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static Optional<Lock> lockOf(Tracker tracker, Instant now, String reason) {

        if (tracker == null) {
            return Optional.empty();
        }

        Instant lockedUntil = tracker.lockedUntil(now);

        if (lockedUntil == null) {
            return Optional.empty();
        }

        return Optional.of(new Lock(reason, Duration.between(now, lockedUntil)));
    }

    private void evictExpiredIfFull(Instant now) {

        if (usernames.size() + addresses.size() < MAX_TRACKED_KEYS) {
            return;
        }

        usernames.values().removeIf(tracker -> tracker.isIdle(now));
        addresses.values().removeIf(tracker -> tracker.isIdle(now));

        // Still full: every key is active, which means an attack in
        // progress. Drop the username counters; the address counters,
        // which bound the attack, are kept.
        if (usernames.size() + addresses.size() >= MAX_TRACKED_KEYS) {
            usernames.clear();
            save(() -> store.forgetAll(LoginAttempt.Kind.USERNAME));
        }
    }

    private Map<String, Tracker> trackers(LoginAttempt.Kind kind) {
        return kind == LoginAttempt.Kind.USERNAME ? usernames : addresses;
    }

    // Rebuilds the counters from the store after a restart.
    private void restore() {

        Instant now = clock.instant();

        try {
            for (LoginAttemptStore.Saved saved : store.loadActive(now.minus(WINDOW), now)) {
                trackers(saved.kind())
                        .computeIfAbsent(saved.key(), k -> new Tracker())
                        .restore(saved.failedAt(), saved.lockedUntil());
            }
        } catch (DataAccessException e) {
            log.warn("Could not load saved login failures; counting starts from zero", e);
        }
    }

    // The in-memory count already decided the outcome; a database problem
    // must not break logging in.
    private static void save(Runnable write) {
        try {
            write.run();
        } catch (DataAccessException e) {
            log.warn("Could not save a login failure or lock", e);
        }
    }

    private enum Outcome {
        IGNORED,
        COUNTED,
        LOCKED
    }

    private static final class Tracker {

        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant lockedUntil;

        synchronized Outcome fail(Instant now, int limit) {

            prune(now);

            if (lockedUntil != null) {
                return Outcome.IGNORED;
            }

            failures.addLast(now);

            if (failures.size() >= limit) {
                lockedUntil = now.plus(LOCK_DURATION);
                failures.clear();
                return Outcome.LOCKED;
            }

            return Outcome.COUNTED;
        }

        synchronized void restore(Instant failedAt, Instant savedLockedUntil) {
            if (savedLockedUntil != null) {
                lockedUntil = savedLockedUntil;
                failures.clear();
            } else if (lockedUntil == null) {
                failures.addLast(failedAt);
            }
        }

        synchronized Instant lockedUntil(Instant now) {
            prune(now);
            return lockedUntil;
        }

        synchronized boolean isIdle(Instant now) {
            prune(now);
            return lockedUntil == null && failures.isEmpty();
        }

        private void prune(Instant now) {

            if (lockedUntil != null && !now.isBefore(lockedUntil)) {
                lockedUntil = null;
            }

            Instant windowStart = now.minus(WINDOW);

            while (!failures.isEmpty() && !failures.peekFirst().isAfter(windowStart)) {
                failures.removeFirst();
            }
        }
    }
}
