package security;

import user.User;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Remembers who is logged in. A session ends after it has been idle for too long. */
public class SessionManager {
    private static class Session {
        final User user;
        Instant lastSeen;

        Session(User user, Instant lastSeen) {
            this.user = user;
            this.lastSeen = lastSeen;
        }
    }

    private final Duration idleTimeout;
    private final Map<String, Session> sessions = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public SessionManager(Duration idleTimeout) {
        if (idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("The idle timeout must be more than zero.");
        }
        this.idleTimeout = idleTimeout;
    }

    public String start(User user) {
        return start(user, Instant.now());
    }

    public String start(User user, Instant now) {
        removeExpired(now);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(user, now));
        return token;
    }

    public User find(String token) {
        return find(token, Instant.now());
    }

    public User find(String token, Instant now) {
        if (token == null) return null;
        Session session = sessions.get(token);
        if (session == null) return null;
        if (Duration.between(session.lastSeen, now).compareTo(idleTimeout) >= 0) {
            sessions.remove(token);
            return null;
        }
        session.lastSeen = now;
        return session.user;
    }

    public void end(String token) {
        if (token != null) sessions.remove(token);
    }

    /** Ends every session of one user, except the one named in keepToken (which may be null). */
    public void endAllFor(String userId, String keepToken) {
        Iterator<Map.Entry<String, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Session> entry = it.next();
            if (entry.getValue().user.getId().equals(userId) && !entry.getKey().equals(keepToken)) {
                it.remove();
            }
        }
    }

    public int count() {
        return sessions.size();
    }

    private void removeExpired(Instant now) {
        sessions.values().removeIf(s -> Duration.between(s.lastSeen, now).compareTo(idleTimeout) >= 0);
    }
}