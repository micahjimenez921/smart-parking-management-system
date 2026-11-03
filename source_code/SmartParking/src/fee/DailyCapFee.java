package fee;

import model.Ticket;

public class DailyCapFee implements FeeStrategy {
    private final int capHours;

    public DailyCapFee(int capHours) {
        if (capHours < 1 || capHours > 24) {
            throw new IllegalArgumentException("The daily cap must be between 1 and 24 hours.");
        }
        this.capHours = capHours;
    }

    @Override
    public double calculateFee(Ticket ticket) {
        long hours = ticket.getHoursParked();
        double rate = ticket.getSlot().getHourlyRate();
        double dailyMax = capHours * rate;

        long fullDays = hours / 24;
        long leftoverHours = hours % 24;
        return fullDays * dailyMax + Math.min(leftoverHours * rate, dailyMax);
    }

    @Override
    public String getDescription() {
        return "Hourly rate, never more than " + capHours + " hours' charge per day";
    }
}