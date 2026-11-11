package user;

import java.util.Set;

public abstract class User {
    private final String id;
    private String name;
    private final String username;
    private String passwordHash;

    protected User(String id, String name, String username, String passwordHash) {
        validateName(name);
        this.id = id;
        this.name = name.trim();
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public static void validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 60) {
            throw new IllegalArgumentException("Enter a name of 1 to 60 characters.");
        }
    }

    public String getId() { return id; }

    public String getName() { return name; }

    public void setName(String newName) {
        validateName(newName);
        this.name = newName.trim();
    }

    public String getUsername() { return username; }

    public String getPasswordHash() { return passwordHash; }

    public void setPasswordHash(String hash) { this.passwordHash = hash; }

    public abstract Role getRole();

    public abstract Set<Permission> getPermissions();

    /** Role-specific information that is saved with the account (a shift, a list of plates, or nothing). */
    public abstract String getDetail();

    public boolean can(Permission permission) {
        return getPermissions().contains(permission);
    }
}