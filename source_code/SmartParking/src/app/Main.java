package app;

import model.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<Vehicle> vehicles = new ArrayList<>();

        vehicles.add(new Car("ABC123", "Ana"));
        vehicles.add(new Motorcycle("MOTO456", "Ben"));
        vehicles.add(new Truck("TRK789", "Carla"));

        for (Vehicle v : vehicles) {
            System.out.println(v.getPlateNumber() + " is a " + v.getVehicleType());
        }
    }
}