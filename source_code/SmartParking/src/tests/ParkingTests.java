package tests;

import app.DemoData;
import exception.InvalidTicketException;
import exception.ParkingFullException;
import exception.PaymentFailedException;
import fee.DailyCapFee;
import fee.FeeStrategy;
import fee.HourlyFee;
import fee.PenaltyFee;
import gate.GateController;
import gate.GateState;
import model.*;
import payment.*;
import service.ParkingLot;
import storage.FileParkingRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain Java tests (no libraries). Run this class and read the PASS / FAIL lines.
 */
public class ParkingTests {
    private static int passed = 0;
    private static int failed = 0;

    private interface Action {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        runSection("Fee calculation", ParkingTests::feeTests);
        runSection("Fee strategies", ParkingTests::feeStrategyTests);
        runSection("Lost ticket", ParkingTests::lostTicketTests);
        runSection("Gates", ParkingTests::gateTests);
        runSection("Settings", ParkingTests::settingsTests);
        runSection("Parking slots (encapsulation)", ParkingTests::slotTests);
        runSection("Polymorphism", ParkingTests::polymorphismTests);
        runSection("ParkingLot rules", ParkingTests::parkingLotTests);
        runSection("Payments", ParkingTests::paymentTests);
        runSection("Factories", ParkingTests::factoryTests);
        runSection("Saving and loading", ParkingTests::persistenceTests);

        System.out.println();
        System.out.println("==============================");
        System.out.println(passed + " passed, " + failed + " failed");
        System.out.println("==============================");
        if (failed > 0) System.exit(1);
    }

