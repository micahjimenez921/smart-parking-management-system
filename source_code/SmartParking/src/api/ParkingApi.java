package api;

import com.sun.net.httpserver.HttpExchange;
import exception.InvalidTicketException;
import exception.ParkingFullException;
import exception.PaymentFailedException;
import gate.GateController;
import gate.GateState;
import model.ParkingSlot;
import model.Receipt;
import model.Ticket;
import model.Vehicle;
import model.VehicleFactory;
import payment.PaymentFactory;
import payment.PaymentMethod;
import service.ParkingLot;
import storage.ParkingRepository;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParkingApi {
    private final ParkingLot lot;
    private final ParkingRepository repository;

    public ParkingApi(ParkingLot lot, ParkingRepository repository) {
        this.lot = lot;
        this.repository = repository;
    }

    private void persist() {
        try {
            repository.save(lot);
        } catch (IOException e) {
            System.out.println("Warning: could not save data: " + e.getMessage());
        }
    }

    public void register(WebServer server) {
        server.addRoute("/api/stats", exchange -> sendJson(exchange, 200, statsJson()));
        server.addRoute("/api/slots", exchange -> sendJson(exchange, 200, slotsJson()));
        server.addRoute("/api/tickets", exchange -> sendJson(exchange, 200, ticketsJson()));
        server.addRoute("/api/receipts", this::handleReceipts);
        server.addRoute("/api/ticket", this::handleTicket);
        server.addRoute("/api/park", this::handlePark);
        server.addRoute("/api/checkout", this::handleCheckout);
        server.addRoute("/api/gates", exchange -> sendJson(exchange, 200, gatesJson()));
        server.addRoute("/api/gate/close", this::handleGateClose);
    }

    /* ---------- reading data ---------- */

    private String statsJson() {
        int total = lot.getSlots().size();
        int free = lot.getAvailableCount();
        return "{"
                + "\"lotName\":" + Json.quote(lot.getName())
                + ",\"total\":" + total
                + ",\"available\":" + free
                + ",\"occupied\":" + (total - free)
                + ",\"revenue\":" + lot.getTotalRevenue()
                + ",\"feeRule\":" + Json.quote(lot.getFeeStrategy().getDescription())
                + ",\"lostTicketPenalty\":" + lot.getLostTicketPenalty()
                + "}";
    }

    private String slotsJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ParkingSlot s : lot.getSlots()) {
            if (!first) sb.append(",");
            first = false;

            sb.append("{")
                    .append("\"id\":").append(Json.quote(s.getSlotId()))
                    .append(",\"type\":").append(Json.quote(s.getSlotType()))
                    .append(",\"rate\":").append(s.getHourlyRate())
                    .append(",\"available\":").append(s.isAvailable());

            Ticket t = lot.findTicketBySlot(s);
            if (t != null) {
                sb.append(",\"plate\":").append(Json.quote(t.getVehicle().getPlateNumber()))
                        .append(",\"vehicleType\":").append(Json.quote(t.getVehicle().getVehicleType()))
                        .append(",\"owner\":").append(Json.quote(t.getVehicle().getOwnerName()))
                        .append(",\"ticketId\":").append(Json.quote(t.getTicketId()))
                        .append(",\"entryTime\":").append(Json.quote(t.getEntryTime().toString()))
                        .append(",\"hours\":").append(t.getHoursParked())
                        .append(",\"fee\":").append(lot.calculateFee(t));
            }
            sb.append("}");
        }
        return sb.append("]").toString();
    }

    private String gateJson(GateController gate, LocalDateTime now) {
        boolean open = gate.getState(now) == GateState.OPEN;
        return "{"
                + "\"id\":" + Json.quote(gate.getGateId())
                + ",\"state\":" + Json.quote(open ? "OPEN" : "CLOSED")
                + ",\"plate\":" + (open ? Json.quote(gate.getOpenedFor(now)) : "null")
                + ",\"secondsLeft\":" + gate.getSecondsLeft(now)
                + "}";
    }

    private String gatesJson() {
        LocalDateTime now = LocalDateTime.now();
        return "[" + gateJson(lot.getEntryGate(), now) + "," + gateJson(lot.getExitGate(), now) + "]";
    }

    private String ticketJson(Ticket t) {
        return ticketJson(t, false);
    }

    private String ticketJson(Ticket t, boolean lostTicket) {
        double fee = lostTicket ? lot.calculateLostTicketFee(t) : lot.calculateFee(t);
        return "{"
                + "\"ticketId\":" + Json.quote(t.getTicketId())
                + ",\"plate\":" + Json.quote(t.getVehicle().getPlateNumber())
                + ",\"vehicleType\":" + Json.quote(t.getVehicle().getVehicleType())
                + ",\"owner\":" + Json.quote(t.getVehicle().getOwnerName())
                + ",\"slotId\":" + Json.quote(t.getSlot().getSlotId())
                + ",\"slotType\":" + Json.quote(t.getSlot().getSlotType())
                + ",\"rate\":" + t.getSlot().getHourlyRate()
                + ",\"entryTime\":" + Json.quote(t.getEntryTime().toString())
                + ",\"hours\":" + t.getHoursParked()
                + ",\"fee\":" + fee
                + ",\"lostTicket\":" + lostTicket
                + ",\"penalty\":" + (lostTicket ? lot.getLostTicketPenalty() : 0.0)
                + "}";
    }

    private String ticketsJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Ticket t : lot.getActiveTickets()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(ticketJson(t));
        }
        return sb.append("]").toString();
    }

    private String receiptRowJson(Receipt r) {
        return "{"
                + "\"ticketId\":" + Json.quote(r.getTicketId())
                + ",\"plate\":" + Json.quote(r.getPlateNumber())
                + ",\"slotId\":" + Json.quote(r.getSlotId())
                + ",\"hours\":" + r.getHours()
                + ",\"method\":" + Json.quote(r.getMethodName())
                + ",\"amount\":" + r.getAmount()
                + ",\"time\":" + Json.quote(r.getTime().toString())
                + "}";
    }

    private String receiptJson(Receipt r, double change, double penalty) {
        return "{"
                + "\"ticketId\":" + Json.quote(r.getTicketId())
                + ",\"plate\":" + Json.quote(r.getPlateNumber())
                + ",\"slotId\":" + Json.quote(r.getSlotId())
                + ",\"hours\":" + r.getHours()
                + ",\"method\":" + Json.quote(r.getMethodName())
                + ",\"amount\":" + r.getAmount()
                + ",\"change\":" + change
                + ",\"penalty\":" + penalty
                + ",\"time\":" + Json.quote(r.getTime().toString())
                + "}";
    }

    /* ---------- actions ---------- */

    private void handleGateClose(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        Map<String, String> form = readForm(exchange);
        GateController gate = lot.findGate(form.getOrDefault("gate", "").trim());
        if (gate == null) {
            sendError(exchange, 404, "Unknown gate.");
            return;
        }
        gate.vehiclePassed();
        sendJson(exchange, 200, gateJson(gate, LocalDateTime.now()));
    }

    private void handleReceipts(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use GET for this address.");
            return;
        }
        Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
        int limit = 100;
        try {
            limit = Integer.parseInt(query.getOrDefault("limit", "100"));
        } catch (NumberFormatException e) {
            /* keep the default */
        }
        limit = Math.max(1, Math.min(limit, 1000));

        List<Receipt> newestFirst = new ArrayList<>(lot.getReceipts());
        newestFirst.sort(Comparator.comparing(Receipt::getTime).reversed());

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < newestFirst.size() && i < limit; i++) {
            if (i > 0) sb.append(",");
            sb.append(receiptRowJson(newestFirst.get(i)));
        }
        sendJson(exchange, 200, sb.append("]").toString());
    }

    private void handleTicket(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use GET for this address.");
            return;
        }
        Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
        String id = query.getOrDefault("id", "").trim().toUpperCase();
        String plate = query.getOrDefault("plate", "").trim();
        boolean lost = "true".equals(query.get("lost"));

        try {
            Ticket ticket = plate.isEmpty() ? lot.findTicket(id) : lot.findTicketByPlate(plate);
            sendJson(exchange, 200, ticketJson(ticket, lost));
        } catch (InvalidTicketException e) {
            sendError(exchange, 404, e.getMessage());
        }
    }

    private void handlePark(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        Map<String, String> form = readForm(exchange);
        String plate = form.getOrDefault("plate", "").trim().toUpperCase();
        String owner = form.getOrDefault("owner", "").trim();
        String vehicleType = form.getOrDefault("vehicleType", "");
        String slotType = form.getOrDefault("slotType", "");

        if (!plate.matches("[A-Z0-9 -]{2,12}")) {
            sendError(exchange, 400, "Enter a valid plate number (2 to 12 letters, numbers, spaces or dashes).");
            return;
        }
        if (owner.isEmpty() || owner.length() > 60) {
            sendError(exchange, 400, "Enter the owner's name (up to 60 characters).");
            return;
        }

        Vehicle vehicle;
        try {
            vehicle = VehicleFactory.create(vehicleType, plate, owner);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
            return;
        }

        try {
            Ticket ticket = lot.parkVehicle(vehicle, slotType);
            persist();
            sendJson(exchange, 200, ticketJson(ticket));
        } catch (ParkingFullException | IllegalStateException e) {
            sendError(exchange, 409, e.getMessage());
        }
    }

    private void handleCheckout(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        Map<String, String> form = readForm(exchange);
        String ticketId = form.getOrDefault("ticketId", "").trim().toUpperCase();
        boolean lost = "true".equals(form.get("lost"));

        PaymentMethod method;
        try {
            method = PaymentFactory.create(form.getOrDefault("method", ""), form.get("detail"));
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
            return;
        }

        try {
            Receipt receipt = lot.checkout(ticketId, method, lost);
            persist();
            double penalty = lost ? lot.getLostTicketPenalty() : 0.0;
            sendJson(exchange, 200, receiptJson(receipt, method.getChange(receipt.getAmount()), penalty));
        } catch (InvalidTicketException e) {
            sendError(exchange, 404, e.getMessage());
        } catch (PaymentFailedException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    /* ---------- helpers ---------- */

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