package com.employger.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-23T08:00:00Z"));
    private final LoginAttemptService service = new LoginAttemptService(clock);

    @Test
    void locksUsernameOnTheFifthFailureAndOnlyThen() {
        for (int i = 1; i < LoginAttemptService.MAX_FAILURES_PER_USERNAME; i++) {
            assertThat(service.recordFailure("juan", "10.0.0.1")).isFalse();
            assertThat(service.checkLocked("juan", "10.0.0.1")).isEmpty();
        }

        assertThat(service.recordFailure("juan", "10.0.0.1")).isTrue();
        assertThat(service.checkLocked("juan", "10.0.0.1"))
                .hasValueSatisfying(lock -> {
                    assertThat(lock.reason()).isEqualTo("username");
                    assertThat(lock.minutesRemaining()).isEqualTo(15);
                });
    }

    @Test
    void usernameLockAppliesFromAnyAddressAndIgnoresCase() {
        failTimes("Juan", "10.0.0.1", LoginAttemptService.MAX_FAILURES_PER_USERNAME);

        assertThat(service.checkLocked("  JUAN ", "10.0.0.99")).isPresent();
        assertThat(service.checkLocked("art", "10.0.0.99")).isEmpty();
    }

    @Test
    void lockExpiresAfterLockDuration() {
        failTimes("juan", "10.0.0.1", LoginAttemptService.MAX_FAILURES_PER_USERNAME);

        clock.advance(LoginAttemptService.LOCK_DURATION.minusSeconds(1));
        assertThat(service.checkLocked("juan", "10.0.0.1"))
                .hasValueSatisfying(lock -> assertThat(lock.minutesRemaining()).isEqualTo(1));

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.checkLocked("juan", "10.0.0.1")).isEmpty();

        // A fresh start: one more failure does not lock again.
        assertThat(service.recordFailure("juan", "10.0.0.1")).isFalse();
    }

    @Test
    void failuresOutsideTheWindowAreForgotten() {
        failTimes("juan", "10.0.0.1", LoginAttemptService.MAX_FAILURES_PER_USERNAME - 1);

        clock.advance(LoginAttemptService.WINDOW.plusSeconds(1));

        assertThat(service.recordFailure("juan", "10.0.0.1")).isFalse();
        assertThat(service.checkLocked("juan", "10.0.0.1")).isEmpty();
    }

    @Test
    void successClearsUsernameCountButNotAddressCount() {
        failTimes("juan", "10.0.0.1", LoginAttemptService.MAX_FAILURES_PER_USERNAME - 1);
        service.recordSuccess("juan");

        assertThat(service.recordFailure("juan", "10.0.0.1")).isFalse();
        assertThat(service.checkLocked("juan", "10.0.0.1")).isEmpty();

        // The address keeps counting across usernames and successes.
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES_PER_ADDRESS; i++) {
            service.recordFailure("guess" + i, "10.0.0.2");
            service.recordSuccess("guess" + i);
        }

        assertThat(service.checkLocked("anyone", "10.0.0.2"))
                .hasValueSatisfying(lock -> assertThat(lock.reason()).isEqualTo("address"));
        assertThat(service.checkLocked("anyone", "10.0.0.3")).isEmpty();
    }

    @Test
    void unknownAndNullUsernamesAreCountedLikeRealOnes() {
        failTimes(null, "10.0.0.1", LoginAttemptService.MAX_FAILURES_PER_USERNAME);

        assertThat(service.checkLocked("", "10.0.0.5")).isPresent();
    }

    private void failTimes(String username, String address, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(username, address);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