    private static void runSection(String title, Action tests) {
        System.out.println();
        System.out.println("--- " + title);
        try {
            tests.run();
        } catch (Throwable error) {
            failed++;
            System.out.println("FAIL  this group stopped early because of an unexpected "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    /* ---------- fee calculation ---------- */

    private static void feeTests() throws Exception {
        ParkingSlot vip = new VIPSlot("V-99");
        ParkingSlot regular = new RegularSlot("R-99");
        ParkingSlot handicapped = new HandicappedSlot("H-99");
        Vehicle car = new Car("TEST 1", "Tester");
        FeeStrategy hourly = new HourlyFee();

        checkEquals("2 hours 10 minutes in a VIP slot rounds up to 3 hours = 150",
                150.0, hourly.calculateFee(ticketAged(car, vip, 130)));
        checkEquals("exactly 60 minutes in a Regular slot = 1 hour = 20",
                20.0, hourly.calculateFee(ticketAged(car, regular, 60)));
        checkEquals("61 minutes in a Regular slot = 2 hours = 40",
                40.0, hourly.calculateFee(ticketAged(car, regular, 61)));
        checkEquals("5 minutes in a Handicapped slot is charged the 1 hour minimum = 15",
                15.0, hourly.calculateFee(ticketAged(car, handicapped, 5)));
        checkEquals("hours charged for 130 minutes", 3L, ticketAged(car, vip, 130).getHoursParked());
    }

    private static void feeStrategyTests() throws Exception {
        ParkingSlot regular = new RegularSlot("R-99");
        Vehicle car = new Car("TEST 2", "Tester");
        FeeStrategy capped = new DailyCapFee(8);

        checkEquals("daily cap: 3 hours is below the cap = 60",
                60.0, capped.calculateFee(ticketAged(car, regular, 180)));
        checkEquals("daily cap: 8 hours = exactly the cap = 160",
                160.0, capped.calculateFee(ticketAged(car, regular, 480)));
        checkEquals("daily cap: 10 hours is held at the cap = 160 (hourly would be 200)",
                160.0, capped.calculateFee(ticketAged(car, regular, 600)));
        checkEquals("daily cap: a full 24 hours costs one daily maximum = 160",
                160.0, capped.calculateFee(ticketAged(car, regular, 1440)));
        checkEquals("daily cap: 25 hours = one daily maximum + 1 hour = 180",
                180.0, capped.calculateFee(ticketAged(car, regular, 1500)));
        checkEquals("daily cap: 2 days and 3 hours = 2 maximums + 60 = 380",
                380.0, capped.calculateFee(ticketAged(car, regular, 2 * 1440 + 180)));

        checkThrows("a daily cap of 0 hours is rejected", IllegalArgumentException.class,
                () -> new DailyCapFee(0));
        checkThrows("a daily cap of 25 hours is rejected", IllegalArgumentException.class,
                () -> new DailyCapFee(25));

        ParkingLot lot = DemoData.createEmptyLot();
        Ticket tenHours = ticketAged(car, regular, 600);
        checkEquals("a lot starts with the plain hourly rule", 200.0, lot.calculateFee(tenHours));
        lot.setFeeStrategy(capped);
        checkEquals("changing the lot's strategy changes its fees", 160.0, lot.calculateFee(tenHours));
        checkThrows("a lot refuses a missing strategy", IllegalArgumentException.class,
                () -> lot.setFeeStrategy(null));

        lot.restoreTicket(new Ticket("T-050", car, lot.findSlot("R-01"), LocalDateTime.now().minusMinutes(600)));
        Receipt receipt = lot.checkout("T-050", new CashPayment(1000));
        checkEquals("checkout charges what the lot's strategy says", 160.0, receipt.getAmount());
    }

        private static void lostTicketTests() throws Exception {
        ParkingSlot regular = new RegularSlot("R-99");
        Vehicle car = new Car("TEST 3", "Tester");

        FeeStrategy lostHourly = new PenaltyFee(new HourlyFee(), 100);
        checkEquals("penalty fee: 3 hours (60) plus the 100 penalty = 160",
                160.0, lostHourly.calculateFee(ticketAged(car, regular, 180)));

        FeeStrategy lostCapped = new PenaltyFee(new DailyCapFee(8), 100);
        checkEquals("penalty fee wraps any rule: 10 hours capped at 160 plus 100 = 260",
                260.0, lostCapped.calculateFee(ticketAged(car, regular, 600)));

        checkThrows("a penalty fee needs a base rule", IllegalArgumentException.class,
                () -> new PenaltyFee(null, 100));
        checkThrows("a negative penalty is rejected", IllegalArgumentException.class,
                () -> new PenaltyFee(new HourlyFee(), -1));

        ParkingLot lot = DemoData.createEmptyLot();
        Ticket ticket = lot.parkVehicle(new Car("LOST 111", "Lola"), "REGULAR");

        checkEquals("a vehicle can be found by plate, ignoring capitals and spaces around it",
                ticket.getTicketId(), lot.findTicketByPlate("  lost 111 ").getTicketId());
        checkEquals("a plate typed without the space still finds the vehicle",
                ticket.getTicketId(), lot.findTicketByPlate("LOST111").getTicketId());
        checkEquals("a plate typed with a dash still finds the vehicle",
                ticket.getTicketId(), lot.findTicketByPlate("lost-111").getTicketId());
        checkThrows("an unknown plate is refused", InvalidTicketException.class,
                () -> lot.findTicketByPlate("NOPE 999"));

        checkEquals("default lost ticket penalty", 100.0, lot.getLostTicketPenalty());
        checkEquals("lost ticket fee = normal fee + penalty", 120.0, lot.calculateLostTicketFee(ticket));
        checkThrows("a negative lot penalty is rejected", IllegalArgumentException.class,
                () -> lot.setLostTicketPenalty(-5));

        checkThrows("paying the normal fee is not enough for a lost ticket", PaymentFailedException.class,
                () -> lot.checkout(ticket.getTicketId(), new CashPayment(20), true));
        check("a failed lost ticket payment keeps the vehicle parked", !ticket.getSlot().isAvailable());

        PaymentMethod cash = new CashPayment(500);
        Receipt receipt = lot.checkout(ticket.getTicketId(), cash, true);
        checkEquals("lost ticket receipt includes the penalty", 120.0, receipt.getAmount());
        checkEquals("change after a lost ticket payment", 380.0, cash.getChange(receipt.getAmount()));
        check("the slot is free after a lost ticket payment", ticket.getSlot().isAvailable());
    }

        private static void gateTests() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 8, 0, 0);

        GateController gate = new GateController("TEST", 15);
        checkEquals("a new gate is closed", GateState.CLOSED, gate.getState(start));

        gate.open("ABC 123", start);
        checkEquals("opening the gate for a vehicle", GateState.OPEN, gate.getState(start));
        checkEquals("the gate remembers who it opened for", "ABC 123", gate.getOpenedFor(start));
        checkEquals("seconds left right after opening", 15L, gate.getSecondsLeft(start));
        checkEquals("seconds left after 6 seconds", 9L, gate.getSecondsLeft(start.plusSeconds(6)));
        checkEquals("still open just before the time runs out", GateState.OPEN, gate.getState(start.plusSeconds(14)));
        checkEquals("the gate closes by itself after 15 seconds", GateState.CLOSED, gate.getState(start.plusSeconds(15)));
        check("a closed gate has no vehicle", gate.getOpenedFor(start.plusSeconds(20)) == null);

        gate.open("XYZ 987", start);
        gate.vehiclePassed();
        checkEquals("the sensor reporting the vehicle closes the gate", GateState.CLOSED, gate.getState(start));

        checkThrows("a gate must have an id", IllegalArgumentException.class, () -> new GateController(" ", 15));
        checkThrows("a gate must close within 1 to 3600 seconds", IllegalArgumentException.class,
                () -> new GateController("G", 0));
        checkThrows("a gate opens for a specific vehicle", IllegalArgumentException.class, () -> gate.open(" "));

        ParkingLot lot = DemoData.createEmptyLot();
        checkEquals("the lot's gates start closed", GateState.CLOSED, lot.getEntryGate().getState());
        checkEquals("the lot has an exit gate too", GateState.CLOSED, lot.getExitGate().getState());

        Ticket ticket = lot.parkVehicle(new Car("GATE 111", "Gina"), "REGULAR");
        checkEquals("parking a vehicle opens the entry gate for it", "GATE 111", lot.getEntryGate().getOpenedFor());
        checkEquals("parking does not open the exit gate", GateState.CLOSED, lot.getExitGate().getState());

        checkThrows("a failed payment is refused", PaymentFailedException.class,
                () -> lot.checkout(ticket.getTicketId(), new CashPayment(1)));
        checkEquals("a failed payment keeps the exit gate closed", GateState.CLOSED, lot.getExitGate().getState());

        lot.checkout(ticket.getTicketId(), new CashPayment(500));
        checkEquals("a successful payment opens the exit gate for that vehicle", "GATE 111", lot.getExitGate().getOpenedFor());

        ParkingLot second = DemoData.createEmptyLot();
        Ticket lostOne = second.parkVehicle(new Car("GATE 222", "Gus"), "REGULAR");
        second.getEntryGate().close();
        second.checkout(lostOne.getTicketId(), new CashPayment(500), true);
        checkEquals("paying for a lost ticket opens the exit gate too", "GATE 222", second.getExitGate().getOpenedFor());

        ParkingLot full = DemoData.createEmptyLot();
        full.parkVehicle(new Car("FULL 1", "a"), "VIP");
        full.parkVehicle(new Car("FULL 2", "b"), "VIP");
        full.getEntryGate().close();
        checkThrows("parking in a full slot type is refused", ParkingFullException.class,
                () -> full.parkVehicle(new Car("FULL 3", "c"), "VIP"));
        checkEquals("a refused vehicle does not open the entry gate", GateState.CLOSED, full.getEntryGate().getState());

        ParkingLot restored = DemoData.createEmptyLot();
        restored.restoreTicket(new Ticket("T-070", new Car("REST 1", "r"), restored.findSlot("R-01")));
        checkEquals("restoring saved data does not open the gate", GateState.CLOSED, restored.getEntryGate().getState());

        check("gates can be found by id, ignoring capitals", lot.findGate("exit") == lot.getExitGate());
        check("an unknown gate id is not found", lot.findGate("side") == null);
    }

        private static void settingsTests() throws Exception {
        ParkingSlot regular = new RegularSlot("R-99");
        checkEquals("a Regular slot starts at its default rate", 20.0, regular.getHourlyRate());
        checkEquals("each slot type knows its own default rate", 15.0, new HandicappedSlot("H-99").getDefaultRate());
        checkEquals("the VIP default rate", 50.0, new VIPSlot("V-99").getDefaultRate());
        checkEquals("a slot can be created with its own rate", 35.0, new RegularSlot("R-98", 35).getHourlyRate());

        regular.setHourlyRate(25);
        checkEquals("a slot's rate can be changed", 25.0, regular.getHourlyRate());
        checkEquals("changing a rate does not change the default", 20.0, regular.getDefaultRate());
        checkThrows("a rate of 0 is rejected", IllegalArgumentException.class, () -> regular.setHourlyRate(0));
        checkThrows("a negative rate is rejected", IllegalArgumentException.class, () -> regular.setHourlyRate(-5));
        checkThrows("a rate above the maximum is rejected", IllegalArgumentException.class,
                () -> regular.setHourlyRate(10_001));
        checkThrows("a rate that is not a number is rejected", IllegalArgumentException.class,
                () -> regular.setHourlyRate(Double.NaN));
        checkEquals("a rejected rate leaves the old rate in place", 25.0, regular.getHourlyRate());

        ParkingLot lot = DemoData.createEmptyLot();
        checkEquals("the lot lists three slot types", 3, lot.getRates().size());
        checkEquals("the Regular rate in a new lot", 20.0, lot.getRates().get("REGULAR"));

        lot.setRate("REGULAR", 30);
        checkEquals("setting a type's rate changes its first slot", 30.0, lot.findSlot("R-01").getHourlyRate());
        checkEquals("setting a type's rate changes its last slot", 30.0, lot.findSlot("R-04").getHourlyRate());
        checkEquals("other slot types are not affected", 15.0, lot.findSlot("H-01").getHourlyRate());
        checkThrows("a slot type that does not exist is rejected", IllegalArgumentException.class,
                () -> lot.setRate("BOAT", 10));

        checkEquals("a new lot has no daily cap", 0, lot.getDailyCapHours());
        lot.setDailyCapHours(8);
        checkEquals("the daily cap can be set", 8, lot.getDailyCapHours());
        check("the fee rule mentions the cap", lot.getFeeStrategy().getDescription().contains("8 hours"));
        lot.setDailyCapHours(0);
        checkEquals("a cap of 0 turns the cap off", 0, lot.getDailyCapHours());
        checkThrows("a cap above 24 hours is rejected", IllegalArgumentException.class, () -> lot.setDailyCapHours(25));
        checkThrows("a negative cap is rejected", IllegalArgumentException.class, () -> lot.setDailyCapHours(-1));

        Ticket twoHours = ticketAged(new Car("SET 1", "Sam"), lot.findSlot("R-01"), 120);
        checkEquals("fees use the new rate: 2 hours x 30", 60.0, lot.calculateFee(twoHours));

        Map<String, Double> bad = new LinkedHashMap<>();
        bad.put("REGULAR", 40.0);
        bad.put("VIP", -5.0);
        checkThrows("one bad rate rejects the whole update", IllegalArgumentException.class,
                () -> lot.updatePricing(bad, 6, 150));
        checkEquals("a rejected update changes no rate", 30.0, lot.findSlot("R-01").getHourlyRate());
        checkEquals("a rejected update changes no cap", 0, lot.getDailyCapHours());
        checkEquals("a rejected update changes no penalty", 100.0, lot.getLostTicketPenalty());

        Map<String, Double> good = new LinkedHashMap<>();
        good.put("REGULAR", 40.0);
        checkThrows("a bad cap rejects the whole update", IllegalArgumentException.class,
                () -> lot.updatePricing(good, 30, 150));
        checkThrows("a bad penalty rejects the whole update", IllegalArgumentException.class,
                () -> lot.updatePricing(good, 6, -1));
        Map<String, Double> unknown = new LinkedHashMap<>();
        unknown.put("BOAT", 10.0);
        checkThrows("an unknown slot type rejects the whole update", IllegalArgumentException.class,
                () -> lot.updatePricing(unknown, 6, 150));
        checkEquals("none of those rejected updates changed the rate", 30.0, lot.findSlot("R-01").getHourlyRate());

        Map<String, Double> update = new LinkedHashMap<>();
        update.put("REGULAR", 40.0);
        update.put("VIP", 60.0);
        lot.updatePricing(update, 6, 150);
        checkEquals("a good update changes the Regular rate", 40.0, lot.getRates().get("REGULAR"));
        checkEquals("a good update changes the VIP rate", 60.0, lot.getRates().get("VIP"));
        checkEquals("a type left out of the update keeps its rate", 15.0, lot.getRates().get("HANDICAPPED"));
        checkEquals("a good update changes the cap", 6, lot.getDailyCapHours());
        checkEquals("a good update changes the penalty", 150.0, lot.getLostTicketPenalty());

        Ticket later = ticketAged(new Car("SET 2", "Sue"), lot.findSlot("R-01"), 120);
        checkEquals("the lost ticket fee uses the new rate and penalty: 80 + 150", 230.0,
                lot.calculateLostTicketFee(later));

        Path folder = Files.createTempDirectory("smartpark-settings");
        FileParkingRepository repository = new FileParkingRepository(folder.resolve("data.txt"));
        repository.save(lot);
        ParkingLot restored = DemoData.createEmptyLot();
        repository.load(restored);
        checkEquals("saved Regular rate is restored", 40.0, restored.getRates().get("REGULAR"));
        checkEquals("saved VIP rate is restored", 60.0, restored.getRates().get("VIP"));
        checkEquals("an unchanged rate is restored", 15.0, restored.getRates().get("HANDICAPPED"));
        checkEquals("the saved cap is restored", 6, restored.getDailyCapHours());
        checkEquals("the saved penalty is restored", 150.0, restored.getLostTicketPenalty());

        Path oldFile = folder.resolve("old.txt");
        Files.writeString(oldFile, "SMARTPARK-DATA 1\nCOUNTER\t3\n"
                + "TICKET\tT-001\tCAR\tOLD 1\tOld Timer\tR-01\t2026-01-01T10:00\n");
        ParkingLot legacy = DemoData.createEmptyLot();
        new FileParkingRepository(oldFile).load(legacy);
        checkEquals("a file saved before settings existed still loads its tickets", 1, legacy.getActiveTickets().size());
        checkEquals("...and keeps the default rate", 20.0, legacy.getRates().get("REGULAR"));
        checkEquals("...and the default penalty", 100.0, legacy.getLostTicketPenalty());

        Path oddFile = folder.resolve("odd.txt");
        Files.writeString(oddFile, "SMARTPARK-DATA 1\nRATE\tBOAT\t10\nRATE\tVIP\t75\n");
        ParkingLot odd = DemoData.createEmptyLot();
        new FileParkingRepository(oddFile).load(odd);
        checkEquals("a saved rate for an unknown type is skipped", 75.0, odd.getRates().get("VIP"));
    }
    private static Ticket ticketAged(Vehicle vehicle, ParkingSlot slot, int minutes) {
        return new Ticket("TEST", vehicle, slot, LocalDateTime.now().minusMinutes(minutes));
    }

    /* ---------- slots ---------- */

    private static void slotTests() throws Exception {
        ParkingSlot slot = new RegularSlot("R-99");
        check("a new slot is available", slot.isAvailable());

        slot.occupy();
        check("occupy() makes the slot unavailable", !slot.isAvailable());

        checkThrows("occupying an occupied slot is refused", IllegalStateException.class, slot::occupy);

        slot.vacate();
        check("vacate() frees the slot again", slot.isAvailable());
    }

    /* ---------- polymorphism ---------- */

    private static void polymorphismTests() throws Exception {
        List<ParkingSlot> slots = List.of(new RegularSlot("R"), new HandicappedSlot("H"), new VIPSlot("V"));
        checkEquals("Regular slot rate", 20.0, slots.get(0).getHourlyRate());
        checkEquals("Handicapped slot rate", 15.0, slots.get(1).getHourlyRate());
        checkEquals("VIP slot rate", 50.0, slots.get(2).getHourlyRate());

        List<Vehicle> vehicles = List.of(new Car("A", "x"), new Motorcycle("B", "x"), new Truck("C", "x"));
        checkEquals("Car type", "CAR", vehicles.get(0).getVehicleType());
        checkEquals("Motorcycle type", "MOTORCYCLE", vehicles.get(1).getVehicleType());
        checkEquals("Truck type", "TRUCK", vehicles.get(2).getVehicleType());
    }

    /* ---------- parking lot ---------- */

    private static void parkingLotTests() throws Exception {
        ParkingLot lot = DemoData.createEmptyLot();

        Ticket first = lot.parkVehicle(new Car("AAA 111", "Ana"), "REGULAR");
        checkEquals("first ticket id", "T-001", first.getTicketId());
        checkEquals("first Regular slot is assigned", "R-01", first.getSlot().getSlotId());
        checkEquals("assigned slot is now occupied", false, first.getSlot().isAvailable());

        lot.parkVehicle(new Car("AAA 112", "Ben"), "VIP");
        lot.parkVehicle(new Car("AAA 113", "Cy"), "VIP");
        checkThrows("parking when the slot type is full is refused", ParkingFullException.class,
                () -> lot.parkVehicle(new Car("AAA 114", "Di"), "VIP"));

        checkThrows("the same plate cannot be parked twice", IllegalStateException.class,
                () -> lot.parkVehicle(new Car("aaa 111", "Ana again"), "REGULAR"));

        checkThrows("the same plate typed without the space is still a duplicate", IllegalStateException.class,
                () -> lot.parkVehicle(new Car("AAA111", "Ana again"), "REGULAR"));
        checkThrows("looking up an unknown ticket is refused", InvalidTicketException.class,
                () -> lot.findTicket("T-999"));

        checkEquals("available slot count", 5, lot.getAvailableCount());
    }

    /* ---------- payments ---------- */

    private static void paymentTests() throws Exception {
        ParkingLot lot = DemoData.createEmptyLot();
        Ticket ticket = lot.parkVehicle(new Car("PAY 100", "Payer"), "REGULAR");
        String id = ticket.getTicketId();

        checkThrows("cash that is too low fails", PaymentFailedException.class,
                () -> lot.checkout(id, new CashPayment(5)));
        check("after a failed payment the ticket is still open", lot.findTicket(id) != null);
        check("after a failed payment the slot is still occupied", !ticket.getSlot().isAvailable());
        checkEquals("after a failed payment no revenue is recorded", 0.0, lot.getTotalRevenue());

        PaymentMethod cash = new CashPayment(50);
        Receipt receipt = lot.checkout(id, cash);
        checkEquals("receipt amount", 20.0, receipt.getAmount());
        checkEquals("change is calculated by CashPayment", 30.0, cash.getChange(receipt.getAmount()));
        check("after payment the slot is free again", ticket.getSlot().isAvailable());
        checkEquals("revenue is recorded", 20.0, lot.getTotalRevenue());
        checkThrows("a paid ticket cannot be used again", InvalidTicketException.class,
                () -> lot.findTicket(id));

        checkThrows("card with 15 digits fails", PaymentFailedException.class,
                () -> new CardPayment("123456789012345").pay(10));
        new CardPayment("1234567890123456").pay(10);
        pass("card with 16 digits succeeds");

        checkThrows("online payment with 10 digits fails", PaymentFailedException.class,
                () -> new OnlinePayment("0917123456").pay(10));
        new OnlinePayment("09171234567").pay(10);
        pass("online payment with 11 digits succeeds");

        checkEquals("card and online give no change", 0.0, new CardPayment("1").getChange(10));
    }

    /* ---------- factories ---------- */

    private static void factoryTests() throws Exception {
        check("VehicleFactory builds a Car", VehicleFactory.create("CAR", "A", "x") instanceof Car);
        check("VehicleFactory builds a Motorcycle", VehicleFactory.create("MOTORCYCLE", "A", "x") instanceof Motorcycle);
        check("VehicleFactory builds a Truck", VehicleFactory.create("TRUCK", "A", "x") instanceof Truck);
        checkThrows("VehicleFactory rejects an unknown type", IllegalArgumentException.class,
                () -> VehicleFactory.create("BOAT", "A", "x"));

        check("PaymentFactory builds cash", PaymentFactory.create("CASH", "100") instanceof CashPayment);
        check("PaymentFactory builds card", PaymentFactory.create("CARD", "1234 5678-9012 3456") instanceof CardPayment);
        PaymentFactory.create("CARD", "1234 5678-9012 3456").pay(1);
        pass("card numbers typed with spaces and dashes are accepted");
        check("PaymentFactory builds online", PaymentFactory.create("ONLINE", "09171234567") instanceof OnlinePayment);
        checkThrows("PaymentFactory rejects an unknown method", IllegalArgumentException.class,
                () -> PaymentFactory.create("CHEQUE", "1"));
        checkThrows("cash amount that is not a number is rejected", IllegalArgumentException.class,
                () -> PaymentFactory.create("CASH", "abc"));
        checkThrows("cash amount NaN is rejected", IllegalArgumentException.class,
                () -> PaymentFactory.create("CASH", "NaN"));
        checkThrows("negative cash amount is rejected", IllegalArgumentException.class,
                () -> PaymentFactory.create("CASH", "-5"));
    }

    /* ---------- saving and loading ---------- */

    private static void persistenceTests() throws Exception {
        Path folder = Files.createTempDirectory("smartpark-test");
        Path file = folder.resolve("data.txt");
        FileParkingRepository repository = new FileParkingRepository(file);
        check("no saved data before the first save", !repository.hasSavedData());

        String trickyOwner = "Dana \"DJ\" O'Neil \\ back\tslash";
        ParkingLot lot = DemoData.createEmptyLot();
        Ticket paid = lot.parkVehicle(new Car("AAA 111", "Ana"), "REGULAR");
        lot.parkVehicle(new Truck("BBB 222", trickyOwner), "VIP");
        lot.checkout(paid.getTicketId(), new CashPayment(500));
        repository.save(lot);
        check("saved data exists after save", repository.hasSavedData());

        ParkingLot restored = DemoData.createEmptyLot();
        repository.load(restored);

        checkEquals("one vehicle is still parked after loading", 1, restored.getActiveTickets().size());
        Ticket back = restored.getActiveTickets().get(0);
        checkEquals("plate is restored", "BBB 222", back.getVehicle().getPlateNumber());
        checkEquals("owner with quotes, backslash and tab is restored exactly", trickyOwner, back.getVehicle().getOwnerName());
        checkEquals("vehicle type is restored", "TRUCK", back.getVehicle().getVehicleType());
        checkEquals("the slot is occupied again", false, back.getSlot().isAvailable());
        checkEquals("payments are restored", 1, restored.getReceipts().size());
        checkEquals("revenue is restored", lot.getTotalRevenue(), restored.getTotalRevenue());
        checkEquals("entry time is restored", lot.getActiveTickets().get(0).getEntryTime(), back.getEntryTime());

        Ticket next = restored.parkVehicle(new Car("CCC 333", "Cy"), "REGULAR");
        checkEquals("ticket numbering continues after loading", "T-003", next.getTicketId());

        // damaged lines must be skipped without losing the good ones
        Files.writeString(file,
                "GARBAGE\tx\n"
                        + "TICKET\tT-900\tBOAT\tZZZ\tNobody\tR-02\t2026-01-01T10:00\n"
                        + "TICKET\tT-901\tCAR\tDUP 1\tSomeone\tV-01\t2026-01-01T10:00\n"
                        + "RECEIPT\tbroken\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        ParkingLot damaged = DemoData.createEmptyLot();
        repository.load(damaged);
        checkEquals("damaged lines are skipped and good tickets still load", 1, damaged.getActiveTickets().size());
        checkEquals("damaged lines are skipped and good payments still load", 1, damaged.getReceipts().size());

        Path wrong = folder.resolve("wrong.txt");
        Files.writeString(wrong, "hello world\n");
        checkThrows("a file that is not a SmartPark file is rejected", IOException.class,
                () -> new FileParkingRepository(wrong).load(DemoData.createEmptyLot()));
    }

    /* ---------- tiny test helpers ---------- */

    private static void pass(String name) {
        passed++;
        System.out.println("PASS  " + name);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            pass(name);
        } else {
            failed++;
            System.out.println("FAIL  " + name);
        }
    }

    private static void checkEquals(String name, Object expected, Object actual) {
        if (expected.equals(actual)) {
            pass(name);
        } else {
            failed++;
            System.out.println("FAIL  " + name + "   (expected " + expected + ", got " + actual + ")");
        }
    }

    private static void checkThrows(String name, Class<? extends Throwable> type, Action action) {
        try {
            action.run();
            failed++;
            System.out.println("FAIL  " + name + "   (nothing was thrown)");
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) {
                pass(name);
            } else {
                failed++;
                System.out.println("FAIL  " + name + "   (got " + thrown.getClass().getSimpleName() + ")");
            }
        }
    }
}