package user;

public class UserFactory {
    public static User create(Role role, String id, String name, String username,
                              String passwordHash, String detail) {
        switch (role) {
            case ADMINISTRATOR:
                return new Administrator(id, name, username, passwordHash);
            case ATTENDANT:
                return new ParkingAttendant(id, name, username, passwordHash, detail);
            case DRIVER:
                return new Driver(id, name, username, passwordHash, detail);
            default:
                throw new IllegalArgumentException("Choose a role.");
        }
    }
}