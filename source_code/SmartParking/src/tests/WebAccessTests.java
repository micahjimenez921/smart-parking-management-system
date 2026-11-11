package tests;

import api.AccessGuard;
import api.AuthApi;
import api.ParkingApi;
import api.SettingsApi;
import api.WebServer;
import app.DemoData;
import security.AccessPolicy;
import security.PasswordHasher;
import security.SessionManager;
import service.AuthService;
import service.ParkingLot;
import service.UserDirectory;
import storage.FileParkingRepository;
import storage.FileUserRepository;
import user.Role;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Plain Java tests for the login, the session cookie, and who may use which address.
 * They start a real server on a free port and talk to it over HTTP. Run this class and read the PASS / FAIL lines.
 */
public class WebAccessTests {
    private static int passed = 0;
    private static int failed = 0;

    private static HttpClient client;
    private static String base;

    private static class Reply {
        int status;
        String body;
        String cookieHeader;

        /** The session token from the Set-Cookie header, or null. */
        String token() {
            if (cookieHeader == null || !cookieHeader.startsWith("sp_session=")) return null;
            String value = cookieHeader.substring("sp_session=".length(), cookieHeader.indexOf(';'));
            return value.isEmpty() ? null : value;
        }
    }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("smartpark-web-test");
        Path web = Files.createDirectories(folder.resolve("web"));
        Files.writeString(web.resolve("login.html"), "<html>login page</html>");
        Files.writeString(web.resolve("index.html"), "<html>app</html>");

        ParkingLot lot = DemoData.createEmptyLot();
        FileParkingRepository lotRepository = new FileParkingRepository(folder.resolve("lot.txt"));

        UserDirectory directory = new UserDirectory();
        directory.create(Role.ADMINISTRATOR, "Ada Admin", "ada", "admin-pass-1", "");
        directory.create(Role.ATTENDANT, "Ben Attendant", "ben", "attendant-pass-1", "NIGHT");
        directory.create(Role.DRIVER, "Dee Driver", "dee", "driver-pass-1", "ABC 123;XYZ-789");
        directory.create(Role.ATTENDANT, "Lou Locked", "lou", "locked-pass-1", "DAY");
        FileUserRepository userRepository = new FileUserRepository(folder.resolve("users.txt"));

        AuthService auth = new AuthService(directory, new SessionManager(Duration.ofMinutes(30)));
        AccessGuard guard = new AccessGuard(auth, AccessPolicy.standard());

        WebServer server = new WebServer(0, web);
        server.setGuard(guard);
        new AuthApi(auth, directory, userRepository, guard).register(server);
        new ParkingApi(lot, lotRepository).register(server);
        new SettingsApi(lot, lotRepository).register(server);
        server.start();

        client = HttpClient.newHttpClient();
        base = "http://127.0.0.1:" + server.getPort();

        try {
            runSection("Pages and anonymous visitors", () -> anonymousTests());
            runSection("Signing in", () -> signInTests());
            runSection("Who may use what", () -> roleTests());
            runSection("Signing out", () -> signOutTests());
            runSection("Changing your own password", () -> passwordTests(directory, userRepository));
            runSection("Too many wrong passwords", () -> lockoutTests());
        } finally {
            server.stop();
        }

