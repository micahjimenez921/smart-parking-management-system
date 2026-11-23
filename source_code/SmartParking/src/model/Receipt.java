package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
        this(ticketId, plateNumber, slotId, hours, methodName, amount, LocalDateTime.now());
    }

    public Receipt(String ticketId, String plateNumber, String slotId,
                   long hours, String methodName, double amount, LocalDateTime time) {
        this.ticketId = ticketId;
        this.plateNumber = plateNumber;
        this.slotId = slotId;
        this.hours = hours;
        this.methodName = methodName;
        this.amount = amount;
        this.time = time;
    }

    public String getTicketId() { return ticketId; }
    public String getPlateNumber() { return plateNumber; }
    public String getSlotId() { return slotId; }
    public long getHours() { return hours; }
    public String getMethodName() { return methodName; }
    public double getAmount() { return amount; }
    public LocalDateTime getTime() { return time; }

    /* A short reference number for the receipt, such as SP-261123-T005.
       It is worked out from the date and the ticket, so older saved receipts get one too. */
    public String getReference() {
        return "SP-" + time.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + ticketId.replace("-", "");
    }

    @Override
    public String toString() {
        return String.format("Receipt %s | %s | slot %s | %d hr(s) | %s | total %.2f",
                ticketId, plateNumber, slotId, hours, methodName, amount);
    }
}