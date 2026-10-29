package model;

public class VIPSlot extends ParkingSlot {
    public VIPSlot(String slotId) {
        super(slotId);
    }

    @Override
    public double getHourlyRate() { return 50.0; }

    @Override
    public String getSlotType() { return "VIP"; }
}