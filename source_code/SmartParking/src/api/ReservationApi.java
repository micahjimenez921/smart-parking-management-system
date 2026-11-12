package api;

import com.sun.net.httpserver.HttpExchange;
import exception.ParkingFullException;
import model.ParkingSlot;
import model.Reservation;
import service.ParkingLot;
import storage.ParkingRepository;
import user.Driver;
import user.User;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reserving slots. A driver holds a free slot for one of their own plates for a short time, and can cancel.
 * Staff see every running reservation and can cancel one.
 */
public class ReservationApi {
    public static final int MAX_PER_DRIVER = 2;

    private final ParkingLot lot;
    private final ParkingRepository repository;
    private final AccessGuard guard;

    public ReservationApi(ParkingLot lot, ParkingRepository repository, AccessGuard guard) {
        this.lot = lot;
        this.repository = repository;
        this.guard = guard;
    }

    public void register(WebServer server) {
        server.addRoute("/api/my/reservations", this::handleMine);
        server.addRoute("/api/my/reserve", this::handleReserve);
        server.addRoute("/api/my/reserve/cancel", this::handleCancelMine);
        server.addRoute("/api/reservations", this::handleAll);
        server.addRoute("/api/reservations/cancel", this::handleCancelAny);
    }

    /* ---------- the driver's addresses ---------- */

    private void handleMine(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use GET for this address.");
            return;
        }
        Driver driver = driverOf(exchange);
        if (driver == null) {
            sendError(exchange, 403, "Only drivers can use this address.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        StringBuilder mine = new StringBuilder("[");
        for (Reservation r : lot.getReservations()) {
            if (!r.getOwnerId().equals(driver.getId())) continue;
            if (mine.length() > 1) mine.append(",");
            mine.append(reservationJson(r, now));
        }
        mine.append("]");

        // what is free to reserve, by slot type
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Double> rates = lot.getRates();
        for (String type : rates.keySet()) counts.put(type, new int[1]);
        for (ParkingSlot s : lot.getSlots()) {
            if (s.isAvailable() && !lot.isReserved(s)) counts.get(s.getSlotType())[0]++;
        }
        StringBuilder options = new StringBuilder("[");
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            if (options.length() > 1) options.append(",");
            options.append("{\"type\":").append(Json.quote(entry.getKey()))
                    .append(",\"rate\":").append(rates.get(entry.getKey()))
                    .append(",\"free\":").append(entry.getValue()[0]).append("}");
        }
        options.append("]");

        StringBuilder plates = new StringBuilder("[");
        for (String plate : driver.getPlates()) {
            if (plates.length() > 1) plates.append(",");
            plates.append(Json.quote(plate));
        }
        plates.append("]");

        sendJson(exchange, 200, "{"
                + "\"holdMinutes\":" + ParkingLot.RESERVATION_MINUTES
                + ",\"max\":" + MAX_PER_DRIVER
                + ",\"plates\":" + plates
                + ",\"options\":" + options
                + ",\"reservations\":" + mine
                + "}");
    }

    private void handleReserve(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        Driver driver = driverOf(exchange);
        if (driver == null) {
            sendError(exchange, 403, "Only drivers can use this address.");
            return;
        }

        Map<String, String> form = readForm(exchange);
        String wanted = form.getOrDefault("plate", "").trim();
        String slotType = form.getOrDefault("slotType", "");

        // only a plate that is on the driver's own account can be reserved for
        String plate = null;
        for (String own : driver.getPlates()) {
            if (driver.ownsPlate(wanted) && normalize(own).equals(normalize(wanted))) plate = own;
        }
        if (plate == null) {
            sendError(exchange, 400, "That plate number is not on your account. Add it on the My vehicles page first.");
            return;
        }
        if (!lot.getRates().containsKey(slotType)) {
            sendError(exchange, 400, "Choose a slot type.");
            return;
        }

        int running = 0;
        for (Reservation r : lot.getReservations()) {
            if (r.getOwnerId().equals(driver.getId())) running++;
        }
        if (running >= MAX_PER_DRIVER) {
            sendError(exchange, 409, "You can hold at most " + MAX_PER_DRIVER + " reservations at a time.");
            return;
        }

        try {
            Reservation r = lot.reserve(driver.getId(), plate, slotType);
            persist();
            sendJson(exchange, 200, reservationJson(r, LocalDateTime.now()));
        } catch (ParkingFullException | IllegalStateException e) {
            sendError(exchange, 409, e.getMessage());
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private void handleCancelMine(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        Driver driver = driverOf(exchange);
        if (driver == null) {
            sendError(exchange, 403, "Only drivers can use this address.");
            return;
        }

        String id = readForm(exchange).getOrDefault("id", "");
        Reservation r = lot.findReservation(id);
        // somebody else's reservation looks exactly like one that does not exist
        if (r == null || !r.getOwnerId().equals(driver.getId())) {
            sendError(exchange, 404, "Reservation " + id.trim() + " not found.");
            return;
        }
        lot.cancelReservation(r.getId(), false);
        persist();
        sendJson(exchange, 200, "{\"ok\":true}");
    }

    /* ---------- the staff's addresses ---------- */

    private void handleAll(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use GET for this address.");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        StringBuilder list = new StringBuilder("[");
        for (Reservation r : lot.getReservations()) {
            if (list.length() > 1) list.append(",");
            list.append(reservationJson(r, now));
        }
        sendJson(exchange, 200, list.append("]").toString());
    }

    private void handleCancelAny(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        String id = readForm(exchange).getOrDefault("id", "");
        try {
            lot.cancelReservation(id, true);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 404, e.getMessage());
            return;
        }
        persist();
        sendJson(exchange, 200, "{\"ok\":true}");
    }

    /* ---------- building the answers ---------- */

    private Driver driverOf(HttpExchange exchange) {
        User user = guard.userOf(exchange);
        return (user instanceof Driver) ? (Driver) user : null;
    }

    private static String normalize(String plate) {
        return plate.replaceAll("[\\s-]", "").toUpperCase();
    }

    private String reservationJson(Reservation r, LocalDateTime now) {
        return "{"
                + "\"id\":" + Json.quote(r.getId())
                + ",\"plate\":" + Json.quote(r.getPlateNumber())
                + ",\"slotId\":" + Json.quote(r.getSlot().getSlotId())
                + ",\"slotType\":" + Json.quote(r.getSlot().getSlotType())
                + ",\"rate\":" + r.getSlot().getHourlyRate()
                + ",\"createdAt\":" + Json.quote(r.getCreatedAt().toString())
                + ",\"expiresAt\":" + Json.quote(r.getExpiresAt().toString())
                + ",\"minutesLeft\":" + r.minutesLeft(now)
                + "}";
    }

    private void persist() {
        try {
            repository.save(lot);
        } catch (IOException e) {
            System.out.println("Warning: could not save data: " + e.getMessage());
        }
    }

    /* ---------- small helpers ---------- */

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(20_000);
        return parseForm(new String(bytes, StandardCharsets.UTF_8));
    }

    private Map<String, String> parseForm(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isEmpty()) return values;

        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
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
