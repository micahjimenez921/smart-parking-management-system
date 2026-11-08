package model;

public class VIPSlot extends ParkingSlot {
    public static final double DEFAULT_RATE = 50.0;

    public VIPSlot(String slotId) {
        super(slotId, DEFAULT_RATE);
    }

    public VIPSlot(String slotId, double hourlyRate) {
        super(slotId, hourlyRate);
    }

    @Override
    public String getSlotType() { return "VIP"; }

    @Override
    public double getDefaultRate() { return DEFAULT_RATE; }
}