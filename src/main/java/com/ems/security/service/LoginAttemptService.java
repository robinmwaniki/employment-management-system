package com.ems.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks failed logins per username and locks the account temporarily after
 * too many consecutive failures. State is kept in memory, so with several
 * application instances each instance counts on its own.
 */
@Service
public class LoginAttemptService {

    /** Upper bound on tracked usernames, so unknown names cannot exhaust memory. */
    private static final int MAX_TRACKED = 10_000;

    private final int maxAttempts;
    private final Duration lockDuration;

    private final ConcurrentHashMap<String, Attempt> attempts =
            new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.security.max-login-attempts:5}") int maxAttempts,
            @Value("${app.security.lockout-minutes:15}") long lockoutMinutes) {

        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofMinutes(lockoutMinutes);
    }

    public void loginSucceeded(String username) {
        attempts.remove(key(username));
    }

    public void loginFailed(String username) {

        if (attempts.size() >= MAX_TRACKED) {
            purgeExpired();
        }

        attempts.compute(key(username), (k, current) -> {

            Instant now = Instant.now();

            // Start fresh when a previous lock has already run out.
            if (current == null || current.isExpired(now)) {
                current = new Attempt();
            }

            current.failures++;

            if (current.failures >= maxAttempts) {
                current.lockedUntil = now.plus(lockDuration);
            }

            return current;
        });
    }

    public boolean isLocked(String username) {

        Attempt attempt = attempts.get(key(username));

        return attempt != null
                && attempt.lockedUntil != null
                && Instant.now().isBefore(attempt.lockedUntil);
    }

    private void purgeExpired() {

        Instant now = Instant.now();

        attempts.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Attempt {

        private int failures;
        private Instant lockedUntil;

        /** True once a lock has passed; unlocked entries are never "expired". */
        private boolean isExpired(Instant now) {
            return lockedUntil != null && !now.isBefore(lockedUntil);
        }
    }
}