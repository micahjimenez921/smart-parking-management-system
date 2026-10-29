package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class Theme {
    public static final Color PRIMARY = new Color(0x2563EB);
    public static final Color BG = new Color(0xF3F4F6);
    public static final Color CARD = Color.WHITE;
    public static final Color SIDEBAR = new Color(0x111827);
    public static final Color TEXT = new Color(0x111827);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color BORDER = new Color(0xE5E7EB);
    public static final Color FREE = new Color(0x16A34A);
    public static final Color FREE_BG = new Color(0xDCFCE7);
    public static final Color FREE_TEXT = new Color(0x166534);
    public static final Color BUSY = new Color(0xDC2626);
    public static final Color BUSY_BG = new Color(0xFEE2E2);
    public static final Color BUSY_TEXT = new Color(0x991B1B);

    public static final Font TITLE = new Font("Segoe UI", Font.BOLD, 26);
    public static final Font HEADING = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font BODY = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font BODY_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font BIG_NUMBER = new Font("Segoe UI", Font.BOLD, 30);

    public static final String CURRENCY = "\u20B1";

    public static String money(double value) {
        return CURRENCY + String.format("%,.2f", value);
    }

    public static String html(String text, int width) {
        return "<html><body style='width:" + width + "px'>" + text + "</body></html>";
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    public static JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(BODY_BOLD);
        b.setBackground(PRIMARY);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(11, 24, 11, 24));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JPanel pageHeader(String title, String subtitle) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(label(title, TITLE, TEXT));
        p.add(Box.createVerticalStrut(4));
        p.add(label(subtitle, BODY, MUTED));
        return p;
    }

    public static void formRow(JPanel parent, String labelText, JComponent field) {
        formRow(parent, label(labelText, SMALL, MUTED), field);
    }

    public static void formRow(JPanel parent, JLabel label, JComponent field) {
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setFont(BODY);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setPreferredSize(new Dimension(420, 40));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        parent.add(label);
        parent.add(Box.createVerticalStrut(6));
        parent.add(field);
        parent.add(Box.createVerticalStrut(16));
    }

    public static class Card extends JPanel {
        private final int arc;

        public Card() {
            this(16);
        }

        public Card(int arc) {
            this.arc = arc;
            setOpaque(false);
            setBackground(CARD);
            setBorder(new EmptyBorder(20, 20, 20, 20));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}