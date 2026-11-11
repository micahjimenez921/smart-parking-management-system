package user;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class Administrator extends User {
    private static final Set<Permission> PERMISSIONS = Collections.unmodifiableSet(EnumSet.of(
            Permission.DASHBOARD, Permission.ENTRY, Permission.EXIT, Permission.TICKETS,
            Permission.REPORTS, Permission.SETTINGS, Permission.USERS));

    public Administrator(String id, String name, String username, String passwordHash) {
        super(id, name, username, passwordHash);
    }

    @Override
    public Role getRole() { return Role.ADMINISTRATOR; }

    @Override
    public Set<Permission> getPermissions() { return PERMISSIONS; }

    @Override
    public String getDetail() { return ""; }
}