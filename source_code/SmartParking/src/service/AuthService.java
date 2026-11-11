package service;

import exception.AuthenticationException;
import security.PasswordHasher;
import security.SessionManager;
import user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Logging in and out, with a lockout against guessing passwords. */
public class AuthService {
    public static final int MAX_FAILURES = 5;
    public static final Duration LOCKOUT = Duration.ofSeconds(60);

    private static class Failures {
        int count;
        Instant lockedUntil;
    }

    private final UserDirectory directory;
    private final SessionManager sessions;
    private final Map<String, Failures> failures = new HashMap<>();

    public AuthService(UserDirectory directory, SessionManager sessions) {
        this.directory = directory;
        this.sessions = sessions;
    }

    public String login(String username, String password) throws AuthenticationException {
        return login(username, password, Instant.now());
    }

    public String login(String username, String password, Instant now) throws AuthenticationException {
        String key = (username == null) ? "" : username.trim().toLowerCase();

        Failures record = failures.get(key);
        if (record != null && record.lockedUntil != null && now.isBefore(record.lockedUntil)) {
            long seconds = Duration.between(now, record.lockedUntil).getSeconds() + 1;
            throw new AuthenticationException("Too many failed attempts. Try again in " + seconds + " seconds.");
        }

        User user = directory.findByUsername(key);
        boolean correct;
        if (user == null) {
            PasswordHasher.verifyAgainstDummy(password);
            correct = false;
        } else {
            correct = PasswordHasher.verify(password, user.getPasswordHash());
        }

        if (!correct) {
            if (failures.size() > 1000) failures.clear();
            Failures f = failures.computeIfAbsent(key, k -> new Failures());
            f.count++;
            if (f.count >= MAX_FAILURES) {
                f.lockedUntil = now.plus(LOCKOUT);
                f.count = 0;
            }
            throw new AuthenticationException("Wrong username or password.");
        }

        failures.remove(key);
        return sessions.start(user, now);
    }

    public User userFor(String token) {
        return sessions.find(token);
    }

    public User userFor(String token, Instant now) {
        return sessions.find(token, now);
    }

    public void logout(String token) {
        sessions.end(token);
    }

    /** Deletes an account and ends every session it had. */
    public void removeUser(String userId) {
        directory.delete(userId);
        sessions.endAllFor(userId, null);
    }

    /** An administrator sets a new password for someone. Their open sessions end. */
    public void resetPassword(String userId, String newPassword) {
        directory.setPassword(userId, newPassword);
        sessions.endAllFor(userId, null);
    }

    /** Changes the user's own password. Their other sessions end, and the current one stays. */
    public void changePassword(User user, String currentPassword, String newPassword, String keepToken)
            throws AuthenticationException {
        if (!PasswordHasher.verify(currentPassword, user.getPasswordHash())) {
            throw new AuthenticationException("The current password is not correct.");
        }
        directory.setPassword(user.getId(), newPassword);
        sessions.endAllFor(user.getId(), keepToken);
    }
}