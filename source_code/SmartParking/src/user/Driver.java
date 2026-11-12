package user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class Driver extends User {
    private static final int MAX_PLATES = 10;
    private static final Set<Permission> PERMISSIONS = Collections.unmodifiableSet(
            EnumSet.of(Permission.SELF_SERVICE));

    private final List<String> plates = new ArrayList<>();

    /** The detail is a list of plate numbers separated by semicolons. */
    public Driver(String id, String name, String username, String passwordHash, String platesText) {
        super(id, name, username, passwordHash);
        if (platesText != null) {
            for (String plate : platesText.split(";")) {
                if (!plate.isBlank()) addPlate(plate);
            }
        }
    }

    private static String normalize(String plate) {
        return plate.replaceAll("[\\s-]", "").toUpperCase();
    }

    public void addPlate(String plate) {
        if (plate == null || !plate.trim().matches("[A-Za-z0-9 -]{2,12}")) {
            throw new IllegalArgumentException("Enter a valid plate number (2 to 12 letters, numbers, spaces or dashes).");
        }
        if (ownsPlate(plate)) return;
        if (plates.size() >= MAX_PLATES) {
            throw new IllegalArgumentException("A driver can have at most " + MAX_PLATES + " plate numbers.");
        }
        plates.add(plate.trim().toUpperCase());
    }

    /** Takes a plate number off the account. Returns false if it was not on it. */
    public boolean removePlate(String plate) {
        if (plate == null) return false;
        String wanted = normalize(plate);
        return plates.removeIf(own -> normalize(own).equals(wanted));
    }

    public List<String> getPlates() { return Collections.unmodifiableList(plates); }

    public boolean ownsPlate(String plate) {
        if (plate == null) return false;
        String wanted = normalize(plate);
        for (String own : plates) {
            if (normalize(own).equals(wanted)) return true;
        }
        return false;
    }

    @Override
    public Role getRole() { return Role.DRIVER; }

    @Override
    public Set<Permission> getPermissions() { return PERMISSIONS; }

    @Override
    public String getDetail() { return String.join(";", plates); }
}