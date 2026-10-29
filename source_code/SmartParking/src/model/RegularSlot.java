package model;

public class RegularSlot extends ParkingSlot {
    public RegularSlot(String slotId) {
        super(slotId);
    }

    @Override
    public double getHourlyRate() { return 20.0; }

    @Override
    public String getSlotType() { return "REGULAR"; }
}