package com.employger.service;

import com.employger.repository.LoginAttemptRepository;
import com.employger.service.login.LoginAttemptStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A new LoginAttemptService on the same store stands in for a restart:
 * it must see the failures and locks the previous one saved.
 */
@SpringBootTest
@Transactional
class LoginAttemptPersistenceIntegrationTest {

    private static final Instant START = Instant.parse("2026-10-25T08:00:00Z");
    private static final String ADDRESS = "10.0.0.7";

    @Autowired
    private LoginAttemptStore store;
    @Autowired
    private LoginAttemptRepository repository;

    @Test
    void lockSurvivesARestartUntilItEnds() {
        LoginAttemptService beforeRestart = serviceAt(START);

        for (int i = 0; i < LoginAttemptService.MAX_FAILURES_PER_USERNAME; i++) {
            beforeRestart.recordFailure("Gian", ADDRESS);
        }

        assertThat(serviceAt(START.plusSeconds(60)).checkLocked("gian", "10.0.0.8"))
                .hasValueSatisfying(lock -> assertThat(lock.reason()).isEqualTo("username"));

        // 16 minutes later the lock is over, and loading clears the old rows.
        assertThat(serviceAt(START.plusSeconds(16 * 60)).checkLocked("gian", ADDRESS)).isEmpty();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void failuresBelowTheLimitAreStillCountedAfterARestart() {
        LoginAttemptService beforeRestart = serviceAt(START);

        for (int i = 0; i < LoginAttemptService.MAX_FAILURES_PER_USERNAME - 1; i++) {
            beforeRestart.recordFailure("gian", ADDRESS);
        }

        LoginAttemptService afterRestart = serviceAt(START.plusSeconds(60));

        assertThat(afterRestart.recordFailure("gian", ADDRESS)).isTrue();
        assertThat(afterRestart.checkLocked("gian", ADDRESS)).isPresent();
    }

    @Test
    void successfulLoginClearsTheSavedUsernameCount() {
        LoginAttemptService beforeRestart = serviceAt(START);

        for (int i = 0; i < LoginAttemptService.MAX_FAILURES_PER_USERNAME - 1; i++) {
            beforeRestart.recordFailure("gian", ADDRESS);
        }
        beforeRestart.recordSuccess("gian");

        LoginAttemptService afterRestart = serviceAt(START.plusSeconds(60));

        assertThat(afterRestart.recordFailure("gian", ADDRESS)).isFalse();
    }

    private LoginAttemptService serviceAt(Instant now) {
        return new LoginAttemptService(Clock.fixed(now, ZoneOffset.UTC), store);
    }
}
