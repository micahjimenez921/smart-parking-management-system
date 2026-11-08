package model;

public abstract class ParkingSlot {
    public static final double MAX_RATE = 10_000;

    private String slotId;
    private boolean occupied;
    private double hourlyRate;

    public ParkingSlot(String slotId, double hourlyRate) {
        validateRate(hourlyRate);
        this.slotId = slotId;
        this.occupied = false;
        this.hourlyRate = hourlyRate;
    }

    public static void validateRate(double rate) {
        if (Double.isNaN(rate) || Double.isInfinite(rate) || rate <= 0 || rate > MAX_RATE) {
            throw new IllegalArgumentException(
                    "The hourly rate must be more than 0 and at most " + (long) MAX_RATE + ".");
        }
    }

    public String getSlotId() { return slotId; }

    public boolean isAvailable() { return !occupied; }

    public double getHourlyRate() { return hourlyRate; }

    public void setHourlyRate(double rate) {
        validateRate(rate);
        this.hourlyRate = rate;
    }

    public void occupy() {
        if (occupied) {
            throw new IllegalStateException("Slot " + slotId + " is already occupied.");
        }
        occupied = true;
    }

    public void vacate() {
        occupied = false;
    }

    public abstract String getSlotType();

    public abstract double getDefaultRate();
}