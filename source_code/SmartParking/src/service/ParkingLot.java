package service;

import exception.InvalidTicketException;
import exception.ParkingFullException;
import exception.PaymentFailedException;
import model.ParkingSlot;
import model.Receipt;
import model.Ticket;
import model.Vehicle;
import payment.PaymentMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParkingLot {
    private String name;
    private List<ParkingSlot> slots = new ArrayList<>();
    private Map<String, Ticket> activeTickets = new HashMap<>();
    private List<Receipt> receipts = new ArrayList<>();
    private int ticketCounter = 0;

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

    public double getTotalRevenue() {
        double total = 0;
        for (Receipt r : receipts) {
            total += r.getAmount();
        }
        return total;
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
        ParkingSlot slot = findAvailableSlot(slotType);
        if (slot == null) {
            throw new ParkingFullException("No available " + slotType + " slot.");
        }
        slot.occupy();
        ticketCounter++;
        String id = String.format("T-%03d", ticketCounter);
        Ticket ticket = new Ticket(id, vehicle, slot);
        activeTickets.put(id, ticket);
        return ticket;
    }

    public Ticket findTicket(String ticketId) throws InvalidTicketException {
        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            throw new InvalidTicketException("Ticket " + ticketId + " not found.");
        }
        return ticket;
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
        Ticket ticket = findTicket(ticketId);
        double fee = ticket.calculateFee();
        long hours = ticket.getHoursParked();

        method.pay(fee);

        ticket.markPaid();
        exitVehicle(ticketId);

        Receipt receipt = new Receipt(ticketId, ticket.getVehicle().getPlateNumber(),
                ticket.getSlot().getSlotId(), hours, method.getMethodName(), fee);
        receipts.add(receipt);
        return receipt;
    }
}