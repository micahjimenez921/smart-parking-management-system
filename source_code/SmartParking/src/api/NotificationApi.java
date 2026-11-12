package api;

import com.sun.net.httpserver.HttpExchange;
import model.ParkingSlot;
import model.Reservation;
import model.Ticket;
import service.EventLog;
import service.ParkingLot;
import user.Permission;
import user.User;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The notification bell. Each person gets the alerts that fit their role: warnings about the lot's
 * condition right now (for staff), their own reservations and vehicles (for drivers), and the recent
 * events that were meant for them.
 */
public class NotificationApi {
    public static final int EXPIRING_SOON_MINUTES = 5;
    public static final int LONG_STAY_HOURS = 12;
    private static final int MAX_ITEMS = 30;

    private final ParkingLot lot;
    private final AccessGuard guard;

    public NotificationApi(ParkingLot lot, AccessGuard guard) {
        this.lot = lot;
        this.guard = guard;
    }

    public void register(WebServer server) {
        server.addRoute("/api/notifications", this::handle);
    }

    private static class Item {
        final String id;
        final String level;
        final String title;
        final String text;
        final LocalDateTime time;

        Item(String id, String level, String title, String text, LocalDateTime time) {
            this.id = id;
            this.level = level;
            this.title = title;
            this.text = text;
            this.time = time;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use GET for this address.");
            return;
        }
        User user = guard.userOf(exchange);
        if (user == null) {
            sendError(exchange, 401, "Please sign in.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        lot.purgeExpired(now);

        List<Item> items = new ArrayList<>();
        if (user.can(Permission.DASHBOARD)) addLotAlerts(items, now);
        if (user.can(Permission.SELF_SERVICE)) addDriverAlerts(items, user, now);
        for (EventLog.Event e : lot.getEvents().recentFor(user, MAX_ITEMS, now)) {
            items.add(new Item(e.getId(), e.getLevel(), e.getTitle(), e.getText(), e.getTime()));
        }

        // problems first, then the newest
        items.sort((a, b) -> {
            int byLevel = Integer.compare(rank(a.level), rank(b.level));
            return byLevel != 0 ? byLevel : b.time.compareTo(a.time);
        });

        StringBuilder sb = new StringBuilder("{\"items\":[");
        for (int i = 0; i < items.size() && i < MAX_ITEMS; i++) {
            Item item = items.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(Json.quote(item.id))
                    .append(",\"level\":").append(Json.quote(item.level))
                    .append(",\"title\":").append(Json.quote(item.title))
                    .append(",\"text\":").append(Json.quote(item.text))
                    .append(",\"time\":").append(Json.quote(item.time.toString()))
                    .append("}");
        }
        sendJson(exchange, 200, sb.append("]}").toString());
    }

    private static int rank(String level) {
        if ("error".equals(level)) return 0;
        if ("warn".equals(level)) return 1;
        return 2;
    }

    /* ---------- what is true right now ---------- */

    private void addLotAlerts(List<Item> items, LocalDateTime now) {
        Map<String, int[]> counts = new LinkedHashMap<>(); // type -> {slots, free}
        for (ParkingSlot s : lot.getSlots()) {
            int[] c = counts.computeIfAbsent(s.getSlotType(), t -> new int[2]);
            c[0]++;
            if (s.isAvailable() && !lot.isReserved(s)) c[1]++;
        }
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            String type = label(entry.getKey());
            int total = entry.getValue()[0];
            int free = entry.getValue()[1];
            if (free == 0) {
                items.add(new Item("live:full:" + entry.getKey(), "error", type + " slots are full",
                        "No " + type.toLowerCase() + " slot can be given to a new vehicle right now.", now));
            } else if (total >= 4 && free <= Math.max(1, total / 5)) {
                items.add(new Item("live:low:" + entry.getKey() + ":" + free, "warn", type + " slots are almost full",
                        "Only " + free + " of " + total + " " + type.toLowerCase() + " slots are free.", now));
            }
        }

        for (Reservation r : lot.getReservations()) {
            if (r.minutesLeft(now) <= EXPIRING_SOON_MINUTES) {
                items.add(new Item("live:soon:" + r.getId(), "info", "Reservation running out",
                        "The reservation for " + r.getPlateNumber() + " (slot " + r.getSlot().getSlotId()
                                + ") ends in " + r.minutesLeft(now) + " min.", now));
            }
        }

        for (Ticket t : lot.getActiveTickets()) {
            if (t.getHoursParked() >= LONG_STAY_HOURS) {
                items.add(new Item("live:long:" + t.getTicketId(), "warn", "Long stay",
                        t.getVehicle().getPlateNumber() + " has been in slot " + t.getSlot().getSlotId()
                                + " for " + t.getHoursParked() + " hours.", now));
            }
        }
    }

    private void addDriverAlerts(List<Item> items, User user, LocalDateTime now) {
        for (Reservation r : lot.getReservations()) {
            if (r.getOwnerId().equals(user.getId()) && r.minutesLeft(now) <= EXPIRING_SOON_MINUTES) {
                items.add(new Item("live:mine-soon:" + r.getId(), "warn", "Your reservation is running out",
                        "Reservation " + r.getId() + " for " + r.getPlateNumber() + " (slot "
                                + r.getSlot().getSlotId() + ") ends in " + r.minutesLeft(now) + " min.", now));
            }
        }
    }

    private static String label(String type) {
        if (type == null || type.isEmpty()) return "";
        if (type.equals("VIP")) return "VIP";
        String lower = type.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, "{\"error\":" + Json.quote(message) + "}");
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
