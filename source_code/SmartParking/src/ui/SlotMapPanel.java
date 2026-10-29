package ui;

import model.ParkingSlot;
import model.Ticket;
import service.ParkingLot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SlotMapPanel extends JPanel {
    private final ParkingLot lot;
    private final JPanel statsRow = new JPanel(new GridLayout(1, 4, 16, 0));
    private final JPanel grid = new JPanel(new GridLayout(0, 4, 16, 16));

    public SlotMapPanel(ParkingLot lot) {
        this.lot = lot;
        setLayout(new BorderLayout(0, 20));
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(28, 32, 28, 32));

        JPanel top = new JPanel(new BorderLayout(0, 20));
        top.setOpaque(false);
        top.add(Theme.pageHeader("Dashboard", "Live overview of the parking lot"), BorderLayout.NORTH);
        statsRow.setOpaque(false);
        top.add(statsRow, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        grid.setOpaque(false);
        JPanel gridWrap = new JPanel(new BorderLayout());
        gridWrap.setOpaque(false);
        gridWrap.add(grid, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(gridWrap);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(Theme.label("Slot map", Theme.HEADING, Theme.TEXT), BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        refresh();
    }

    public void refresh() {
        statsRow.removeAll();
        int total = lot.getSlots().size();
        int free = lot.getAvailableCount();
        statsRow.add(statCard("Total slots", String.valueOf(total), Theme.TEXT));
        statsRow.add(statCard("Available", String.valueOf(free), Theme.FREE));
        statsRow.add(statCard("Occupied", String.valueOf(total - free), Theme.BUSY));
        statsRow.add(statCard("Revenue", Theme.money(lot.getTotalRevenue()), Theme.PRIMARY));

        grid.removeAll();
        for (ParkingSlot s : lot.getSlots()) {
            grid.add(slotTile(s));
        }
        revalidate();
        repaint();
    }

    private JPanel statCard(String title, String value, Color color) {
        Theme.Card card = new Theme.Card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(Theme.label(title, Theme.BODY, Theme.MUTED));
        card.add(Box.createVerticalStrut(6));
        card.add(Theme.label(value, Theme.BIG_NUMBER, color));
        return card;
    }

    private JPanel slotTile(ParkingSlot s) {
        boolean free = s.isAvailable();
        Color text = free ? Theme.FREE_TEXT : Theme.BUSY_TEXT;

        Theme.Card tile = new Theme.Card();
        tile.setBackground(free ? Theme.FREE_BG : Theme.BUSY_BG);
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        tile.add(Theme.label(s.getSlotId(), Theme.TITLE, text));
        tile.add(Box.createVerticalStrut(2));
        tile.add(Theme.label(s.getSlotType() + "  |  " + Theme.money(s.getHourlyRate()) + "/hr",
                Theme.SMALL, text));
        tile.add(Box.createVerticalStrut(12));

        String status = "Available";
        if (!free) {
            Ticket t = lot.findTicketBySlot(s);
            status = (t != null)
                    ? t.getVehicle().getPlateNumber() + "  |  " + t.getTicketId()
                    : "Occupied";
        }
        tile.add(Theme.label(status, Theme.BODY_BOLD, text));
        return tile;
    }
}