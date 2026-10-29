package ui;

import exception.InvalidTicketException;
import exception.PaymentFailedException;
import model.Receipt;
import model.Ticket;
import model.Vehicle;
import payment.CardPayment;
import payment.CashPayment;
import payment.OnlinePayment;
import payment.PaymentMethod;
import service.ParkingLot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ExitPanel extends JPanel {
    private final ParkingLot lot;
    private final Runnable onChange;
    private final JTextField ticketField = new JTextField();
    private final JLabel summary = new JLabel(" ");
    private final JLabel feeLabel = Theme.label("--", Theme.BIG_NUMBER, Theme.PRIMARY);
    private final JComboBox<String> methodBox =
            new JComboBox<>(new String[]{"Cash", "Card", "Online"});
    private final JLabel detailLabel = Theme.label("Cash tendered", Theme.SMALL, Theme.MUTED);
    private final JTextField detailField = new JTextField();
    private final JLabel message = new JLabel(" ");
    private Ticket currentTicket;

    public ExitPanel(ParkingLot lot, Runnable onChange) {
        this.lot = lot;
        this.onChange = onChange;

        setLayout(new BorderLayout(0, 24));
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(28, 32, 28, 32));
        add(Theme.pageHeader("Exit and payment", "Look up a ticket, pay, and release the slot"),
                BorderLayout.NORTH);

        ticketField.putClientProperty("JTextField.placeholderText", "e.g. T-001");
        summary.setFont(Theme.BODY);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        feeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        message.setFont(Theme.BODY);
        message.setAlignmentX(Component.LEFT_ALIGNMENT);

        Theme.Card left = new Theme.Card();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(Theme.label("Ticket", Theme.HEADING, Theme.TEXT));
        left.add(Box.createVerticalStrut(16));
        Theme.formRow(left, "Ticket ID", ticketField);
        JButton lookupButton = Theme.primaryButton("Look up ticket");
        lookupButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        lookupButton.addActionListener(e -> lookup());
        left.add(lookupButton);
        left.add(Box.createVerticalStrut(20));
        left.add(summary);
        left.add(Box.createVerticalStrut(16));
        left.add(Theme.label("Amount due", Theme.SMALL, Theme.MUTED));
        left.add(feeLabel);

        Theme.Card right = new Theme.Card();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(Theme.label("Payment", Theme.HEADING, Theme.TEXT));
        right.add(Box.createVerticalStrut(16));
        Theme.formRow(right, "Payment method", methodBox);
        Theme.formRow(right, detailLabel, detailField);
        JButton payButton = Theme.primaryButton("Pay and exit");
        payButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        payButton.addActionListener(e -> pay());
        right.add(payButton);
        right.add(Box.createVerticalStrut(14));
        right.add(message);

        methodBox.addActionListener(e -> updateMethod());
        updateMethod();

        JPanel cols = new JPanel(new GridLayout(1, 2, 24, 0));
        cols.setOpaque(false);
        cols.add(left);
        cols.add(right);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(cols, BorderLayout.NORTH);
        add(top, BorderLayout.CENTER);
    }

    private void updateMethod() {
        String choice = (String) methodBox.getSelectedItem();
        String hint;
        if ("Cash".equals(choice)) {
            detailLabel.setText("Cash tendered");
            hint = "Amount handed over";
        } else if ("Card".equals(choice)) {
            detailLabel.setText("Card number");
            hint = "16 digits";
        } else {
            detailLabel.setText("Mobile number");
            hint = "11 digits";
        }
        detailField.putClientProperty("JTextField.placeholderText", hint);
        detailField.setText("");
        detailField.repaint();
    }

    private void lookup() {
        message.setText(" ");
        String id = ticketField.getText().trim().toUpperCase();
        if (id.isEmpty()) {
            showMessage("Enter a ticket ID.", false);
            return;
        }
        try {
            currentTicket = lot.findTicket(id);
            Vehicle v = currentTicket.getVehicle();
            summary.setText(Theme.html(
                    "Vehicle: <b>" + v.getPlateNumber() + "</b> (" + v.getVehicleType() + ")<br>"
                            + "Owner: " + v.getOwnerName() + "<br>"
                            + "Slot: " + currentTicket.getSlot().getSlotId()
                            + " (" + currentTicket.getSlot().getSlotType() + ")<br>"
                            + "Hours charged: " + currentTicket.getHoursParked(), 330));
            feeLabel.setText(Theme.money(currentTicket.calculateFee()));
        } catch (InvalidTicketException e) {
            currentTicket = null;
            summary.setText(" ");
            feeLabel.setText("--");
            showMessage(e.getMessage(), false);
        }
    }

    private void pay() {
        if (currentTicket == null) {
            showMessage("Look up a ticket first.", false);
            return;
        }
        String detail = detailField.getText().trim();
        String choice = (String) methodBox.getSelectedItem();

        PaymentMethod method;
        CashPayment cash = null;
        if ("Cash".equals(choice)) {
            try {
                cash = new CashPayment(Double.parseDouble(detail));
            } catch (NumberFormatException e) {
                showMessage("Enter the cash amount as a number.", false);
                return;
            }
            method = cash;
        } else if ("Card".equals(choice)) {
            method = new CardPayment(detail);
        } else {
            method = new OnlinePayment(detail);
        }

        try {
            Receipt r = lot.checkout(currentTicket.getTicketId(), method);
            String text = "Payment successful via " + r.getMethodName() + ". Ticket "
                    + r.getTicketId() + " closed. Total: <b>" + Theme.money(r.getAmount()) + "</b>.";
            if (cash != null) {
                text += " Change: <b>" + Theme.money(cash.getChange(r.getAmount())) + "</b>.";
            }
            showMessage(text, true);

            currentTicket = null;
            ticketField.setText("");
            summary.setText(" ");
            feeLabel.setText("--");
            detailField.setText("");
            onChange.run();
        } catch (PaymentFailedException | InvalidTicketException e) {
            showMessage(e.getMessage(), false);
        }
    }

    private void showMessage(String text, boolean ok) {
        message.setForeground(ok ? Theme.FREE_TEXT : Theme.BUSY_TEXT);
        message.setText(Theme.html(text, 330));
    }
}