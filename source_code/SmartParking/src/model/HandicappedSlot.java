package model;

public class HandicappedSlot extends ParkingSlot {
    public static final double DEFAULT_RATE = 15.0;

    public HandicappedSlot(String slotId) {
        super(slotId, DEFAULT_RATE);
    }

    public HandicappedSlot(String slotId, double hourlyRate) {
        super(slotId, hourlyRate);
    }

    @Override
    public String getSlotType() { return "HANDICAPPED"; }

    @Override
    public double getDefaultRate() { return DEFAULT_RATE; }
}