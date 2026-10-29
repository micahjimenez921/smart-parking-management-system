package model;

public class Motorcycle extends Vehicle {
    public Motorcycle(String plateNumber, String ownerName) {
        super(plateNumber, ownerName);
    }

    @Override
    public String getVehicleType() {
        return "MOTORCYCLE";
    }
}