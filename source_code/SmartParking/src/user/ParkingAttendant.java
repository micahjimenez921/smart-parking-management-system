package user;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class ParkingAttendant extends User {
    public static final List<String> SHIFTS = List.of("DAY", "EVENING", "NIGHT");

    private static final Set<Permission> PERMISSIONS = Collections.unmodifiableSet(EnumSet.of(
            Permission.DASHBOARD, Permission.ENTRY, Permission.EXIT, Permission.TICKETS, Permission.REPORTS));

    private final String shift;

    public ParkingAttendant(String id, String name, String username, String passwordHash, String shift) {
        super(id, name, username, passwordHash);
        String wanted = (shift == null || shift.isBlank()) ? "DAY" : shift.trim().toUpperCase();
        if (!SHIFTS.contains(wanted)) {
            throw new IllegalArgumentException("The shift must be DAY, EVENING, or NIGHT.");
        }
        this.shift = wanted;
    }

    public String getShift() { return shift; }

    @Override
    public Role getRole() { return Role.ATTENDANT; }

    @Override
    public Set<Permission> getPermissions() { return PERMISSIONS; }

    @Override
    public String getDetail() { return shift; }
}