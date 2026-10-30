package api;

import com.sun.net.httpserver.HttpExchange;
import model.ParkingSlot;
import model.Ticket;
import service.ParkingLot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ParkingApi {
    private final ParkingLot lot;

    public ParkingApi(ParkingLot lot) {
        this.lot = lot;
    }

    public void register(WebServer server) {
        server.addRoute("/api/stats", exchange -> respond(exchange, statsJson()));
        server.addRoute("/api/slots", exchange -> respond(exchange, slotsJson()));
    }

    private String statsJson() {
        int total = lot.getSlots().size();
        int free = lot.getAvailableCount();
        return "{"
                + "\"lotName\":" + Json.quote(lot.getName())
                + ",\"total\":" + total
                + ",\"available\":" + free
                + ",\"occupied\":" + (total - free)
                + ",\"revenue\":" + lot.getTotalRevenue()
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
                        .append(",\"fee\":").append(t.calculateFee());
            }
            sb.append("}");
        }
        return sb.append("]").toString();
    }

    private void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}