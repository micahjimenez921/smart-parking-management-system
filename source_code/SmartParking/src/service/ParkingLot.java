package service;

import exception.InvalidTicketException;
import exception.ParkingFullException;
import exception.PaymentFailedException;
import fee.FeeStrategy;
import fee.HourlyFee;
import fee.PenaltyFee;
import gate.GateController;
import model.ParkingSlot;
import model.Receipt;
import model.Ticket;
import model.Vehicle;
import payment.PaymentMethod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParkingLot {
    private String name;
    private List<ParkingSlot> slots = new ArrayList<>();
    private Map<String, Ticket> activeTickets = new HashMap<>();
    private List<Receipt> receipts = new ArrayList<>();
    private int ticketCounter = 0;
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

    public void addSlot(ParkingSlot slot) {
        slots.add(slot);
    }

    public int getAvailableCount() {
        int count = 0;
        for (ParkingSlot s : slots) {
            if (s.isAvailable()) count++;
        }
        return count;
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
        for (ParkingSlot s : slots) {
            if (s.isAvailable() && s.getSlotType().equals(slotType)) {
                return s;
            }
        }
        return null;
    }

    public Ticket parkVehicle(Vehicle vehicle, String slotType) throws ParkingFullException {
        if (isPlateParked(vehicle.getPlateNumber())) {
            throw new IllegalStateException(
                    "Vehicle " + vehicle.getPlateNumber() + " is already parked.");
        }
        ParkingSlot slot = findAvailableSlot(slotType);
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