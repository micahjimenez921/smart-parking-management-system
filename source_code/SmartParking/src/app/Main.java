package app;

import exception.InvalidTicketException;
import exception.ParkingFullException;
import model.*;
import service.ParkingLot;

import java.time.LocalDateTime;

public class Main {
    public static void main(String[] args) {
        ParkingLot lot = new ParkingLot("Main Lot");
        lot.addSlot(new RegularSlot("R-01"));
        lot.addSlot(new HandicappedSlot("H-01"));
        lot.addSlot(new VIPSlot("V-01"));

        try {
            Ticket t1 = lot.parkVehicle(new Car("ABC123", "Ana"), "REGULAR");
            System.out.println("Parked: " + t1.getVehicle().getPlateNumber()
                    + " in " + t1.getSlot().getSlotId() + " | ticket " + t1.getTicketId());
            lot.parkVehicle(new Truck("TRK789", "Carla"), "REGULAR");
        } catch (ParkingFullException e) {
            System.out.println("Error: " + e.getMessage());
        }

        try {
            Ticket done = lot.exitVehicle("T-001");
            System.out.println("Exited: " + done.getVehicle().getPlateNumber()
                    + " | hours charged: " + done.getHoursParked()
                    + " | fee: " + done.calculateFee());
            lot.exitVehicle("T-999");
        } catch (InvalidTicketException e) {
            System.out.println("Error: " + e.getMessage());
        }

        Ticket old = new Ticket("TEST", new Car("OLD111", "Zed"), new VIPSlot("V-99"),
                LocalDateTime.now().minusHours(2).minusMinutes(10));
        System.out.println("Fee for 2h10m in a VIP slot: " + old.calculateFee());
    }
}