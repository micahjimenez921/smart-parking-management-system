package storage;

import model.ParkingSlot;
import model.Receipt;
import model.Ticket;
import model.Vehicle;
import model.VehicleFactory;
import service.ParkingLot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FileParkingRepository implements ParkingRepository {
    private static final String HEADER = "SMARTPARK-DATA 1";

    private final Path file;

    public FileParkingRepository(Path file) {
        this.file = file;
    }

    @Override
    public boolean hasSavedData() {
        return Files.isRegularFile(file);
    }

    @Override
    public void save(ParkingLot lot) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add(join("COUNTER", String.valueOf(lot.getTicketCounter())));

        for (Ticket t : lot.getActiveTickets()) {
            lines.add(join("TICKET",
                    t.getTicketId(),
                    t.getVehicle().getVehicleType(),
                    t.getVehicle().getPlateNumber(),
                    t.getVehicle().getOwnerName(),
                    t.getSlot().getSlotId(),
                    t.getEntryTime().toString()));
        }

        for (Receipt r : lot.getReceipts()) {
            lines.add(join("RECEIPT",
                    r.getTicketId(),
                    r.getPlateNumber(),
                    r.getSlotId(),
                    String.valueOf(r.getHours()),
                    r.getMethodName(),
                    String.valueOf(r.getAmount()),
                    r.getTime().toString()));
        }

        Path folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);

        // write to a temporary file first, so a crash can never leave a half-written data file
        Path temp = folder.resolve(file.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void load(ParkingLot lot) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(lines.get(0))) {
            throw new IOException("This is not a SmartPark data file.");
        }

        int tickets = 0;
        int receipts = 0;
        int skipped = 0;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;

            try {
                String[] p = split(line);
                switch (p[0]) {
                    case "COUNTER":
                        lot.setTicketCounter(Integer.parseInt(p[1]));
                        break;
                    case "TICKET": {
                        Vehicle vehicle = VehicleFactory.create(p[2], p[3], p[4]);
                        ParkingSlot slot = lot.findSlot(p[5]);
                        if (slot == null) {
                            throw new IllegalArgumentException("unknown slot " + p[5]);
                        }
                        lot.restoreTicket(new Ticket(p[1], vehicle, slot, LocalDateTime.parse(p[6])));
                        tickets++;
                        break;
                    }
                    case "RECEIPT":
                        lot.addReceipt(new Receipt(p[1], p[2], p[3], Long.parseLong(p[4]), p[5],
                                Double.parseDouble(p[6]), LocalDateTime.parse(p[7])));
                        receipts++;
                        break;
                    default:
                        throw new IllegalArgumentException("unknown record type " + p[0]);
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                skipped++;
                System.out.println("Skipped line " + (i + 1) + " of the data file: some fields are missing.");
            } catch (RuntimeException e) {
                skipped++;
                System.out.println("Skipped line " + (i + 1) + " of the data file: " + e.getMessage());
            }
        }

        System.out.println("Loaded " + tickets + " parked vehicle(s) and " + receipts + " payment(s)"
                + (skipped > 0 ? " (" + skipped + " line(s) skipped)" : "") + ".");
    }

    /* ---------- text format helpers ---------- */

    private static String join(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append('\t');
            sb.append(escape(fields[i]));
        }
        return sb.toString();
    }

    private static String[] split(String line) {
        String[] parts = line.split("\t", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = unescape(parts[i]);
        }
        return parts;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescape(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                if (next == 't') sb.append('\t');
                else if (next == 'n') sb.append('\n');
                else if (next == 'r') sb.append('\r');
                else sb.append(next);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}