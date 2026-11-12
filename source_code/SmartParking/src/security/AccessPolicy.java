package security;

import user.Permission;
import user.User;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Decides who may use each address of the API. */
public class AccessPolicy {
    public enum Decision {
        ALLOWED, UNAUTHENTICATED, FORBIDDEN
    }

    private final Set<String> publicPaths = new HashSet<>();
    private final Set<String> anyLoggedIn = new HashSet<>();
    private final Map<String, Permission> required = new HashMap<>();

    public AccessPolicy publicPath(String path) {
        publicPaths.add(path);
        return this;
    }

    public AccessPolicy anyLoggedIn(String path) {
        anyLoggedIn.add(path);
        return this;
    }

    public AccessPolicy require(String path, Permission permission) {
        required.put(path, permission);
        return this;
    }

    /** An address that is not listed needs the USERS permission, so a forgotten rule is strict, not open. */
    public Decision check(String path, User user) {
        if (publicPaths.contains(path)) return Decision.ALLOWED;
        if (user == null) return Decision.UNAUTHENTICATED;
        if (anyLoggedIn.contains(path)) return Decision.ALLOWED;

        Permission needed = required.getOrDefault(path, Permission.USERS);
        return user.can(needed) ? Decision.ALLOWED : Decision.FORBIDDEN;
    }

    public static AccessPolicy standard() {
        return new AccessPolicy()
                .publicPath("/api/login")
                .anyLoggedIn("/api/logout")
                .anyLoggedIn("/api/me")
                .anyLoggedIn("/api/password")

                .require("/api/stats", Permission.DASHBOARD)
                .require("/api/slots", Permission.DASHBOARD)
                .require("/api/gates", Permission.DASHBOARD)
                .require("/api/gate/close", Permission.DASHBOARD)

                .require("/api/park", Permission.ENTRY)

                .require("/api/ticket", Permission.EXIT)
                .require("/api/checkout", Permission.EXIT)

                .require("/api/tickets", Permission.TICKETS)
                .require("/api/receipts", Permission.TICKETS)

                .require("/api/settings", Permission.SETTINGS)
                .require("/api/slots/add", Permission.SETTINGS)
                .require("/api/slots/remove", Permission.SETTINGS)

                .require("/api/users", Permission.USERS)
                .require("/api/users/add", Permission.USERS)
                .require("/api/users/remove", Permission.USERS)
                .require("/api/users/reset", Permission.USERS)

                .require("/api/my/ticket", Permission.SELF_SERVICE)
                .require("/api/my/pay", Permission.SELF_SERVICE)
                .require("/api/my/plates/add", Permission.SELF_SERVICE)
                .require("/api/my/plates/remove", Permission.SELF_SERVICE);
    }
}