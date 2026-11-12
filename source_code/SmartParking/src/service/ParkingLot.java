package service;

import exception.InvalidTicketException;
import exception.ParkingFullException;
import exception.PaymentFailedException;
import fee.DailyCapFee;
import fee.FeeStrategy;
import fee.HourlyFee;
import fee.PenaltyFee;
import gate.GateController;
import model.ParkingSlot;
import model.SlotFactory;
import model.Receipt;
import model.Reservation;
import model.Ticket;
import model.Vehicle;
import payment.PaymentMethod;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParkingLot {
    public static final int MAX_SLOTS = 200;
    public static final int RESERVATION_MINUTES = 30;

    private String name;
    private List<ParkingSlot> slots = new ArrayList<>();
    private Map<String, Ticket> activeTickets = new HashMap<>();
    private List<Receipt> receipts = new ArrayList<>();
    private int ticketCounter = 0;
    private final List<Reservation> reservations = new ArrayList<>();
    private int reservationCounter = 0;
    private final EventLog events = new EventLog();
    private FeeStrategy feeStrategy = new HourlyFee();
    private double lostTicketPenalty = 100.0;
    private final GateController entryGate = new GateController("ENTRY", 15);
    private final GateController exitGate = new GateController("EXIT", 15);

    public ParkingLot(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public List<ParkingSlot> getSlots() { return slots; }

    public List<Receipt> getReceipts() { return receipts; }

    public EventLog getEvents() { return events; }

    public void addSlot(ParkingSlot slot) {
        slots.add(slot);
    }

    /** Adds a new slot of the given type, at the type's current rate, with the next free id. */
    public ParkingSlot addSlotOfType(String slotType) {
        String prefix = SlotFactory.idPrefix(slotType);
        if (slots.size() >= MAX_SLOTS) {
            throw new IllegalArgumentException("The lot cannot have more than " + MAX_SLOTS + " slots.");
        }

        int highest = 0;
        for (ParkingSlot s : slots) {
            String id = s.getSlotId();
            if (id.startsWith(prefix + "-")) {
                try {
                    highest = Math.max(highest, Integer.parseInt(id.substring(prefix.length() + 1)));
                } catch (NumberFormatException e) {
                    /* not one of the numbered slots */
                }
            }
        }
        String newId = String.format("%s-%02d", prefix, highest + 1);

        Double rate = getRates().get(slotType);
        ParkingSlot slot = (rate == null)
                ? SlotFactory.create(slotType, newId)
                : SlotFactory.create(slotType, newId, rate);
        slots.add(slot);
        return slot;
    }

    /** Removes a free slot. A slot with a vehicle in it cannot be removed. */
    public void removeSlot(String slotId) {
        ParkingSlot slot = findSlot(slotId);
        if (slot == null) {
            throw new IllegalArgumentException("Slot " + slotId + " not found.");
        }
        if (!slot.isAvailable()) {
            throw new IllegalStateException("Slot " + slotId + " has a vehicle in it and cannot be removed.");
        }
        if (isReserved(slot)) {
            throw new IllegalStateException("Slot " + slotId + " is reserved and cannot be removed.");
        }
        slots.remove(slot);
    }

    /** Used when loading saved data: the saved slots replace the default ones. */
    public void clearSlots() {
        if (!activeTickets.isEmpty()) {
            throw new IllegalStateException("Slots cannot be cleared while vehicles are parked.");
        }
        slots.clear();
        reservations.clear();
    }

    /** Slots that anyone can park in: nobody is in them and they are not held for a reservation. */
    public int getAvailableCount() {
        purgeExpired(LocalDateTime.now());
        int count = 0;
        for (ParkingSlot s : slots) {
            if (s.isAvailable() && !isReserved(s)) count++;
        }
        return count;
    }

    public int getReservedCount() {
        purgeExpired(LocalDateTime.now());
        return reservations.size();
    }

    public void addReceipt(Receipt receipt) {
        receipts.add(receipt);
    }

    public ParkingSlot findSlot(String slotId) {
        for (ParkingSlot s : slots) {
            if (s.getSlotId().equals(slotId)) return s;
        }
        return null;
    }

    public int getTicketCounter() { return ticketCounter; }

    public void setTicketCounter(int value) { ticketCounter = value; }

    public FeeStrategy getFeeStrategy() { return feeStrategy; }

    public void setFeeStrategy(FeeStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("A fee strategy is required.");
        }
        this.feeStrategy = strategy;
    }

    public double calculateFee(Ticket ticket) {
        return feeStrategy.calculateFee(ticket);
    }

    public double getLostTicketPenalty() { return lostTicketPenalty; }

    public void setLostTicketPenalty(double penalty) {
        if (Double.isNaN(penalty) || Double.isInfinite(penalty) || penalty < 0) {
            throw new IllegalArgumentException("The penalty must be zero or more.");
        }
        this.lostTicketPenalty = penalty;
    }

    public double calculateLostTicketFee(Ticket ticket) {
        return new PenaltyFee(feeStrategy, lostTicketPenalty).calculateFee(ticket);
    }

    /** The hourly rate of each slot type, in the order the types first appear. */
    public Map<String, Double> getRates() {
        Map<String, Double> rates = new LinkedHashMap<>();
        for (ParkingSlot s : slots) {
            rates.putIfAbsent(s.getSlotType(), s.getHourlyRate());
        }
        return rates;
    }

    /** Changes the hourly rate of every slot of one type. */
    public void setRate(String slotType, double rate) {
        ParkingSlot.validateRate(rate);
        boolean found = false;
        for (ParkingSlot s : slots) {
            if (s.getSlotType().equals(slotType)) {
                s.setHourlyRate(rate);
                found = true;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("There are no " + slotType + " slots.");
        }
    }

    public int getDailyCapHours() {
        return (feeStrategy instanceof DailyCapFee) ? ((DailyCapFee) feeStrategy).getCapHours() : 0;
    }

    /** 0 means no daily cap (plain hourly pricing). 1 to 24 sets the cap in hours. */
    public void setDailyCapHours(int hours) {
        if (hours == 0) {
            feeStrategy = new HourlyFee();
        } else {
            feeStrategy = new DailyCapFee(hours);
        }
    }

    /** Checks every value first, so one bad value changes nothing. */
    public void updatePricing(Map<String, Double> newRates, int capHours, double penalty) {
        Map<String, Double> current = getRates();
        for (Map.Entry<String, Double> entry : newRates.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("There are no " + entry.getKey() + " slots.");
            }
            ParkingSlot.validateRate(entry.getValue());
        }
        if (capHours < 0 || capHours > 24) {
            throw new IllegalArgumentException("The daily cap must be 0 (no cap) or 1 to 24 hours.");
        }
        if (Double.isNaN(penalty) || Double.isInfinite(penalty) || penalty < 0) {
            throw new IllegalArgumentException("The penalty must be zero or more.");
        }

        for (Map.Entry<String, Double> entry : newRates.entrySet()) {
            setRate(entry.getKey(), entry.getValue());
        }
        setDailyCapHours(capHours);
        setLostTicketPenalty(penalty);
    }

    public GateController getEntryGate() { return entryGate; }

    public GateController getExitGate() { return exitGate; }

    public GateController findGate(String gateId) {
        if (entryGate.getGateId().equalsIgnoreCase(gateId)) return entryGate;
        if (exitGate.getGateId().equalsIgnoreCase(gateId)) return exitGate;
        return null;
    }

    public void restoreTicket(Ticket ticket) {
        ticket.getSlot().occupy();
        activeTickets.put(ticket.getTicketId(), ticket);
    }

    public List<Ticket> getActiveTickets() {
        List<Ticket> list = new ArrayList<>(activeTickets.values());
        list.sort(Comparator.comparing(Ticket::getEntryTime).reversed());
        return list;
    }

    public double getTotalRevenue() {
        double total = 0;
        for (Receipt r : receipts) {
            total += r.getAmount();
        }
        return total;
    }

    private static String normalizePlate(String plateNumber) {
        return plateNumber.replaceAll("[\\s-]", "").toUpperCase();
    }

    public boolean isPlateParked(String plateNumber) {
        String wanted = normalizePlate(plateNumber);
        for (Ticket t : activeTickets.values()) {
            if (normalizePlate(t.getVehicle().getPlateNumber()).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    public ParkingSlot findAvailableSlot(String slotType) {
        purgeExpired(LocalDateTime.now());
        for (ParkingSlot s : slots) {
            if (s.isAvailable() && !isReserved(s) && s.getSlotType().equals(slotType)) {
                return s;
            }
        }
        return null;
    }

    /* ---------- reservations ---------- */

    public int getReservationCounter() { return reservationCounter; }

    public void setReservationCounter(int value) { reservationCounter = Math.max(reservationCounter, value); }

    /** The reservations that are still running, oldest first. */
    public List<Reservation> getReservations() {
        purgeExpired(LocalDateTime.now());
        return new ArrayList<>(reservations);
    }

    public boolean isReserved(ParkingSlot slot) {
        for (Reservation r : reservations) {
            if (r.getSlot() == slot) return true;
        }
        return false;
    }

    public Reservation findReservation(String id) {
        purgeExpired(LocalDateTime.now());
        for (Reservation r : reservations) {
            if (r.getId().equalsIgnoreCase(id == null ? "" : id.trim())) return r;
        }
        return null;
    }

    public Reservation findReservationBySlot(ParkingSlot slot) {
        for (Reservation r : reservations) {
            if (r.getSlot() == slot) return r;
        }
        return null;
    }

    public Reservation findReservationByPlate(String plateNumber) {
        purgeExpired(LocalDateTime.now());
        if (plateNumber == null) return null;
        String wanted = normalizePlate(plateNumber);
        for (Reservation r : reservations) {
            if (normalizePlate(r.getPlateNumber()).equals(wanted)) return r;
        }
        return null;
    }

    public Reservation reserve(String ownerId, String plateNumber, String slotType) throws ParkingFullException {
        return reserve(ownerId, plateNumber, slotType, LocalDateTime.now());
    }

    /** Holds a free slot of the chosen type for this plate for RESERVATION_MINUTES. */
    public Reservation reserve(String ownerId, String plateNumber, String slotType, LocalDateTime now)
            throws ParkingFullException {
        if (plateNumber == null || !plateNumber.trim().matches("[A-Za-z0-9 -]{2,12}")) {
            throw new IllegalArgumentException("Enter a valid plate number (2 to 12 letters, numbers, spaces or dashes).");
        }
        purgeExpired(now);
        if (isPlateParked(plateNumber)) {
            throw new IllegalStateException("Vehicle " + plateNumber.trim().toUpperCase() + " is already parked.");
        }
        if (findReservationByPlate(plateNumber) != null) {
            throw new IllegalStateException(plateNumber.trim().toUpperCase() + " already has a reservation.");
        }
        ParkingSlot slot = findAvailableSlot(slotType);
        if (slot == null) {
            throw new ParkingFullException("No free " + slotType + " slot to reserve.");
        }

        reservationCounter++;
        String id = String.format("R-%03d", reservationCounter);
        String plate = plateNumber.trim().toUpperCase();
        Reservation reservation = new Reservation(id, ownerId, plate, slot, now, now.plusMinutes(RESERVATION_MINUTES));
        reservations.add(reservation);
        events.record(EventLog.STAFF, "info", "Slot reserved",
                plate + " reserved slot " + slot.getSlotId() + " for " + RESERVATION_MINUTES + " minutes.", now);
        return reservation;
    }

    /** Puts back a saved reservation. The slot must exist, be empty and not be held already. */
    public void restoreReservation(Reservation reservation) {
        ParkingSlot slot = reservation.getSlot();
        if (!slot.isAvailable() || isReserved(slot)) {
            throw new IllegalStateException("slot " + slot.getSlotId() + " is not free");
        }
        reservations.add(reservation);
    }

    /** Ends a reservation that is still running. byStaff tells the driver who did it. */
    public Reservation cancelReservation(String id, boolean byStaff) {
        Reservation r = findReservation(id);
        if (r == null) {
            throw new IllegalArgumentException("Reservation " + (id == null ? "" : id.trim()) + " not found.");
        }
        reservations.remove(r);
        if (byStaff) {
            events.record(r.getOwnerId(), "warn", "Reservation cancelled",
                    "Staff cancelled your reservation " + r.getId() + " for " + r.getPlateNumber()
                            + ". Slot " + r.getSlot().getSlotId() + " was released.");
        } else {
            events.record(EventLog.STAFF, "info", "Reservation cancelled",
                    r.getPlateNumber() + " cancelled the reservation for slot " + r.getSlot().getSlotId() + ".");
        }
        return r;
    }

    /** Removes reservations whose time ran out and tells the people involved. Safe to call at any time. */
    public List<Reservation> purgeExpired(LocalDateTime now) {
        List<Reservation> gone = new ArrayList<>();
        for (Reservation r : reservations) {
            if (r.isExpired(now)) gone.add(r);
        }
        for (Reservation r : gone) {
            reservations.remove(r);
            events.record(r.getOwnerId(), "warn", "Reservation expired",
                    "Your reservation " + r.getId() + " for " + r.getPlateNumber() + " ran out. Slot "
                            + r.getSlot().getSlotId() + " was released. You can reserve again.", r.getExpiresAt());
            events.record(EventLog.STAFF, "info", "Reservation expired",
                    "The reservation for " + r.getPlateNumber() + " ran out. Slot " + r.getSlot().getSlotId()
                            + " is free again.", r.getExpiresAt());
        }
        return gone;
    }

    public Ticket parkVehicle(Vehicle vehicle, String slotType) throws ParkingFullException {
        if (isPlateParked(vehicle.getPlateNumber())) {
            throw new IllegalStateException(
                    "Vehicle " + vehicle.getPlateNumber() + " is already parked.");
        }
        purgeExpired(LocalDateTime.now());

        // a vehicle with a reservation goes to the slot that was held for it
        Reservation held = findReservationByPlate(vehicle.getPlateNumber());
        ParkingSlot slot;
        if (held != null) {
            slot = held.getSlot();
            reservations.remove(held);
            events.record(held.getOwnerId(), "info", "Welcome",
                    vehicle.getPlateNumber() + " arrived and is parked in the slot you reserved (" + slot.getSlotId() + ").");
        } else {
            slot = findAvailableSlot(slotType);
        }
        if (slot == null) {
            throw new ParkingFullException("No available " + slotType + " slot.");
        }
        slot.occupy();
        ticketCounter++;
        String id = String.format("T-%03d", ticketCounter);
        Ticket ticket = new Ticket(id, vehicle, slot);
        activeTickets.put(id, ticket);
        entryGate.open(vehicle.getPlateNumber());
        return ticket;
    }

    public Ticket findTicket(String ticketId) throws InvalidTicketException {
        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            throw new InvalidTicketException("Ticket " + ticketId + " not found.");
        }
        return ticket;
    }

    public Ticket findTicketByPlate(String plateNumber) throws InvalidTicketException {
        String wanted = normalizePlate(plateNumber);
        for (Ticket t : activeTickets.values()) {
            if (normalizePlate(t.getVehicle().getPlateNumber()).equals(wanted)) {
                return t;
            }
        }
        throw new InvalidTicketException("No parked vehicle with plate " + plateNumber.trim() + ".");
    }

    public Ticket exitVehicle(String ticketId) throws InvalidTicketException {
        Ticket ticket = findTicket(ticketId);
        ticket.getSlot().vacate();
        ticket.close();
        activeTickets.remove(ticketId);
        return ticket;
    }

    public Receipt checkout(String ticketId, PaymentMethod method)
            throws InvalidTicketException, PaymentFailedException {
        return checkout(ticketId, method, false);
    }

    public Receipt checkout(String ticketId, PaymentMethod method, boolean lostTicket)
            throws InvalidTicketException, PaymentFailedException {
        Ticket ticket = findTicket(ticketId);
        double fee = lostTicket ? calculateLostTicketFee(ticket) : calculateFee(ticket);
        long hours = ticket.getHoursParked();

        method.pay(fee);

        ticket.markPaid();
        exitVehicle(ticketId);

        Receipt receipt = new Receipt(ticketId, ticket.getVehicle().getPlateNumber(),
                ticket.getSlot().getSlotId(), hours, method.getMethodName(), fee);
        addReceipt(receipt);
        exitGate.open(receipt.getPlateNumber());
        return receipt;
    }

    public Ticket findTicketBySlot(ParkingSlot slot) {
        for (Ticket t : activeTickets.values()) {
            if (t.getSlot() == slot) return t;
        }
        return null;
    }
}