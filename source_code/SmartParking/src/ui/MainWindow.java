package ui;

import service.ParkingLot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MainWindow extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final List<JButton> navButtons = new ArrayList<>();

    public MainWindow(ParkingLot lot) {
        super("SmartPark - Parking Management System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 720);
        setMinimumSize(new Dimension(980, 620));
        setLocationRelativeTo(null);

        SlotMapPanel dashboard = new SlotMapPanel(lot);
        EntryPanel entry = new EntryPanel(lot, dashboard::refresh);
        ExitPanel exit = new ExitPanel(lot, dashboard::refresh);

        content.setBackground(Theme.BG);
        content.add(dashboard, "dashboard");
        content.add(entry, "entry");
        content.add(exit, "exit");

        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        showPage("dashboard");
    }

    private JPanel buildSidebar() {
        JPanel side = new JPanel();
        side.setBackground(Theme.SIDEBAR);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(28, 16, 28, 16));
        side.setPreferredSize(new Dimension(230, 0));

        JLabel brand = Theme.label("SmartPark", Theme.TITLE, Color.WHITE);
        brand.setBorder(new EmptyBorder(0, 8, 0, 0));
        JLabel tag = Theme.label("Parking management", Theme.SMALL, new Color(0x9CA3AF));
        tag.setBorder(new EmptyBorder(0, 8, 0, 0));
        side.add(brand);
        side.add(tag);
        side.add(Box.createVerticalStrut(32));

        side.add(navButton("Dashboard", "dashboard"));
        side.add(Box.createVerticalStrut(6));
        side.add(navButton("Vehicle entry", "entry"));
        side.add(Box.createVerticalStrut(6));
        side.add(navButton("Exit and payment", "exit"));

        side.add(Box.createVerticalGlue());
        JLabel footer = Theme.label("Advanced OOP project", Theme.SMALL, new Color(0x6B7280));
        footer.setBorder(new EmptyBorder(0, 8, 0, 0));
        side.add(footer);
        return side;
    }

    private JButton navButton(String text, String page) {
        JButton b = new JButton(text);
        b.setFont(Theme.BODY_BOLD);
        b.setForeground(Color.WHITE);
        b.setBackground(Theme.SIDEBAR);
        b.setFocusPainted(false);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(new EmptyBorder(12, 16, 12, 16));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("page", page);
        b.addActionListener(e -> showPage(page));
        navButtons.add(b);
        return b;
    }

    private void showPage(String page) {
        cards.show(content, page);
        for (JButton b : navButtons) {
            boolean active = page.equals(b.getClientProperty("page"));
            b.setBackground(active ? Theme.PRIMARY : Theme.SIDEBAR);
        }
    }
}