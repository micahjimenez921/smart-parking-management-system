package model;

public abstract class ParkingSlot {
    private String slotId;
    private boolean occupied;

    public ParkingSlot(String slotId) {
        this.slotId = slotId;
        this.occupied = false;
    }

    public String getSlotId() { return slotId; }

    public boolean isAvailable() { return !occupied; }

    public void occupy() {
        if (occupied) {
            throw new IllegalStateException("Slot " + slotId + " is already occupied.");
        }
        occupied = true;
    }

    public void vacate() {
        occupied = false;
    }

    public abstract double getHourlyRate();

    public abstract String getSlotType();
}