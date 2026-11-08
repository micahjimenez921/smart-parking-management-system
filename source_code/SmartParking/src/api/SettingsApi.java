package api;

import com.sun.net.httpserver.HttpExchange;
import model.ParkingSlot;
import service.ParkingLot;
import storage.ParkingRepository;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsApi {
    private final ParkingLot lot;
    private final ParkingRepository repository;

    public SettingsApi(ParkingLot lot, ParkingRepository repository) {
        this.lot = lot;
        this.repository = repository;
    }

    public void register(WebServer server) {
        server.addRoute("/api/settings", this::handleSettings);
    }

    private String settingsJson() {
        StringBuilder rates = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> entry : lot.getRates().entrySet()) {
            if (!first) rates.append(",");
            first = false;
            rates.append(Json.quote(entry.getKey())).append(":").append(entry.getValue());
        }
        rates.append("}");

        return "{"
                + "\"rates\":" + rates
                + ",\"dailyCapHours\":" + lot.getDailyCapHours()
                + ",\"lostTicketPenalty\":" + lot.getLostTicketPenalty()
                + ",\"feeRule\":" + Json.quote(lot.getFeeStrategy().getDescription())
                + ",\"maxRate\":" + ParkingSlot.MAX_RATE
                + "}";
    }

    private void handleSettings(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 200, settingsJson());
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use GET or POST for this address.");
            return;
        }

        Map<String, String> form = readForm(exchange);
        try {
            Map<String, Double> newRates = new LinkedHashMap<>();
            for (String type : lot.getRates().keySet()) {
                String text = form.get("rate_" + type);
                if (text != null) {
                    newRates.put(type, parseNumber(text, type.toLowerCase() + " rate"));
                }
            }

            int capHours = form.containsKey("dailyCapHours")
                    ? parseWholeNumber(form.get("dailyCapHours"), "daily cap")
                    : lot.getDailyCapHours();
            double penalty = form.containsKey("lostTicketPenalty")
                    ? parseNumber(form.get("lostTicketPenalty"), "lost ticket penalty")
                    : lot.getLostTicketPenalty();

            lot.updatePricing(newRates, capHours, penalty);
            persist();
            sendJson(exchange, 200, settingsJson());
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private void persist() {
        try {
            repository.save(lot);
        } catch (IOException e) {
            System.out.println("Warning: could not save data: " + e.getMessage());
        }
    }

    /* ---------- helpers ---------- */

    private double parseNumber(String text, String label) {
        try {
            return Double.parseDouble(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter the " + label + " as a number.");
        }
    }

    private int parseWholeNumber(String text, String label) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter the " + label + " as a whole number.");
        }
    }

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