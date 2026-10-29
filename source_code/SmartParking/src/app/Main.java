package app;

import exception.InvalidTicketException;
import exception.ParkingFullException;
import exception.PaymentFailedException;
import model.*;
import payment.*;
import service.ParkingLot;

public class Main {
    public static void main(String[] args) {
        ParkingLot lot = new ParkingLot("Main Lot");
        lot.addSlot(new RegularSlot("R-01"));
        lot.addSlot(new HandicappedSlot("H-01"));
        lot.addSlot(new VIPSlot("V-01"));

        try {
            Ticket t = lot.parkVehicle(new Car("ABC123", "Ana"), "REGULAR");
            System.out.println("Parked: " + t.getVehicle().getPlateNumber()
                    + " in " + t.getSlot().getSlotId() + " | ticket " + t.getTicketId());
        } catch (ParkingFullException e) {
            System.out.println("Error: " + e.getMessage());
        }

        try {
            lot.checkout("T-001", new CardPayment("123"));
        } catch (PaymentFailedException e) {
            System.out.println("Payment failed: " + e.getMessage());
        } catch (InvalidTicketException e) {
            System.out.println("Error: " + e.getMessage());
        }

        try {
            Receipt r = lot.checkout("T-001", new CashPayment(50));
            System.out.println(r);
        } catch (PaymentFailedException e) {
            System.out.println("Payment failed: " + e.getMessage());
        } catch (InvalidTicketException e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println("Available slots: " + lot.getAvailableCount());
        System.out.println("Total revenue: " + lot.getTotalRevenue());
    }
}