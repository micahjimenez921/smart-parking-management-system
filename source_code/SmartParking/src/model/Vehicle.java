package model;

public abstract class Vehicle {
    private String plateNumber;
    private String ownerName;

    public Vehicle(String plateNumber, String ownerName) {
        this.plateNumber = plateNumber;
        this.ownerName = ownerName;
    }

    public String getPlateNumber() { return plateNumber; }
    public String getOwnerName()   { return ownerName; }

    public abstract String getVehicleType();
}