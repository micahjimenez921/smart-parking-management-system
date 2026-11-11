package tests;

import exception.AuthenticationException;
import security.AccessPolicy;
import security.AccessPolicy.Decision;
import security.PasswordHasher;
import security.SessionManager;
import service.AuthService;
import service.UserDirectory;
import storage.FileUserRepository;
import user.Administrator;
import user.Driver;
import user.ParkingAttendant;
import user.Permission;
import user.Role;
import user.User;
import user.UserFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plain Java tests for accounts, passwords, sessions, and access rules.
 * Run this class and read the PASS / FAIL lines.
 */
public class SecurityTests {
    private static int passed = 0;
    private static int failed = 0;

    private interface Action {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        runSection("Passwords", SecurityTests::passwordTests);
        runSection("User roles (inheritance and polymorphism)", SecurityTests::roleTests);
        runSection("User directory", SecurityTests::directoryTests);
        runSection("Logging in", SecurityTests::loginTests);
        runSection("Sessions", SecurityTests::sessionTests);
        runSection("Access rules", SecurityTests::accessTests);
        runSection("Saving and loading users", SecurityTests::persistenceTests);

        System.out.println();
        System.out.println("==============================");
        System.out.println(passed + " passed, " + failed + " failed");
        System.out.println("==============================");
        if (failed > 0) System.exit(1);
    }