        System.out.println();
        System.out.println("==============================");
        System.out.println(passed + " passed, " + failed + " failed");
        System.out.println("==============================");
        if (failed > 0) System.exit(1);
    }

    private interface Action {
        void run() throws Exception;
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

    /* ---------- the groups ---------- */

    private static void anonymousTests() throws Exception {
        checkEquals("the login page can be opened without signing in", 200, call("GET", "/login.html", null, null).status);
        checkEquals("the main page file can be opened (it sends visitors to the login page itself)",
                200, call("GET", "/index.html", null, null).status);
        checkEquals("the dashboard numbers need a sign-in", 401, call("GET", "/api/stats", null, null).status);
        checkEquals("the slot list needs a sign-in", 401, call("GET", "/api/slots", null, null).status);
        checkEquals("parking a vehicle needs a sign-in", 401, call("POST", "/api/park", null, "plate=AB1").status);
        checkEquals("settings need a sign-in", 401, call("GET", "/api/settings", null, null).status);
        checkEquals("'who am I' needs a sign-in", 401, call("GET", "/api/me", null, null).status);
        checkEquals("a made-up session cookie is refused", 401, call("GET", "/api/me", "sp_session=not-a-real-token", null).status);
    }

    private static void signInTests() throws Exception {
        Reply wrong = login("ada", "wrong-password");
        checkEquals("a wrong password is refused", 401, wrong.status);
        check("a wrong password sets no cookie", wrong.cookieHeader == null);
        check("the refusal does not say which part was wrong", wrong.body.contains("Wrong username or password"));

        checkEquals("an unknown user is refused the same way", 401, login("nobody", "whatever-pass").status);
        checkEquals("an empty form is refused", 401, call("POST", "/api/login", null, "").status);
        checkEquals("signing in with GET is not allowed", 405, call("GET", "/api/login", null, null).status);

        Reply ok = login("ada", "admin-pass-1");
        checkEquals("the right password signs in", 200, ok.status);
        check("a session cookie is sent back", ok.token() != null);
        check("the cookie is hidden from page scripts (HttpOnly)", ok.cookieHeader.contains("HttpOnly"));
        check("the cookie is not sent from other websites (SameSite=Strict)", ok.cookieHeader.contains("SameSite=Strict"));
        check("the answer says who signed in", ok.body.contains("\"username\":\"ada\"") && ok.body.contains("\"role\":\"ADMINISTRATOR\""));
        check("the answer never contains the password or its hash",
                !ok.body.contains("admin-pass-1") && !ok.body.contains("pbkdf2"));

        Reply me = call("GET", "/api/me", "sp_session=" + ok.token(), null);
        checkEquals("'who am I' works with the cookie", 200, me.status);
        check("'who am I' lists the permissions", me.body.contains("\"SETTINGS\"") && me.body.contains("\"USERS\""));

        Reply upper = login("  ADA ", "admin-pass-1");
        checkEquals("the username ignores capitals and spaces around it", 200, upper.status);
    }

    private static void roleTests() throws Exception {
        String admin = "sp_session=" + login("ada", "admin-pass-1").token();
        String attendant = "sp_session=" + login("ben", "attendant-pass-1").token();
        String driver = "sp_session=" + login("dee", "driver-pass-1").token();

        checkEquals("an administrator sees the dashboard numbers", 200, call("GET", "/api/stats", admin, null).status);
        checkEquals("an administrator opens settings", 200, call("GET", "/api/settings", admin, null).status);

        checkEquals("an attendant sees the dashboard numbers", 200, call("GET", "/api/stats", attendant, null).status);
        checkEquals("an attendant sees the tickets", 200, call("GET", "/api/tickets", attendant, null).status);
        checkEquals("an attendant may use the entry address (a bad plate gets 400, not 403)",
                400, call("POST", "/api/park", attendant, "plate=%21&owner=x&vehicleType=CAR&slotType=REGULAR").status);
        checkEquals("an attendant is NOT allowed to open settings", 403, call("GET", "/api/settings", attendant, null).status);
        checkEquals("an attendant is NOT allowed to add a slot", 403, call("POST", "/api/slots/add", attendant, "type=VIP").status);
        checkEquals("an attendant is NOT allowed to change prices", 403, call("POST", "/api/settings", attendant, "x=1").status);

        Reply driverMe = call("GET", "/api/me", driver, null);
        checkEquals("a driver can see their own details", 200, driverMe.status);
        check("a driver's details include their plates", driverMe.body.contains("ABC 123;XYZ-789"));
        check("a driver only has the self-service permission",
                driverMe.body.contains("\"permissions\":[\"SELF_SERVICE\"]"));
        checkEquals("a driver is NOT allowed to see the dashboard numbers", 403, call("GET", "/api/stats", driver, null).status);
        checkEquals("a driver is NOT allowed to park a vehicle", 403, call("POST", "/api/park", driver, "plate=AB1").status);
        checkEquals("a driver is NOT allowed to see the tickets", 403, call("GET", "/api/tickets", driver, null).status);
        checkEquals("a driver is NOT allowed to open settings", 403, call("GET", "/api/settings", driver, null).status);
    }

    private static void signOutTests() throws Exception {
        Reply signedIn = login("ben", "attendant-pass-1");
        String cookie = "sp_session=" + signedIn.token();
        checkEquals("the session works before signing out", 200, call("GET", "/api/me", cookie, null).status);

        Reply out = call("POST", "/api/logout", cookie, "");
        checkEquals("signing out works", 200, out.status);
        check("signing out clears the cookie", out.cookieHeader != null && out.cookieHeader.contains("Max-Age=0"));
        checkEquals("the old cookie no longer works", 401, call("GET", "/api/me", cookie, null).status);
        checkEquals("the old cookie no longer opens the dashboard numbers", 401, call("GET", "/api/stats", cookie, null).status);
        checkEquals("signing out with GET is not allowed", 405, call("GET", "/api/logout", "sp_session=" + login("ben", "attendant-pass-1").token(), null).status);
    }

    private static void passwordTests(UserDirectory directory, FileUserRepository userRepository) throws Exception {
        String first = "sp_session=" + login("dee", "driver-pass-1").token();
        String second = "sp_session=" + login("dee", "driver-pass-1").token();

        checkEquals("changing a password needs a sign-in", 401,
                call("POST", "/api/password", null, "current=driver-pass-1&newPassword=another-pass-2").status);
        checkEquals("a wrong current password is refused with 400, so the page stays open", 400,
                call("POST", "/api/password", first, "current=nope-nope-1&newPassword=another-pass-2").status);
        checkEquals("a new password that is too short is refused", 400,
                call("POST", "/api/password", first, "current=driver-pass-1&newPassword=short").status);
        checkEquals("the same password again is refused", 400,
                call("POST", "/api/password", first, "current=driver-pass-1&newPassword=driver-pass-1").status);
        checkEquals("the old password still works after those refusals", 200, login("dee", "driver-pass-1").status);

        Reply changed = call("POST", "/api/password", first, "current=driver-pass-1&newPassword=another-pass-2");
        checkEquals("a correct change works", 200, changed.status);
        checkEquals("the session that changed the password stays signed in", 200, call("GET", "/api/me", first, null).status);
        checkEquals("the person's other session is signed out", 401, call("GET", "/api/me", second, null).status);
        checkEquals("the old password no longer signs in", 401, login("dee", "driver-pass-1").status);
        checkEquals("the new password signs in", 200, login("dee", "another-pass-2").status);

        UserDirectory reloaded = new UserDirectory();
        userRepository.load(reloaded);
        check("the new password was saved to the file",
                PasswordHasher.verify("another-pass-2", reloaded.findByUsername("dee").getPasswordHash()));
        check("the old password is not in the saved file anymore",
                !PasswordHasher.verify("driver-pass-1", reloaded.findByUsername("dee").getPasswordHash()));
    }

    private static void lockoutTests() throws Exception {
        for (int i = 0; i < 5; i++) {
            checkEquals("wrong attempt " + (i + 1) + " is refused", 401, login("lou", "wrong-guess-" + i).status);
        }
        Reply locked = login("lou", "locked-pass-1");
        checkEquals("even the right password is refused during the lockout", 401, locked.status);
        check("the lockout message says to wait", locked.body.contains("Too many failed attempts"));
        check("no cookie is given during the lockout", locked.cookieHeader == null);
        checkEquals("other accounts are not affected by the lockout", 200, login("ada", "admin-pass-1").status);
    }

    /* ---------- talking to the server ---------- */

    private static Reply login(String username, String password) throws Exception {
        String body = "username=" + java.net.URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + java.net.URLEncoder.encode(password, StandardCharsets.UTF_8);
        return call("POST", "/api/login", null, body);
    }

    private static Reply call(String method, String path, String cookie, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10));
        if (cookie != null) builder.header("Cookie", cookie);
        if (body != null) {
            builder.header("Content-Type", "application/x-www-form-urlencoded");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        Reply reply = new Reply();
        reply.status = response.statusCode();
        reply.body = response.body();
        reply.cookieHeader = response.headers().firstValue("Set-Cookie").orElse(null);
        return reply;
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
        if (expected.equals(actual)) {
            pass(name);
        } else {
            failed++;
            System.out.println("FAIL  " + name + "   (expected " + expected + ", got " + actual + ")");
        }
    }
}
