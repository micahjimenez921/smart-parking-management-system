package tests;

import api.AccessGuard;
import api.AuthApi;
import api.UserApi;
import api.WebServer;
import security.AccessPolicy;
import security.SessionManager;
import service.AuthService;
import service.UserDirectory;
import storage.FileUserRepository;
import user.Role;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain Java tests for managing accounts (the administrator's Users addresses) and for driver self-registration.
 * They start a real server on a free port and talk to it over HTTP. Run this class and read the PASS / FAIL lines.
 */
public class UserApiTests {
    private static int passed = 0;
    private static int failed = 0;

    private static HttpClient client;
    private static String base;

    private static UserDirectory directory;
    private static FileUserRepository repository;

    private static class Reply {
        int status;
        String body;
        String cookieHeader;

        String token() {
            if (cookieHeader == null || !cookieHeader.startsWith("sp_session=")) return null;
            return cookieHeader.substring("sp_session=".length(), cookieHeader.indexOf(';'));
        }
    }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("smartpark-users-test");
        Path web = Files.createDirectories(folder.resolve("web"));
        Files.writeString(web.resolve("index.html"), "<html>app</html>");

        directory = new UserDirectory();
        directory.create(Role.ADMINISTRATOR, "Ada Admin", "ada", "admin-pass-1", "");
        directory.create(Role.ATTENDANT, "Ben Attendant", "ben", "attendant-pass-1", "DAY");
        directory.create(Role.DRIVER, "Dee Driver", "dee", "driver-pass-1", "ABC 123");
        repository = new FileUserRepository(folder.resolve("users.txt"));

        AuthService auth = new AuthService(directory, new SessionManager(Duration.ofMinutes(30)));
        AccessGuard guard = new AccessGuard(auth, AccessPolicy.standard());

        WebServer server = new WebServer(0, web);
        server.setGuard(guard);
        new AuthApi(auth, directory, repository, guard).register(server);
        new UserApi(auth, directory, repository, guard).register(server);
        server.start();

        client = HttpClient.newHttpClient();
        base = "http://127.0.0.1:" + server.getPort();

        try {
            runSection("Who may use the Users addresses", () -> accessTests());
            runSection("Listing accounts", () -> listTests());
            runSection("Adding accounts", () -> addTests());
            runSection("Resetting passwords", () -> resetTests());
            runSection("Deleting accounts", () -> deleteTests());
            runSection("Accounts are saved", () -> savedTests());
            runSection("Driver self-registration", () -> registerTests());
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

    private static void accessTests() throws Exception {
        String attendant = cookieFor("ben", "attendant-pass-1");
        String driver = cookieFor("dee", "driver-pass-1");
        String admin = cookieFor("ada", "admin-pass-1");

        checkEquals("signed-out visitors cannot list accounts (401)", 401, call("GET", "/api/users", null, null).status);
        checkEquals("an attendant cannot list accounts (403)", 403, call("GET", "/api/users", attendant, null).status);
        checkEquals("a driver cannot list accounts (403)", 403, call("GET", "/api/users", driver, null).status);
        checkEquals("an administrator can", 200, call("GET", "/api/users", admin, null).status);

        String add = form("role", "ADMINISTRATOR", "name", "Sneaky Person", "username", "sneaky", "password", "sneaky-pass-1");
        checkEquals("signed-out visitors cannot add accounts (401)", 401, call("POST", "/api/users/add", null, add).status);
        checkEquals("an attendant cannot add accounts (403)", 403, call("POST", "/api/users/add", attendant, add).status);
        checkEquals("a driver cannot add an administrator (403)", 403, call("POST", "/api/users/add", driver, add).status);
        check("nothing was created", directory.findByUsername("sneaky") == null);

        String deeId = directory.findByUsername("dee").getId();
        checkEquals("an attendant cannot reset a password (403)", 403,
                call("POST", "/api/users/reset", attendant, form("id", deeId, "password", "hacked-pass-1")).status);
        checkEquals("an attendant cannot delete an account (403)", 403,
                call("POST", "/api/users/remove", attendant, form("id", deeId)).status);
        check("the driver can still sign in with the old password", cookieForOrNull("dee", "driver-pass-1") != null);

        checkEquals("GET is refused on add (405)", 405, call("GET", "/api/users/add", admin, null).status);
        checkEquals("GET is refused on reset (405)", 405, call("GET", "/api/users/reset", admin, null).status);
        checkEquals("GET is refused on remove (405)", 405, call("GET", "/api/users/remove", admin, null).status);
        checkEquals("POST is refused on the list (405)", 405, call("POST", "/api/users", admin, "").status);
    }

    private static void listTests() throws Exception {
        String admin = cookieFor("ada", "admin-pass-1");
        String body = call("GET", "/api/users", admin, null).body;

        check("lists every account", body.contains("\"username\":\"ada\"") && body.contains("\"username\":\"ben\"")
                && body.contains("\"username\":\"dee\""));
        check("shows each role", body.contains("\"role\":\"ADMINISTRATOR\"") && body.contains("\"role\":\"ATTENDANT\"")
                && body.contains("\"role\":\"DRIVER\""));
        check("shows the shift and the plates", body.contains("\"detail\":\"DAY\"") && body.contains("\"detail\":\"ABC 123\""));
        check("never sends a password or a hash", !body.toLowerCase().contains("hash") && !body.contains("pass")
                && !body.contains("$") && !body.contains("PBKDF"));
    }

    private static void addTests() throws Exception {
        String admin = cookieFor("ada", "admin-pass-1");

        Reply attendant = call("POST", "/api/users/add", admin,
                form("role", "ATTENDANT", "name", "Night Nina", "username", "Nina", "password", "nina-pass-123", "detail", "night"));
        checkEquals("adds an attendant", 200, attendant.status);
        check("the username is saved in lower case", attendant.body.contains("\"username\":\"nina\""));
        check("the shift is saved", attendant.body.contains("\"detail\":\"NIGHT\""));
        check("a typed password is not echoed back", !attendant.body.contains("nina-pass-123")
                && !attendant.body.contains("generatedPassword"));
        check("the new attendant can sign in", cookieForOrNull("nina", "nina-pass-123") != null);

        Reply driver = call("POST", "/api/users/add", admin,
                form("role", "DRIVER", "name", "Sam Driver", "username", "sam", "password", "sam-pass-1234", "detail", "SAM 123; SAM-456"));
        checkEquals("adds a driver with plates", 200, driver.status);
        check("the plates are saved", driver.body.contains("SAM 123;SAM-456"));

        Reply second = call("POST", "/api/users/add", admin,
                form("role", "ADMINISTRATOR", "name", "Second Admin", "username", "ada2", "password", "ada2-pass-123"));
        checkEquals("adds another administrator", 200, second.status);
        check("the new administrator can use the Users page", call("GET", "/api/users", cookieFor("ada2", "ada2-pass-123"), null).status == 200);

        Reply generated = call("POST", "/api/users/add", admin,
                form("role", "DRIVER", "name", "Gen Erated", "username", "generated", "password", ""));
        checkEquals("an empty password generates one", 200, generated.status);
        String password = field(generated.body, "generatedPassword");
        check("the generated password is shown in the answer", password != null && password.length() >= 12);
        check("the generated password works", password != null && cookieForOrNull("generated", password) != null);

        checkEquals("a taken username is refused (400)", 400,
                call("POST", "/api/users/add", admin, form("role", "DRIVER", "name", "Copy", "username", "DEE", "password", "copy-pass-1234")).status);
        checkEquals("a bad username is refused (400)", 400,
                call("POST", "/api/users/add", admin, form("role", "DRIVER", "name", "Bad", "username", "a b", "password", "bad-pass-1234")).status);
        checkEquals("a short password is refused (400)", 400,
                call("POST", "/api/users/add", admin, form("role", "DRIVER", "name", "Short", "username", "shorty", "password", "abc")).status);
        checkEquals("an empty name is refused (400)", 400,
                call("POST", "/api/users/add", admin, form("role", "DRIVER", "name", "  ", "username", "noname", "password", "noname-pass-1")).status);
        checkEquals("an unknown role is refused (400)", 400,
                call("POST", "/api/users/add", admin, form("role", "BOSS", "name", "Boss", "username", "boss", "password", "boss-pass-1234")).status);
        checkEquals("a bad shift is refused (400)", 400,
                call("POST", "/api/users/add", admin, form("role", "ATTENDANT", "name", "Late", "username", "late", "password", "late-pass-1234", "detail", "MORNING")).status);
        checkEquals("a bad plate is refused (400)", 400,
                call("POST", "/api/users/add", admin, form("role", "DRIVER", "name", "Plate", "username", "plated", "password", "plate-pass-1234", "detail", "!!!")).status);
        check("refused requests created nothing", directory.findByUsername("shorty") == null
                && directory.findByUsername("boss") == null && directory.findByUsername("plated") == null);
    }

    private static void resetTests() throws Exception {
        String admin = cookieFor("ada", "admin-pass-1");
        String ben = cookieFor("ben", "attendant-pass-1");
        String benId = directory.findByUsername("ben").getId();

        check("Ben is signed in", call("GET", "/api/me", ben, null).status == 200);
        Reply reset = call("POST", "/api/users/reset", admin, form("id", benId, "password", "ben-new-pass-1"));
        checkEquals("an administrator resets a password", 200, reset.status);
        check("a typed password is not echoed back", !reset.body.contains("ben-new-pass-1"));
        checkEquals("Ben's open session ended (401)", 401, call("GET", "/api/me", ben, null).status);
        check("the old password no longer works", cookieForOrNull("ben", "attendant-pass-1") == null);
        check("the new password works", cookieForOrNull("ben", "ben-new-pass-1") != null);

        Reply generated = call("POST", "/api/users/reset", admin, form("id", benId, "password", ""));
        checkEquals("an empty password generates one", 200, generated.status);
        String password = field(generated.body, "generatedPassword");
        check("the generated password works", password != null && cookieForOrNull("ben", password) != null);

        checkEquals("a short password is refused (400)", 400,
                call("POST", "/api/users/reset", admin, form("id", benId, "password", "abc")).status);
        checkEquals("an unknown account is a 404", 404,
                call("POST", "/api/users/reset", admin, form("id", "U-999", "password", "whatever-123")).status);
        checkEquals("an administrator cannot reset their own password here (400)", 400,
                call("POST", "/api/users/reset", admin, form("id", directory.findByUsername("ada").getId(), "password", "new-admin-pass")).status);
        check("the administrator is still signed in", call("GET", "/api/me", admin, null).status == 200);
    }

    private static void deleteTests() throws Exception {
        String admin = cookieFor("ada", "admin-pass-1");
        String adaId = directory.findByUsername("ada").getId();

        checkEquals("an administrator cannot delete themselves (400)", 400,
                call("POST", "/api/users/remove", admin, form("id", adaId)).status);
        check("they are still there", directory.findByUsername("ada") != null);

        String sam = cookieFor("sam", "sam-pass-1234");
        String samId = directory.findByUsername("sam").getId();
        checkEquals("deletes a driver", 200, call("POST", "/api/users/remove", admin, form("id", samId)).status);
        check("the driver is gone", directory.findByUsername("sam") == null);
        checkEquals("their open session ended (401)", 401, call("GET", "/api/me", sam, null).status);
        check("they cannot sign in again", cookieForOrNull("sam", "sam-pass-1234") == null);
        check("the list no longer shows them", !call("GET", "/api/users", admin, null).body.contains("\"username\":\"sam\""));

        checkEquals("an unknown account is a 404", 404, call("POST", "/api/users/remove", admin, form("id", samId)).status);

        // the last administrator can never be deleted, even by another administrator who is then the only one left
        String ada2 = cookieFor("ada2", "ada2-pass-123");
        checkEquals("one administrator deletes the other", 200,
                call("POST", "/api/users/remove", ada2, form("id", adaId)).status);
        check("exactly one administrator is left", directory.countByRole(Role.ADMINISTRATOR) == 1);
        checkEquals("the last administrator cannot be deleted (400)", 400,
                call("POST", "/api/users/remove", ada2, form("id", directory.findByUsername("ada2").getId())).status);
        check("still one administrator", directory.countByRole(Role.ADMINISTRATOR) == 1);
    }

    private static void savedTests() throws Exception {
        UserDirectory reloaded = new UserDirectory();
        repository.load(reloaded);

        check("added accounts are saved to the file", reloaded.findByUsername("nina") != null
                && reloaded.findByUsername("generated") != null);
        check("deleted accounts are gone from the file", reloaded.findByUsername("sam") == null
                && reloaded.findByUsername("ada") == null);
        check("a reset password is saved", reloaded.findByUsername("ben") != null
                && !reloaded.findByUsername("ben").getPasswordHash().equals(directory.findByUsername("dee").getPasswordHash()));
        check("the ids are never reused", reloaded.getLastIdNumber() >= directory.getLastIdNumber());
    }

    private static void registerTests() throws Exception {
        Reply ok = call("POST", "/api/register", null,
                form("name", "Rita Driver", "username", "Rita", "password", "rita-pass-123", "again", "rita-pass-123", "plate", "rit 777"));
        checkEquals("a visitor can register without signing in", 200, ok.status);
        check("they are a driver", ok.body.contains("\"role\":\"DRIVER\""));
        check("they are given only the self-service permission", ok.body.contains("\"permissions\":[\"SELF_SERVICE\"]"));
        check("the plate is saved in capitals", ok.body.contains("\"detail\":\"RIT 777\""));
        check("they are signed in straight away", ok.token() != null);
        checkEquals("the new session works", 200, call("GET", "/api/me", "sp_session=" + ok.token(), null).status);
        check("they cannot open the Users page", call("GET", "/api/users", "sp_session=" + ok.token(), null).status == 403);
        check("the password is not echoed back", !ok.body.contains("rita-pass-123"));
        check("the account is saved to the file", savedUsernames().contains("rita"));

        Reply tryAdmin = call("POST", "/api/register", null,
                form("name", "Wannabe Boss", "username", "wannabe", "password", "wannabe-pass-1", "again", "wannabe-pass-1",
                        "role", "ADMINISTRATOR", "detail", "DAY"));
        checkEquals("a registration that asks for another role still succeeds", 200, tryAdmin.status);
        check("but the role asked for is ignored: a driver", tryAdmin.body.contains("\"role\":\"DRIVER\""));
        check("and the account is a driver in the directory", directory.findByUsername("wannabe").getRole() == Role.DRIVER);

        checkEquals("a taken username is refused (400)", 400,
                call("POST", "/api/register", null, form("name", "Copy", "username", "rita", "password", "copy-pass-1234", "again", "copy-pass-1234")).status);
        checkEquals("passwords that differ are refused (400)", 400,
                call("POST", "/api/register", null, form("name", "Mix", "username", "mixed", "password", "mixed-pass-123", "again", "mixed-pass-124")).status);
        checkEquals("a short password is refused (400)", 400,
                call("POST", "/api/register", null, form("name", "Short", "username", "shorty", "password", "abc", "again", "abc")).status);
        checkEquals("a bad plate is refused (400)", 400,
                call("POST", "/api/register", null, form("name", "Plate", "username", "plated", "password", "plate-pass-1234", "again", "plate-pass-1234", "plate", "!!!")).status);
        checkEquals("GET is refused (405)", 405, call("GET", "/api/register", null, null).status);
        check("refused requests created nothing", directory.findByUsername("mixed") == null
                && directory.findByUsername("shorty") == null && directory.findByUsername("plated") == null);

        // five accounts from this address in the window are allowed (Rita and Wannabe are two of them)
        for (int i = 1; i <= 3; i++) {
            checkEquals("registration " + (i + 2) + " from this address is allowed", 200,
                    call("POST", "/api/register", null,
                            form("name", "Bulk " + i, "username", "bulk" + i, "password", "bulk-pass-123", "again", "bulk-pass-123")).status);
        }
        checkEquals("the sixth is turned away (429)", 429,
                call("POST", "/api/register", null, form("name", "Bulk 4", "username", "bulk4", "password", "bulk-pass-123", "again", "bulk-pass-123")).status);
        check("nothing was created by the refused one", directory.findByUsername("bulk4") == null);
        check("refused attempts that failed validation did not use up the allowance",
                directory.countByRole(Role.DRIVER) >= 5);
    }

    /* ---------- helpers ---------- */

    private static String savedUsernames() throws Exception {
        UserDirectory reloaded = new UserDirectory();
        repository.load(reloaded);
        StringBuilder sb = new StringBuilder();
        for (user.User u : reloaded.getUsers()) sb.append(u.getUsername()).append(" ");
        return sb.toString();
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String cookieFor(String username, String password) throws Exception {
        String cookie = cookieForOrNull(username, password);
        if (cookie == null) throw new IllegalStateException("could not sign in as " + username);
        return cookie;
    }

    private static String cookieForOrNull(String username, String password) throws Exception {
        Reply reply = call("POST", "/api/login", null, form("username", username, "password", password));
        return reply.token() == null ? null : "sp_session=" + reply.token();
    }

    private static String form(String... pairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
        }
        return sb.toString();
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

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name);
        }
    }

    private static void checkEquals(String name, Object expected, Object actual) {
        if (expected.equals(actual)) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name + "   (expected " + expected + ", got " + actual + ")");
        }
    }
}
