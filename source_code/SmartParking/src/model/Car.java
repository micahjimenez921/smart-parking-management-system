package model;

public class Car extends Vehicle {
    public Car(String plateNumber, String ownerName) {
        super(plateNumber, ownerName);
    }

    @Override
    public String getVehicleType() {
        return "CAR";
    }
}