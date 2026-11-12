package api;

import com.sun.net.httpserver.HttpExchange;
import exception.InvalidTicketException;
import exception.PaymentFailedException;
import model.Receipt;
import model.Ticket;
import payment.PaymentFactory;
import payment.PaymentMethod;
import service.ParkingLot;
import service.UserDirectory;
import storage.ParkingRepository;
import storage.UserRepository;
import user.Driver;
import user.User;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The driver's own addresses. A driver only ever sees and pays tickets whose plate number is on their own account,
 * and can pay by card or online (cash is taken by the attendant).
 */
public class DriverApi {
    private static final int RECENT_PAYMENTS = 10;

    private final ParkingLot lot;
    private final ParkingRepository lotRepository;
    private final UserDirectory directory;
    private final UserRepository userRepository;
    private final AccessGuard guard;

    public DriverApi(ParkingLot lot, ParkingRepository lotRepository, UserDirectory directory,
                     UserRepository userRepository, AccessGuard guard) {
        this.lot = lot;
        this.lotRepository = lotRepository;
        this.directory = directory;
        this.userRepository = userRepository;
        this.guard = guard;
    }

    public void register(WebServer server) {
        server.addRoute("/api/my/ticket", this::handleMine);
        server.addRoute("/api/my/pay", this::handlePay);
        server.addRoute("/api/my/plates/add", this::handlePlateAdd);
        server.addRoute("/api/my/plates/remove", this::handlePlateRemove);
    }

    /* ---------- addresses ---------- */

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

        StringBuilder tickets = new StringBuilder("[");
        for (Ticket t : lot.getActiveTickets()) {
            if (!driver.ownsPlate(t.getVehicle().getPlateNumber())) continue;
            if (tickets.length() > 1) tickets.append(",");
            tickets.append(ticketJson(t));
        }
        tickets.append("]");

        // newest first, only for the driver's own plates
        StringBuilder payments = new StringBuilder("[");
        int shown = 0;
        List<Receipt> receipts = lot.getReceipts();
        for (int i = receipts.size() - 1; i >= 0 && shown < RECENT_PAYMENTS; i--) {
            Receipt r = receipts.get(i);
            if (!driver.ownsPlate(r.getPlateNumber())) continue;
            if (shown > 0) payments.append(",");
            payments.append(receiptJson(r));
            shown++;
        }
        payments.append("]");

        sendJson(exchange, 200, "{"
                + "\"plates\":" + platesJson(driver)
                + ",\"feeRule\":" + Json.quote(lot.getFeeStrategy().getDescription())
                + ",\"tickets\":" + tickets
                + ",\"payments\":" + payments
                + "}");
    }

    private void handlePay(HttpExchange exchange) throws IOException {
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
        String ticketId = form.getOrDefault("ticketId", "").trim().toUpperCase();
        String methodName = form.getOrDefault("method", "");

        if (!"CARD".equals(methodName) && !"ONLINE".equals(methodName)) {
            sendError(exchange, 400, "Drivers can pay by card or online. To pay cash, see the attendant.");
            return;
        }

        try {
            Ticket ticket = lot.findTicket(ticketId);
            // a ticket of somebody else's vehicle looks exactly like a ticket that does not exist
            if (!driver.ownsPlate(ticket.getVehicle().getPlateNumber())) {
                throw new InvalidTicketException("Ticket " + ticketId + " not found.");
            }

            PaymentMethod method = PaymentFactory.create(methodName, form.get("detail"));
            Receipt receipt = lot.checkout(ticketId, method);
            persistLot();
            lot.getEvents().record(service.EventLog.STAFF, "info", "Online payment",
                    "A driver paid PHP " + String.format("%.2f", receipt.getAmount()) + " by " + receipt.getMethodName().toLowerCase()
                            + " for " + receipt.getPlateNumber() + " (ticket " + receipt.getTicketId() + ").");
            lot.getEvents().record(driver.getId(), "info", "Payment received",
                    "Your payment for " + receipt.getPlateNumber() + " was received. The exit gate is open.");
            sendJson(exchange, 200, receiptJson(receipt));
        } catch (InvalidTicketException e) {
            sendError(exchange, 404, e.getMessage());
        } catch (PaymentFailedException | IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private void handlePlateAdd(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        Driver driver = driverOf(exchange);
        if (driver == null) {
            sendError(exchange, 403, "Only drivers can use this address.");
            return;
        }

        try {
            driver.addPlate(readForm(exchange).getOrDefault("plate", ""));
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
            return;
        }
        persistUsers();
        sendJson(exchange, 200, "{\"plates\":" + platesJson(driver) + "}");
    }

    private void handlePlateRemove(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        Driver driver = driverOf(exchange);
        if (driver == null) {
            sendError(exchange, 403, "Only drivers can use this address.");
            return;
        }

        if (!driver.removePlate(readForm(exchange).getOrDefault("plate", ""))) {
            sendError(exchange, 404, "That plate number is not on your account.");
            return;
        }
        persistUsers();
        sendJson(exchange, 200, "{\"plates\":" + platesJson(driver) + "}");
    }

    /* ---------- building the answers ---------- */

    private Driver driverOf(HttpExchange exchange) {
        User user = guard.userOf(exchange);
        return (user instanceof Driver) ? (Driver) user : null;
    }

    private String platesJson(Driver driver) {
        StringBuilder sb = new StringBuilder("[");
        for (String plate : driver.getPlates()) {
            if (sb.length() > 1) sb.append(",");
            sb.append(Json.quote(plate));
        }
        return sb.append("]").toString();
    }

    // the owner's name is left out on purpose: a driver sees their own vehicle, not who else is in the lot
    private String ticketJson(Ticket t) {
        return "{"
                + "\"ticketId\":" + Json.quote(t.getTicketId())
                + ",\"plate\":" + Json.quote(t.getVehicle().getPlateNumber())
                + ",\"vehicleType\":" + Json.quote(t.getVehicle().getVehicleType())
                + ",\"slotId\":" + Json.quote(t.getSlot().getSlotId())
                + ",\"slotType\":" + Json.quote(t.getSlot().getSlotType())
                + ",\"rate\":" + t.getSlot().getHourlyRate()
                + ",\"entryTime\":" + Json.quote(t.getEntryTime().toString())
                + ",\"hours\":" + t.getHoursParked()
                + ",\"fee\":" + lot.calculateFee(t)
                + "}";
    }

    private String receiptJson(Receipt r) {
        return "{"
                + "\"ticketId\":" + Json.quote(r.getTicketId())
                + ",\"plate\":" + Json.quote(r.getPlateNumber())
                + ",\"slotId\":" + Json.quote(r.getSlotId())
                + ",\"hours\":" + r.getHours()
                + ",\"method\":" + Json.quote(r.getMethodName())
                + ",\"amount\":" + r.getAmount()
                + ",\"change\":0.0"
                + ",\"penalty\":0.0"
                + ",\"time\":" + Json.quote(r.getTime().toString())
                + "}";
    }

    /* ---------- saving ---------- */

    private void persistLot() {
        try {
            lotRepository.save(lot);
        } catch (IOException e) {
            System.out.println("Warning: could not save data: " + e.getMessage());
        }
    }

    private void persistUsers() {
        try {
            userRepository.save(directory);
        } catch (IOException e) {
            System.out.println("Warning: could not save the user accounts: " + e.getMessage());
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