    private static void runSection(String title, Action tests) {
        System.out.println();
        System.out.println("--- " + title);
        try {
            tests.run();
        } catch (Throwable error) {
            failed++;
            System.out.println("FAIL  this group stopped early because of an unexpected "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    /* ---------- passwords ---------- */

    private static void passwordTests() throws Exception {
        String hash = PasswordHasher.hash("correct horse");
        check("a hash is not the password", !hash.contains("correct horse"));
        check("a hash starts with its method", hash.startsWith("pbkdf2$"));
        check("the right password is accepted", PasswordHasher.verify("correct horse", hash));
        check("a wrong password is refused", !PasswordHasher.verify("correct horsf", hash));
        check("an empty password is refused", !PasswordHasher.verify("", hash));
        check("a missing password is refused", !PasswordHasher.verify(null, hash));
        check("the same password gives a different hash each time (random salt)",
                !hash.equals(PasswordHasher.hash("correct horse")));
        check("a damaged stored hash never matches", !PasswordHasher.verify("correct horse", "pbkdf2$abc"));
        check("a stored hash from another method never matches", !PasswordHasher.verify("x", "plain$1$2$3"));
        check("nothing stored never matches", !PasswordHasher.verify("correct horse", null));

        checkThrows("a password shorter than 8 characters is rejected", IllegalArgumentException.class,
                () -> PasswordHasher.hash("short"));
        checkThrows("a very long password is rejected", IllegalArgumentException.class,
                () -> PasswordHasher.hash("x".repeat(101)));
        PasswordHasher.validate("x".repeat(100));
        pass("a password of exactly 100 characters is allowed");

        String generated = PasswordHasher.generate(12);
        checkEquals("generated passwords have the requested length", 12, generated.length());
        check("two generated passwords differ", !generated.equals(PasswordHasher.generate(12)));
        check("a generated password passes the password rules", PasswordHasher.verify(generated, PasswordHasher.hash(generated)));
    }

    /* ---------- roles ---------- */

    private static void roleTests() throws Exception {
        String hash = "h";
        List<User> users = List.of(
                new Administrator("U-001", "Ada Admin", "ada", hash),
                new ParkingAttendant("U-002", "Ben Attendant", "ben", hash, "NIGHT"),
                new Driver("U-003", "Dee Driver", "dee", hash, "ABC 123;XYZ-789"));

        checkEquals("an administrator's role", Role.ADMINISTRATOR, users.get(0).getRole());
        checkEquals("an attendant's role", Role.ATTENDANT, users.get(1).getRole());
        checkEquals("a driver's role", Role.DRIVER, users.get(2).getRole());

        Set<Permission> adminSet = users.get(0).getPermissions();
        check("an administrator can manage settings and users",
                adminSet.contains(Permission.SETTINGS) && adminSet.contains(Permission.USERS));
        check("an administrator can also do the attendant's work",
                users.get(0).can(Permission.ENTRY) && users.get(0).can(Permission.EXIT)
                        && users.get(0).can(Permission.REPORTS));

        check("an attendant can use the dashboard, entry, exit, tickets and reports",
                users.get(1).can(Permission.DASHBOARD) && users.get(1).can(Permission.ENTRY)
                        && users.get(1).can(Permission.EXIT) && users.get(1).can(Permission.TICKETS)
                        && users.get(1).can(Permission.REPORTS));
        check("an attendant cannot manage settings", !users.get(1).can(Permission.SETTINGS));
        check("an attendant cannot manage users", !users.get(1).can(Permission.USERS));

        check("a driver can use self service", users.get(2).can(Permission.SELF_SERVICE));
        check("a driver cannot use any staff feature",
                !users.get(2).can(Permission.DASHBOARD) && !users.get(2).can(Permission.ENTRY)
                        && !users.get(2).can(Permission.EXIT) && !users.get(2).can(Permission.TICKETS)
                        && !users.get(2).can(Permission.REPORTS) && !users.get(2).can(Permission.SETTINGS)
                        && !users.get(2).can(Permission.USERS));
        check("staff cannot use driver self service", !users.get(1).can(Permission.SELF_SERVICE));

        checkThrows("permissions cannot be changed from outside", UnsupportedOperationException.class,
                () -> users.get(1).getPermissions().add(Permission.SETTINGS));

        checkEquals("an attendant's shift", "NIGHT", ((ParkingAttendant) users.get(1)).getShift());
        checkEquals("an attendant's detail is the shift", "NIGHT", users.get(1).getDetail());
        checkEquals("the default shift is DAY", "DAY", new ParkingAttendant("U-9", "x", "xyz", hash, "").getShift());
        checkThrows("an unknown shift is rejected", IllegalArgumentException.class,
                () -> new ParkingAttendant("U-9", "x", "xyz", hash, "MORNING"));

        Driver driver = (Driver) users.get(2);
        checkEquals("a driver keeps the plates it was given", 2, driver.getPlates().size());
        check("a driver owns its own plate, ignoring spaces and capitals", driver.ownsPlate("abc123"));
        check("a driver owns a plate typed with a dash", driver.ownsPlate("xyz 789"));
        check("a driver does not own another plate", !driver.ownsPlate("OTHER 1"));
        driver.addPlate("abc 123");
        checkEquals("adding the same plate again changes nothing", 2, driver.getPlates().size());
        driver.addPlate("NEW 55");
        check("a new plate can be added", driver.ownsPlate("new55"));
        checkEquals("a driver's detail lists its plates", "ABC 123;XYZ-789;NEW 55", driver.getDetail());
        checkThrows("a bad plate is rejected", IllegalArgumentException.class, () -> driver.addPlate("!!"));
        checkThrows("a driver's plate list cannot be changed from outside", UnsupportedOperationException.class,
                () -> driver.getPlates().add("HACK 1"));

        checkThrows("a user needs a name", IllegalArgumentException.class,
                () -> new Administrator("U-9", " ", "abc", hash));
        checkThrows("a name over 60 characters is rejected", IllegalArgumentException.class,
                () -> new Administrator("U-9", "n".repeat(61), "abc", hash));

        check("UserFactory builds an administrator",
                UserFactory.create(Role.ADMINISTRATOR, "U-1", "a", "aaa", hash, "") instanceof Administrator);
        check("UserFactory builds an attendant",
                UserFactory.create(Role.ATTENDANT, "U-1", "a", "aaa", hash, "DAY") instanceof ParkingAttendant);
        check("UserFactory builds a driver",
                UserFactory.create(Role.DRIVER, "U-1", "a", "aaa", hash, "AB 12") instanceof Driver);
        checkEquals("Role.parse ignores capitals and spaces", Role.ATTENDANT, Role.parse(" attendant "));
        checkThrows("Role.parse rejects an unknown role", IllegalArgumentException.class, () -> Role.parse("boss"));
    }

    /* ---------- directory ---------- */

    private static void directoryTests() throws Exception {
        UserDirectory directory = new UserDirectory();
        User admin = directory.create(Role.ADMINISTRATOR, "Ada Admin", "Ada", "adminpass1", "");
        checkEquals("the first account is U-001", "U-001", admin.getId());
        checkEquals("usernames are stored in lowercase", "ada", admin.getUsername());
        check("the stored password is a hash", !admin.getPasswordHash().contains("adminpass1"));
        check("the stored hash verifies the password", PasswordHasher.verify("adminpass1", admin.getPasswordHash()));

        User attendant = directory.create(Role.ATTENDANT, "Ben", "ben.k", "attendant1", "EVENING");
        checkEquals("the next account is U-002", "U-002", attendant.getId());
        check("create builds the right subclass", attendant instanceof ParkingAttendant);

        check("a user can be found by username, ignoring capitals", directory.findByUsername(" BEN.K ") == attendant);
        check("a user can be found by id", directory.findById("U-001") == admin);
        check("an unknown username finds nothing", directory.findByUsername("nobody") == null);

        checkThrows("a taken username is refused, whatever its capitals", IllegalArgumentException.class,
                () -> directory.create(Role.DRIVER, "Dup", "ADA", "password123", "AB 12"));
        checkThrows("a username that is too short is refused", IllegalArgumentException.class,
                () -> directory.create(Role.DRIVER, "Sho", "ab", "password123", ""));
        checkThrows("a username with spaces is refused", IllegalArgumentException.class,
                () -> directory.create(Role.DRIVER, "Sp", "two words", "password123", ""));
        checkThrows("a weak password is refused", IllegalArgumentException.class,
                () -> directory.create(Role.DRIVER, "Weak", "weakling", "short", ""));
        checkThrows("a missing name is refused", IllegalArgumentException.class,
                () -> directory.create(Role.DRIVER, "", "noname", "password123", ""));
        checkEquals("refused accounts are not added", 2, directory.getUsers().size());
        checkThrows("the account list cannot be changed from outside", UnsupportedOperationException.class,
                () -> directory.getUsers().clear());

        directory.setPassword("U-002", "newpassword9");
        check("a reset password works", PasswordHasher.verify("newpassword9", attendant.getPasswordHash()));
        check("the old password stops working", !PasswordHasher.verify("attendant1", attendant.getPasswordHash()));
        checkThrows("a weak reset password is refused", IllegalArgumentException.class,
                () -> directory.setPassword("U-002", "x"));
        checkThrows("resetting an unknown user is refused", IllegalArgumentException.class,
                () -> directory.setPassword("U-999", "newpassword9"));

        checkThrows("the last administrator cannot be deleted", IllegalStateException.class,
                () -> directory.delete("U-001"));
        directory.delete("U-002");
        check("another user can be deleted", directory.findById("U-002") == null);
        checkThrows("deleting an unknown user is refused", IllegalArgumentException.class,
                () -> directory.delete("U-002"));

        directory.create(Role.ADMINISTRATOR, "Second Admin", "ada2", "adminpass2", "");
        directory.delete("U-001");
        check("with two administrators one can be deleted", directory.findById("U-001") == null);
        checkEquals("ids are not reused after a delete", "U-004", directory.create(Role.DRIVER, "D", "dee", "password123", "").getId());
        checkEquals("the last id number is remembered", 4, directory.getLastIdNumber());
        checkEquals("accounts counted by role", 1, directory.countByRole(Role.ADMINISTRATOR));

        UserDirectory fresh = new UserDirectory();
        String credentials = fresh.ensureAdministrator();
        check("an empty directory gets an administrator", credentials != null && credentials.startsWith("admin / "));
        checkEquals("exactly one account was created", 1, fresh.getUsers().size());
        String password = credentials.substring("admin / ".length());
        check("the printed password really is the administrator's password",
                PasswordHasher.verify(password, fresh.findByUsername("admin").getPasswordHash()));
        check("a directory that has an administrator gets no new one", fresh.ensureAdministrator() == null);
        checkEquals("...and still has one account", 1, fresh.getUsers().size());

        UserDirectory taken = new UserDirectory();
        taken.create(Role.DRIVER, "Driver Admin", "admin", "password123", "");
        check("if the name admin is taken, another name is used", taken.ensureAdministrator().startsWith("admin2 / "));
    }

    /* ---------- login ---------- */

    private static void loginTests() throws Exception {
        UserDirectory directory = new UserDirectory();
        directory.create(Role.ATTENDANT, "Ben", "ben", "benspassword", "DAY");
        directory.create(Role.ADMINISTRATOR, "Ada", "ada", "adaspassword", "");
        SessionManager sessions = new SessionManager(Duration.ofHours(8));
        AuthService auth = new AuthService(directory, sessions);
        Instant t0 = Instant.parse("2026-01-01T08:00:00Z");

        String token = auth.login("Ben", "benspassword", t0);
        check("a correct login gives a token", token != null && token.length() >= 40);
        check("the token belongs to that user", auth.userFor(token, t0) == directory.findByUsername("ben"));

        AuthenticationException wrongPassword = expectLoginFailure(auth, "ben", "wrongpassword", t0);
        AuthenticationException unknownUser = expectLoginFailure(auth, "nobody", "wrongpassword", t0);
        checkEquals("a wrong password gives the general message", "Wrong username or password.", wrongPassword.getMessage());
        checkEquals("an unknown user gets the same message, so usernames are not revealed",
                wrongPassword.getMessage(), unknownUser.getMessage());
        checkThrows("a missing password is refused", AuthenticationException.class, () -> auth.login("ben", null, t0));
        checkThrows("a missing username is refused", AuthenticationException.class, () -> auth.login(null, "benspassword", t0));

        // lockout after repeated failures
        UserDirectory d2 = new UserDirectory();
        d2.create(Role.ATTENDANT, "Cy", "cyrus", "cyspassword", "DAY");
        AuthService locked = new AuthService(d2, new SessionManager(Duration.ofHours(8)));
        for (int i = 0; i < AuthService.MAX_FAILURES; i++) {
            expectLoginFailure(locked, "cyrus", "guess" + i + "xxx", t0);
        }
        AuthenticationException lockMessage = expectLoginFailure(locked, "cyrus", "cyspassword", t0.plusSeconds(1));
        check("after 5 failures even the right password is refused for a while",
                lockMessage.getMessage().startsWith("Too many failed attempts"));
        check("the lockout message says how long to wait", lockMessage.getMessage().contains("seconds"));
        AuthenticationException stillLocked = expectLoginFailure(locked, "CYRUS", "cyspassword", t0.plusSeconds(59));
        check("the lockout applies whatever capitals are typed", stillLocked.getMessage().startsWith("Too many failed attempts"));
        check("after the lockout the right password works",
                locked.login("cyrus", "cyspassword", t0.plusSeconds(61)) != null);

        UserDirectory d3 = new UserDirectory();
        d3.create(Role.ATTENDANT, "Di", "dina", "dispassword", "DAY");
        AuthService reset = new AuthService(d3, new SessionManager(Duration.ofHours(8)));
        for (int i = 0; i < AuthService.MAX_FAILURES - 1; i++) {
            expectLoginFailure(reset, "dina", "wrongpass" + i, t0);
        }
        check("a good login before the limit works", reset.login("dina", "dispassword", t0) != null);
        for (int i = 0; i < AuthService.MAX_FAILURES - 1; i++) {
            expectLoginFailure(reset, "dina", "wrongpass" + i, t0);
        }
        check("a good login clears the count of failures", reset.login("dina", "dispassword", t0) != null);

        // logout
        auth.logout(token);
        check("after logout the token no longer works", auth.userFor(token, t0) == null);
        auth.logout("not-a-token");
        auth.logout(null);
        pass("logging out an unknown token does no harm");

        // removing and resetting accounts end their sessions
        UserDirectory d4 = new UserDirectory();
        d4.create(Role.ADMINISTRATOR, "Root", "root", "rootspassword", "");
        User eve = d4.create(Role.ATTENDANT, "Eve", "eve", "evespassword", "DAY");
        AuthService managed = new AuthService(d4, new SessionManager(Duration.ofHours(8)));
        String eveToken = managed.login("eve", "evespassword", t0);
        managed.resetPassword(eve.getId(), "freshpassword1");
        check("resetting a password ends the user's sessions", managed.userFor(eveToken, t0) == null);
        check("the reset password works", managed.login("eve", "freshpassword1", t0) != null);
        String eveToken2 = managed.login("eve", "freshpassword1", t0);
        managed.removeUser(eve.getId());
        check("deleting an account ends its sessions", managed.userFor(eveToken2, t0) == null);
        expectLoginFailure(managed, "eve", "freshpassword1", t0);
        pass("a deleted account can no longer log in");
        checkThrows("the last administrator cannot be removed", IllegalStateException.class,
                () -> managed.removeUser("U-001"));

        // changing a password
        String first = auth.login("ben", "benspassword", t0);
        String second = auth.login("ben", "benspassword", t0);
        User ben = directory.findByUsername("ben");
        checkThrows("changing a password needs the current one", AuthenticationException.class,
                () -> auth.changePassword(ben, "not-the-password", "brandnewpass", first));
        checkThrows("the new password must be strong enough", IllegalArgumentException.class,
                () -> auth.changePassword(ben, "benspassword", "short", first));
        auth.changePassword(ben, "benspassword", "brandnewpass", first);
        check("the new password works", auth.login("ben", "brandnewpass", t0) != null);
        expectLoginFailure(auth, "ben", "benspassword", t0);
        pass("the old password stops working");
        check("the session that changed the password stays logged in", auth.userFor(first, t0) == ben);
        check("the user's other sessions are ended", auth.userFor(second, t0) == null);
    }

    private static AuthenticationException expectLoginFailure(AuthService auth, String username, String password, Instant now) {
        try {
            auth.login(username, password, now);
        } catch (AuthenticationException e) {
            return e;
        }
        failed++;
        System.out.println("FAIL  a login that should have failed succeeded (" + username + ")");
        return new AuthenticationException("(login succeeded)");
    }

    /* ---------- sessions ---------- */

    private static void sessionTests() throws Exception {
        UserDirectory directory = new UserDirectory();
        User ben = directory.create(Role.ATTENDANT, "Ben", "ben", "benspassword", "DAY");
        User ada = directory.create(Role.ADMINISTRATOR, "Ada", "ada", "adaspassword", "");
        SessionManager sessions = new SessionManager(Duration.ofMinutes(30));
        Instant t0 = Instant.parse("2026-01-01T08:00:00Z");

        String token = sessions.start(ben, t0);
        check("a started session is found", sessions.find(token, t0.plusSeconds(10)) == ben);
        check("an unknown token finds nothing", sessions.find("nope", t0) == null);
        check("a missing token finds nothing", sessions.find(null, t0) == null);

        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 50; i++) tokens.add(sessions.start(ada, t0));
        checkEquals("every token is different", 50, tokens.size());

        SessionManager idle = new SessionManager(Duration.ofMinutes(30));
        String t = idle.start(ben, t0);
        check("a session is alive just before the idle limit", idle.find(t, t0.plus(Duration.ofMinutes(29))) == ben);
        check("using a session keeps it alive (the clock restarts)", idle.find(t, t0.plus(Duration.ofMinutes(58))) == ben);
        check("a session that sits idle for 30 minutes ends", idle.find(t, t0.plus(Duration.ofMinutes(100))) == null);
        check("an ended session stays ended", idle.find(t, t0.plus(Duration.ofMinutes(101))) == null);

        SessionManager manager = new SessionManager(Duration.ofHours(1));
        String a = manager.start(ben, t0);
        String b = manager.start(ben, t0);
        String c = manager.start(ada, t0);
        manager.end(a);
        check("ending one session leaves the others", manager.find(a, t0) == null && manager.find(b, t0) == ben);
        manager.endAllFor(ben.getId(), null);
        check("endAllFor ends every session of that user", manager.find(b, t0) == null);
        check("...and no one else's", manager.find(c, t0) == ada);

        String k1 = manager.start(ben, t0);
        String k2 = manager.start(ben, t0);
        manager.endAllFor(ben.getId(), k1);
        check("endAllFor can keep one session", manager.find(k1, t0) == ben && manager.find(k2, t0) == null);

        checkThrows("the idle limit must be more than zero", IllegalArgumentException.class,
                () -> new SessionManager(Duration.ZERO));

        SessionManager tidy = new SessionManager(Duration.ofMinutes(1));
        tidy.start(ben, t0);
        tidy.start(ben, t0);
        tidy.start(ada, t0.plus(Duration.ofHours(1)));
        checkEquals("old sessions are cleared when a new one starts", 1, tidy.count());
    }

    /* ---------- access rules ---------- */

    private static void accessTests() throws Exception {
        UserDirectory directory = new UserDirectory();
        User admin = directory.create(Role.ADMINISTRATOR, "Ada", "ada", "adaspassword", "");
        User attendant = directory.create(Role.ATTENDANT, "Ben", "ben", "benspassword", "DAY");
        User driver = directory.create(Role.DRIVER, "Dee", "dee", "deespassword", "AB 12");
        AccessPolicy policy = AccessPolicy.standard();

        checkEquals("the login address is open to everyone", Decision.ALLOWED, policy.check("/api/login", null));
        checkEquals("a visitor who is not logged in is turned away from the dashboard data",
                Decision.UNAUTHENTICATED, policy.check("/api/slots", null));
        checkEquals("a visitor cannot park a vehicle", Decision.UNAUTHENTICATED, policy.check("/api/park", null));
        checkEquals("a visitor cannot read settings", Decision.UNAUTHENTICATED, policy.check("/api/settings", null));
        checkEquals("a visitor cannot ask who is logged in", Decision.UNAUTHENTICATED, policy.check("/api/me", null));

        for (String path : List.of("/api/stats", "/api/slots", "/api/gates", "/api/gate/close", "/api/park",
                "/api/ticket", "/api/checkout", "/api/tickets", "/api/receipts")) {
            checkEquals("an attendant may use " + path, Decision.ALLOWED, policy.check(path, attendant));
        }
        for (String path : List.of("/api/settings", "/api/slots/add", "/api/slots/remove", "/api/users",
                "/api/users/add", "/api/users/remove", "/api/users/reset")) {
            checkEquals("an attendant may not use " + path, Decision.FORBIDDEN, policy.check(path, attendant));
            checkEquals("an administrator may use " + path, Decision.ALLOWED, policy.check(path, admin));
        }

        for (String path : List.of("/api/stats", "/api/slots", "/api/park", "/api/checkout", "/api/tickets",
                "/api/receipts", "/api/settings", "/api/users")) {
            checkEquals("a driver may not use " + path, Decision.FORBIDDEN, policy.check(path, driver));
        }
        checkEquals("a driver may use their own ticket address", Decision.ALLOWED, policy.check("/api/my/ticket", driver));
        checkEquals("a driver may pay their own ticket", Decision.ALLOWED, policy.check("/api/my/pay", driver));
        checkEquals("an attendant may not use the driver's self service", Decision.FORBIDDEN, policy.check("/api/my/ticket", attendant));

        for (User u : List.of(admin, attendant, driver)) {
            checkEquals(u.getRole() + " may log out", Decision.ALLOWED, policy.check("/api/logout", u));
            checkEquals(u.getRole() + " may ask who they are", Decision.ALLOWED, policy.check("/api/me", u));
            checkEquals(u.getRole() + " may change their own password", Decision.ALLOWED, policy.check("/api/password", u));
        }

        checkEquals("an address nobody listed is strict for an attendant", Decision.FORBIDDEN, policy.check("/api/forgotten", attendant));
        checkEquals("an address nobody listed is open only to administrators", Decision.ALLOWED, policy.check("/api/forgotten", admin));
        checkEquals("an address nobody listed needs a login", Decision.UNAUTHENTICATED, policy.check("/api/forgotten", null));
    }

    /* ---------- saving and loading users ---------- */

    private static void persistenceTests() throws Exception {
        Path folder = Files.createTempDirectory("smartpark-users");
        FileUserRepository repository = new FileUserRepository(folder.resolve("users.txt"));
        check("no user file before the first save", !repository.hasSavedUsers());

        UserDirectory directory = new UserDirectory();
        directory.create(Role.ADMINISTRATOR, "Ada \"The Boss\" O'Neil", "ada", "adminpass1", "");
        directory.create(Role.ATTENDANT, "Ben\tTab", "ben", "attendant1", "NIGHT");
        directory.create(Role.DRIVER, "Dee", "dee", "driverpass1", "ABC 123;XYZ-789");
        repository.save(directory);
        check("a user file exists after saving", repository.hasSavedUsers());

        String text = Files.readString(folder.resolve("users.txt"));
        check("the file never contains a plain password",
                !text.contains("adminpass1") && !text.contains("attendant1") && !text.contains("driverpass1"));

        UserDirectory back = new UserDirectory();
        repository.load(back);
        checkEquals("all accounts are restored", 3, back.getUsers().size());

        User ada = back.findByUsername("ada");
        check("an administrator is restored as an administrator", ada instanceof Administrator);
        checkEquals("a name with quotes is restored exactly", "Ada \"The Boss\" O'Neil", ada.getName());
        check("the restored password hash still verifies the password", PasswordHasher.verify("adminpass1", ada.getPasswordHash()));

        User ben = back.findByUsername("ben");
        check("an attendant is restored as an attendant", ben instanceof ParkingAttendant);
        checkEquals("a name with a tab is restored exactly", "Ben\tTab", ben.getName());
        checkEquals("the shift is restored", "NIGHT", ((ParkingAttendant) ben).getShift());

        User dee = back.findByUsername("dee");
        check("a driver is restored as a driver", dee instanceof Driver);
        check("the driver's plates are restored", ((Driver) dee).ownsPlate("abc123") && ((Driver) dee).ownsPlate("xyz789"));
        checkEquals("ids are restored", "U-003", dee.getId());
        checkEquals("the next new id continues the numbering", "U-004",
                back.create(Role.DRIVER, "New", "newdriver", "password123", "").getId());

        UserDirectory gaps = new UserDirectory();
        gaps.create(Role.ADMINISTRATOR, "Root", "rootuser", "password123", "");
        User temporary = gaps.create(Role.DRIVER, "Temp", "tempuser", "password123", "");
        gaps.delete(temporary.getId());
        FileUserRepository gapRepository = new FileUserRepository(folder.resolve("gaps.txt"));
        gapRepository.save(gaps);
        UserDirectory afterRestart = new UserDirectory();
        gapRepository.load(afterRestart);
        checkEquals("a deleted account's id is not reused even after a restart", "U-003",
                afterRestart.create(Role.DRIVER, "Next", "nextuser", "password123", "").getId());

        Files.writeString(folder.resolve("damaged.txt"), "SMARTPARK-USERS 1\n"
                + "GARBAGE\tx\n"
                + "USER\tU-001\tBOSS\tbad\tBad Role\thash\t\n"
                + "USER\tU-002\tDRIVER\tshort\n"
                + "USER\tU-003\tADMINISTRATOR\tgood\tGood Admin\tsomehash\t\n"
                + "USER\tU-004\tADMINISTRATOR\tgood\tSame Username\tsomehash\t\n");
        UserDirectory partial = new UserDirectory();
        new FileUserRepository(folder.resolve("damaged.txt")).load(partial);
        checkEquals("damaged lines are skipped and the good account loads", 1, partial.getUsers().size());
        checkEquals("...and it is the good one", "good", partial.getUsers().get(0).getUsername());

        Files.writeString(folder.resolve("wrong.txt"), "hello world\n");
        checkThrows("a file that is not a user file is rejected", IOException.class,
                () -> new FileUserRepository(folder.resolve("wrong.txt")).load(new UserDirectory()));
    }

    /* ---------- tiny test helpers ---------- */

    private static void pass(String name) {
        passed++;
        System.out.println("PASS  " + name);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            pass(name);
        } else {
            failed++;
            System.out.println("FAIL  " + name);
        }
    }

    private static void checkEquals(String name, Object expected, Object actual) {
        if (java.util.Objects.equals(expected, actual)) {
            pass(name);
        } else {
            failed++;
            System.out.println("FAIL  " + name + "   (expected " + expected + ", got " + actual + ")");
        }
    }

    private static void checkThrows(String name, Class<? extends Throwable> type, Action action) {
        try {
            action.run();
            failed++;
            System.out.println("FAIL  " + name + "   (nothing was thrown)");
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) {
                pass(name);
            } else {
                failed++;
                System.out.println("FAIL  " + name + "   (got " + thrown.getClass().getSimpleName() + ")");
            }
        }
    }
}