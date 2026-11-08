package model;

public class RegularSlot extends ParkingSlot {
    public static final double DEFAULT_RATE = 20.0;

    public RegularSlot(String slotId) {
        super(slotId, DEFAULT_RATE);
    }

    public RegularSlot(String slotId, double hourlyRate) {
        super(slotId, hourlyRate);
    }

    @Override
    public String getSlotType() { return "REGULAR"; }

    @Override
    public double getDefaultRate() { return DEFAULT_RATE; }
}