package model;

public class Truck extends Vehicle {
    public Truck(String plateNumber, String ownerName) {
        super(plateNumber, ownerName);
    }

    @Override
    public String getVehicleType() {
        return "TRUCK";
    }
}