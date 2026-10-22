package com.employger.service;

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
 * which usernames are real. State is kept in memory: it is cleared when the
 * application restarts, which is acceptable for a single-server deployment.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES_PER_USERNAME = 5;
    static final int MAX_FAILURES_PER_ADDRESS = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    // Upper bound on tracked keys, so random usernames cannot exhaust memory.
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final Map<String, Tracker> usernames = new ConcurrentHashMap<>();
    private final Map<String, Tracker> addresses = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
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

        boolean usernameLocked = usernames
                .computeIfAbsent(normalize(username), key -> new Tracker())
                .fail(now, MAX_FAILURES_PER_USERNAME);

        boolean addressLocked = addresses
                .computeIfAbsent(String.valueOf(address), key -> new Tracker())
                .fail(now, MAX_FAILURES_PER_ADDRESS);

        return usernameLocked || addressLocked;
    }

    /**
     * Clears the failure count for a username after a successful login.
     * The address count is kept, so one valid account cannot be used to
     * reset the limit while guessing others.
     */
    public void recordSuccess(String username) {
        usernames.remove(normalize(username));
    }

    /** Forgets all failures and locks. Intended for tests. */
    public void reset() {
        usernames.clear();
        addresses.clear();
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
        }
    }

    private static final class Tracker {

        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant lockedUntil;

        synchronized boolean fail(Instant now, int limit) {

            prune(now);

            if (lockedUntil != null) {
                return false;
            }

            failures.addLast(now);

            if (failures.size() >= limit) {
                lockedUntil = now.plus(LOCK_DURATION);
                failures.clear();
                return true;
            }

            return false;
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
