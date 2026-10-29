package model;

import java.time.LocalDateTime;

public class Receipt {
    private String ticketId;
    private String plateNumber;
    private String slotId;
    private long hours;
    private String methodName;
    private double amount;
    private LocalDateTime time;

    public Receipt(String ticketId, String plateNumber, String slotId,
                   long hours, String methodName, double amount) {
        this.ticketId = ticketId;
        this.plateNumber = plateNumber;
        this.slotId = slotId;
        this.hours = hours;
        this.methodName = methodName;
        this.amount = amount;
        this.time = LocalDateTime.now();
    }

    public String getTicketId() { return ticketId; }
    public String getPlateNumber() { return plateNumber; }
    public String getSlotId() { return slotId; }
    public long getHours() { return hours; }
    public String getMethodName() { return methodName; }
    public double getAmount() { return amount; }
    public LocalDateTime getTime() { return time; }

    @Override
    public String toString() {
        return String.format("Receipt %s | %s | slot %s | %d hr(s) | %s | total %.2f",
                ticketId, plateNumber, slotId, hours, methodName, amount);
    }
}