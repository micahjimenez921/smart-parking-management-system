package model;

public class HandicappedSlot extends ParkingSlot {
    public HandicappedSlot(String slotId) {
        super(slotId);
    }

    @Override
    public double getHourlyRate() { return 15.0; }

    @Override
    public String getSlotType() { return "HANDICAPPED"; }
}