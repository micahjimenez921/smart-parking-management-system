package fee;

import model.Ticket;

public class HourlyFee implements FeeStrategy {
    @Override
    public double calculateFee(Ticket ticket) {
        return ticket.getHoursParked() * ticket.getSlot().getHourlyRate();
    }

    @Override
    public String getDescription() {
        return "Hourly rate, rounded up to whole hours";
    }
}