package service;

import security.PasswordHasher;
import user.Role;
import user.User;
import user.UserFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All the user accounts, with the rules for creating, changing, and removing them. */
public class UserDirectory {
    private final List<User> users = new ArrayList<>();
    private int lastIdNumber = 0;

    /** The number of the last id that was handed out. Saved, so a deleted account's id is never reused. */
    public int getLastIdNumber() {
        return lastIdNumber;
    }

    public void setLastIdNumber(int number) {
        lastIdNumber = Math.max(lastIdNumber, number);
    }

    public List<User> getUsers() {
        return Collections.unmodifiableList(users);
    }

    public User create(Role role, String name, String username, String password, String detail) {
        String cleanUsername = validateUsername(username);
        User.validateName(name);
        PasswordHasher.validate(password);
        if (findByUsername(cleanUsername) != null) {
            throw new IllegalArgumentException("The username " + cleanUsername + " is already taken.");
        }

        User user = UserFactory.create(role, nextId(), name, cleanUsername,
                PasswordHasher.hash(password), detail);
        users.add(user);
        return user;
    }

    /** Puts back an account that was saved earlier, with its password hash as it was. */
    public void restore(User user) {
        if (findById(user.getId()) != null || findByUsername(user.getUsername()) != null) {
            throw new IllegalArgumentException("The account " + user.getUsername() + " appears twice.");
        }
        users.add(user);
        if (user.getId().startsWith("U-")) {
            try {
                setLastIdNumber(Integer.parseInt(user.getId().substring(2)));
            } catch (NumberFormatException e) {
                /* not one of the numbered ids */
            }
        }
    }

    public static String validateUsername(String username) {
        String clean = (username == null) ? "" : username.trim().toLowerCase();
        if (!clean.matches("[a-z0-9._-]{3,30}")) {
            throw new IllegalArgumentException(
                    "The username must be 3 to 30 letters, numbers, dots, dashes or underscores.");
        }
        return clean;
    }

    public User findByUsername(String username) {
        if (username == null) return null;
        String wanted = username.trim().toLowerCase();
        for (User u : users) {
            if (u.getUsername().equals(wanted)) return u;
        }
        return null;
    }

    public User findById(String id) {
        for (User u : users) {
            if (u.getId().equals(id)) return u;
        }
        return null;
    }

    public void delete(String id) {
        User user = findById(id);
        if (user == null) {
            throw new IllegalArgumentException("User " + id + " not found.");
        }
        if (user.getRole() == Role.ADMINISTRATOR && countByRole(Role.ADMINISTRATOR) == 1) {
            throw new IllegalStateException("The last administrator cannot be deleted.");
        }
        users.remove(user);
    }

    public void setPassword(String id, String newPassword) {
        User user = findById(id);
        if (user == null) {
            throw new IllegalArgumentException("User " + id + " not found.");
        }
        PasswordHasher.validate(newPassword);
        user.setPasswordHash(PasswordHasher.hash(newPassword));
    }

    public int countByRole(Role role) {
        int count = 0;
        for (User u : users) {
            if (u.getRole() == role) count++;
        }
        return count;
    }

    /**
     * Makes sure at least one administrator exists. If there is none, creates one named admin and
     * returns the generated password so it can be shown once. Returns null if nothing was created.
     */
    public String ensureAdministrator() {
        if (countByRole(Role.ADMINISTRATOR) > 0) return null;

        String password = PasswordHasher.generate(12);
        String username = "admin";
        int n = 1;
        while (findByUsername(username) != null) {
            n++;
            username = "admin" + n;
        }
        create(Role.ADMINISTRATOR, "Administrator", username, password, "");
        return username + " / " + password;
    }

    private String nextId() {
        lastIdNumber++;
        return String.format("U-%03d", lastIdNumber);
    }
}