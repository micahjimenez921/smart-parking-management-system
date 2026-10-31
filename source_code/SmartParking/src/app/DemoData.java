package app;

import exception.ParkingFullException;
import model.*;
import service.ParkingLot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

public class DemoData {
    // Set to false to start with an empty payment history.
    private static final boolean SEED_HISTORY = true;

    public static ParkingLot create() {
        ParkingLot lot = new ParkingLot("Main Lot");
        for (int i = 1; i <= 4; i++) lot.addSlot(new RegularSlot(String.format("R-%02d", i)));
        for (int i = 1; i <= 2; i++) lot.addSlot(new HandicappedSlot(String.format("H-%02d", i)));
        for (int i = 1; i <= 2; i++) lot.addSlot(new VIPSlot(String.format("V-%02d", i)));

        try {
            lot.parkVehicle(new Car("ABC123", "Ana"), "REGULAR");
            lot.parkVehicle(new Motorcycle("MOTO456", "Ben"), "REGULAR");
            lot.parkVehicle(new Truck("TRK789", "Carla"), "VIP");
        } catch (ParkingFullException e) {
            System.out.println(e.getMessage());
        }

        if (SEED_HISTORY) {
            addSampleHistory(lot);
        }
        return lot;
    }

    private static void addSampleHistory(ParkingLot lot) {
        Random random = new Random(42);
        List<ParkingSlot> slots = lot.getSlots();
        String[] methods = {"CASH", "CASH", "CARD", "CARD", "ONLINE"};
        int[] hoursPool = {1, 1, 1, 2, 2, 2, 3, 3, 4, 5, 6};

        LocalDateTime now = LocalDateTime.now();
        int counter = 0;

        for (int daysAgo = 29; daysAgo >= 0; daysAgo--) {
            LocalDate day = LocalDate.now().minusDays(daysAgo);
            int count = 3 + random.nextInt(5);
            if (day.getDayOfWeek() == DayOfWeek.FRIDAY || day.getDayOfWeek() == DayOfWeek.SATURDAY) {
                count += 2;
            }

            for (int i = 0; i < count; i++) {
                LocalDateTime time = day.atTime(7 + random.nextInt(13), random.nextInt(60));
                if (time.isAfter(now)) continue;

                ParkingSlot slot = slots.get(random.nextInt(slots.size()));
                int hours = hoursPool[random.nextInt(hoursPool.length)];
                String plate = "" + (char) ('A' + random.nextInt(26)) + (char) ('A' + random.nextInt(26))
                        + (char) ('A' + random.nextInt(26)) + " " + (100 + random.nextInt(900));
                counter++;

                lot.addReceipt(new Receipt(String.format("H-%04d", counter), plate, slot.getSlotId(),
                        hours, methods[random.nextInt(methods.length)],
                        hours * slot.getHourlyRate(), time));
            }
        }
    }
}