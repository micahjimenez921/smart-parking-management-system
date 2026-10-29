package app;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;

public class FlatLafTest {
    public static void main(String[] args) {
        FlatLightLaf.setup();
        JFrame frame = new JFrame("FlatLaf works");
        frame.setSize(400, 200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new JLabel("It works!", SwingConstants.CENTER));
        frame.setVisible(true);
    }
}