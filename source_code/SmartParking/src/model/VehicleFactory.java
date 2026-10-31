package model;

public class VehicleFactory {
    public static Vehicle create(String type, String plateNumber, String ownerName) {
        if ("CAR".equals(type)) return new Car(plateNumber, ownerName);
        if ("MOTORCYCLE".equals(type)) return new Motorcycle(plateNumber, ownerName);
        if ("TRUCK".equals(type)) return new Truck(plateNumber, ownerName);
        throw new IllegalArgumentException("Choose a vehicle type.");
    }
}