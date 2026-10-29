package app;

import com.formdev.flatlaf.FlatLightLaf;
import exception.ParkingFullException;
import model.*;
import service.ParkingLot;
import ui.MainWindow;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        ParkingLot lot = new ParkingLot("Main Lot");
        for (int i = 1; i <= 4; i++) lot.addSlot(new RegularSlot(String.format("R-%02d", i)));
        for (int i = 1; i <= 2; i++) lot.addSlot(new HandicappedSlot(String.format("H-%02d", i)));
        for (int i = 1; i <= 2; i++) lot.addSlot(new VIPSlot(String.format("V-%02d", i)));

        try {
            lot.parkVehicle(new Car("ABC123", "Ana"), "REGULAR");
            lot.parkVehicle(new Motorcycle("MOTO456", "Ben"), "REGULAR");
            lot.parkVehicle(new Truck("TRK789", "Carla"), "VIP");
        } catch (ParkingFullException e) {
            System.out.println(e.getMessage());
        }

        FlatLightLaf.setup();
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);

        SwingUtilities.invokeLater(() -> new MainWindow(lot).setVisible(true));
    }
}