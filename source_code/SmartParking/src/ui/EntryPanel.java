package ui;

import exception.ParkingFullException;
import model.Car;
import model.Motorcycle;
import model.Ticket;
import model.Truck;
import model.Vehicle;
import service.ParkingLot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class EntryPanel extends JPanel {
    private final ParkingLot lot;
    private final Runnable onChange;
    private final JTextField plateField = new JTextField();
    private final JTextField ownerField = new JTextField();
    private final JComboBox<String> vehicleBox =
            new JComboBox<>(new String[]{"Car", "Motorcycle", "Truck"});
    private final JComboBox<String> slotBox =
            new JComboBox<>(new String[]{"REGULAR", "HANDICAPPED", "VIP"});
    private final JLabel message = new JLabel(" ");

    public EntryPanel(ParkingLot lot, Runnable onChange) {
        this.lot = lot;
        this.onChange = onChange;

        setLayout(new BorderLayout(0, 24));
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(28, 32, 28, 32));
        add(Theme.pageHeader("Vehicle entry", "Register an arriving vehicle and assign a slot"),
                BorderLayout.NORTH);

        plateField.putClientProperty("JTextField.placeholderText", "e.g. ABC123");
        ownerField.putClientProperty("JTextField.placeholderText", "Driver's full name");

        Theme.Card card = new Theme.Card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        Theme.formRow(card, "Plate number", plateField);
        Theme.formRow(card, "Owner name", ownerField);
        Theme.formRow(card, "Vehicle type", vehicleBox);
        Theme.formRow(card, "Slot type", slotBox);

        JButton parkButton = Theme.primaryButton("Park vehicle");
        parkButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        parkButton.addActionListener(e -> park());
        card.add(parkButton);
        card.add(Box.createVerticalStrut(14));

        message.setFont(Theme.BODY);
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(message);

        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrap.setOpaque(false);
        wrap.add(card);
        add(wrap, BorderLayout.CENTER);
    }

    private void park() {
        String plate = plateField.getText().trim().toUpperCase();
        String owner = ownerField.getText().trim();
        if (plate.isEmpty() || owner.isEmpty()) {
            showMessage("Please enter the plate number and the owner's name.", false);
            return;
        }

        Vehicle vehicle;
        String type = (String) vehicleBox.getSelectedItem();
        if ("Motorcycle".equals(type)) {
            vehicle = new Motorcycle(plate, owner);
        } else if ("Truck".equals(type)) {
            vehicle = new Truck(plate, owner);
        } else {
            vehicle = new Car(plate, owner);
        }

        try {
            Ticket t = lot.parkVehicle(vehicle, (String) slotBox.getSelectedItem());
            showMessage("Parked in slot <b>" + t.getSlot().getSlotId() + "</b>. Ticket ID: <b>"
                    + t.getTicketId() + "</b>. Keep it for exit.", true);
            plateField.setText("");
            ownerField.setText("");
            onChange.run();
        } catch (ParkingFullException e) {
            showMessage(e.getMessage(), false);
        }
    }

    private void showMessage(String text, boolean ok) {
        message.setForeground(ok ? Theme.FREE_TEXT : Theme.BUSY_TEXT);
        message.setText(Theme.html(text, 380));
    }
}