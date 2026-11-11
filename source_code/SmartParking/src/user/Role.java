package user;

public enum Role {
    ADMINISTRATOR, ATTENDANT, DRIVER;

    public static Role parse(String text) {
        try {
            return Role.valueOf(text == null ? "" : text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Choose a role.");
        }
    }
}