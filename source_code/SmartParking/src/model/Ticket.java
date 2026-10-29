package model;

import java.time.Duration;
import java.time.LocalDateTime;

public class Ticket {
    private String ticketId;
    private Vehicle vehicle;
    private ParkingSlot slot;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private TicketStatus status;

    public Ticket(String ticketId, Vehicle vehicle, ParkingSlot slot) {
        this(ticketId, vehicle, slot, LocalDateTime.now());
    }

    public Ticket(String ticketId, Vehicle vehicle, ParkingSlot slot, LocalDateTime entryTime) {
        this.ticketId = ticketId;
        this.vehicle = vehicle;
        this.slot = slot;
        this.entryTime = entryTime;
        this.status = TicketStatus.ACTIVE;
    }

    public String getTicketId() { return ticketId; }
    public Vehicle getVehicle() { return vehicle; }
    public ParkingSlot getSlot() { return slot; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public TicketStatus getStatus() { return status; }

    public long getHoursParked() {
        LocalDateTime end = (exitTime != null) ? exitTime : LocalDateTime.now();
        long minutes = Duration.between(entryTime, end).toMinutes();
        long hours = (minutes + 59) / 60;
        return Math.max(1, hours);
    }

    public double calculateFee() {
        return getHoursParked() * slot.getHourlyRate();
    }

    public void markPaid() {
        status = TicketStatus.PAID;
    }

    public void close() {
        exitTime = LocalDateTime.now();
        status = TicketStatus.CLOSED;
    }
}