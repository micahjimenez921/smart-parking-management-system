package service;

import user.Permission;
import user.User;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Things that happened that someone should hear about (a reservation ran out, a driver paid online, ...).
 * Each event is for one audience: STAFF (anyone who can use the dashboard), ADMIN (anyone who can manage
 * users), or one account id. The newest events are kept in memory only.
 */
public class EventLog {
    public static final String STAFF = "STAFF";
    public static final String ADMIN = "ADMIN";

    private static final int KEEP = 200;
    private static final Duration SHOWN_FOR = Duration.ofHours(24);

    public static class Event {
        private final String id;
        private final LocalDateTime time;
        private final String audience;
        private final String level;
        private final String title;
        private final String text;

        Event(String id, LocalDateTime time, String audience, String level, String title, String text) {
            this.id = id;
            this.time = time;
            this.audience = audience;
            this.level = level;
            this.title = title;
            this.text = text;
        }

        public String getId() { return id; }
        public LocalDateTime getTime() { return time; }
        public String getAudience() { return audience; }
        public String getLevel() { return level; }
        public String getTitle() { return title; }
        public String getText() { return text; }
    }

    // makes ids differ between runs of the server, so a browser's "already read" list never hides new events
    private final String token = Long.toString(System.currentTimeMillis(), 36);
    private final Deque<Event> events = new ArrayDeque<>();
    private long counter = 0;

    public void record(String audience, String level, String title, String text) {
        record(audience, level, title, text, LocalDateTime.now());
    }

    public void record(String audience, String level, String title, String text, LocalDateTime time) {
        counter++;
        events.addFirst(new Event("e" + token + "-" + counter, time, audience, level, title, text));
        while (events.size() > KEEP) events.removeLast();
    }

    /** The recent events this user should see, newest first. */
    public List<Event> recentFor(User user, int limit, LocalDateTime now) {
        List<Event> result = new ArrayList<>();
        for (Event e : events) {
            if (result.size() >= limit) break;
            if (Duration.between(e.time, now).compareTo(SHOWN_FOR) > 0) continue;
            boolean mine = e.audience.equals(user.getId())
                    || (e.audience.equals(STAFF) && user.can(Permission.DASHBOARD))
                    || (e.audience.equals(ADMIN) && user.can(Permission.USERS));
            if (mine) result.add(e);
        }
        return result;
    }

    public int size() {
        return events.size();
    }
}
