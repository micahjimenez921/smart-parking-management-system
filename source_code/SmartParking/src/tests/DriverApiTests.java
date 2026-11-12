package tests;

import api.AccessGuard;
import api.AuthApi;
import api.DriverApi;
import api.ParkingApi;
import api.WebServer;
import app.DemoData;
import gate.GateState;
import model.Ticket;
import model.VehicleFactory;
import security.AccessPolicy;
import security.PasswordHasher;
import security.SessionManager;
import service.AuthService;
import service.ParkingLot;
import service.UserDirectory;
import storage.FileParkingRepository;
import storage.FileUserRepository;
import user.Driver;
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
import java.time.LocalDateTime;

/**
 * Plain Java tests for the driver's own addresses: seeing only their own vehicles, paying, and managing plate numbers.
 * They start a real server on a free port and talk to it over HTTP. Run this class and read the PASS / FAIL lines.
 */
public class DriverApiTests {
    private static int passed = 0;
    private static int failed = 0;

    private static HttpClient client;
    private static String base;

    private static ParkingLot lot;
    private static FileUserRepository userRepository;
    private static Ticket deeCar;
    private static Ticket deeVan;
    private static Ticket eveCar;
    private static Ticket staffCar;

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
        Path folder = Files.createTempDirectory("smartpark-driver-test");
        Path web = Files.createDirectories(folder.resolve("web"));
        Files.writeString(web.resolve("index.html"), "<html>app</html>");

        lot = DemoData.createEmptyLot();
        FileParkingRepository lotRepository = new FileParkingRepository(folder.resolve("lot.txt"));

        deeCar = lot.parkVehicle(VehicleFactory.create("CAR", "ABC 123", "Dee Driver"), "REGULAR");
        deeVan = lot.parkVehicle(VehicleFactory.create("TRUCK", "VAN-777", "Dee Driver"), "REGULAR");
        eveCar = lot.parkVehicle(VehicleFactory.create("CAR", "EVE 555", "Eve Secret"), "REGULAR");
        staffCar = lot.parkVehicle(VehicleFactory.create("CAR", "QQQ 999", "Walk In"), "REGULAR");

        UserDirectory directory = new UserDirectory();
        directory.create(Role.ADMINISTRATOR, "Ada Admin", "ada", "admin-pass-1", "");
        directory.create(Role.ATTENDANT, "Ben Attendant", "ben", "attendant-pass-1", "DAY");
        directory.create(Role.DRIVER, "Dee Driver", "dee", "driver-pass-1", "ABC 123;VAN-777");
        directory.create(Role.DRIVER, "Eve Driver", "eve", "driver-pass-2", "EVE 555");
        directory.create(Role.DRIVER, "New Driver", "newbie", "driver-pass-3", "");
        userRepository = new FileUserRepository(folder.resolve("users.txt"));

        AuthService auth = new AuthService(directory, new SessionManager(Duration.ofMinutes(30)));
        AccessGuard guard = new AccessGuard(auth, AccessPolicy.standard());

        WebServer server = new WebServer(0, web);
        server.setGuard(guard);
        new AuthApi(auth, directory, userRepository, guard).register(server);
        new ParkingApi(lot, lotRepository).register(server);
        new DriverApi(lot, lotRepository, directory, userRepository, guard).register(server);
        server.start();

        client = HttpClient.newHttpClient();
        base = "http://127.0.0.1:" + server.getPort();

        try {
            runSection("Who may use the driver addresses", () -> accessTests());
            runSection("A driver sees only their own vehicles", () -> seeingTests());
            runSection("Paying", () -> payingTests());
            runSection("Plate numbers", () -> plateTests(directory));
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
        String admin = cookieFor("ada", "admin-pass-1");
        String attendant = cookieFor("ben", "attendant-pass-1");
        String dee = cookieFor("dee", "driver-pass-1");

        checkEquals("signed-out visitors get 401", 401, call("GET", "/api/my/ticket", null, null).status);
        checkEquals("an attendant is not allowed (403)", 403, call("GET", "/api/my/ticket", attendant, null).status);
        checkEquals("an administrator is not allowed either (403)", 403, call("GET", "/api/my/ticket", admin, null).status);
        checkEquals("an attendant cannot pay as a driver", 403,
                call("POST", "/api/my/pay", attendant, form("ticketId", deeCar.getTicketId(), "method", "CARD", "detail", "4111111111111111")).status);
        checkEquals("an attendant cannot add a plate", 403, call("POST", "/api/my/plates/add", attendant, "plate=AAA111").status);
        checkEquals("a driver can open their page data", 200, call("GET", "/api/my/ticket", dee, null).status);
        checkEquals("paying with GET is not allowed", 405, call("GET", "/api/my/pay", dee, null).status);
        checkEquals("adding a plate with GET is not allowed", 405, call("GET", "/api/my/plates/add", dee, null).status);
        checkEquals("a driver is still kept out of the staff ticket list", 403, call("GET", "/api/tickets", dee, null).status);
    }

    private static void seeingTests() throws Exception {
        Reply dee = call("GET", "/api/my/ticket", cookieFor("dee", "driver-pass-1"), null);
        check("Dee sees her car", dee.body.contains("\"plate\":\"ABC 123\""));
        check("Dee sees her van", dee.body.contains("\"plate\":\"VAN-777\""));
        check("Dee does not see Eve's car", !dee.body.contains("EVE 555"));
        check("Dee does not see the walk-in car", !dee.body.contains("QQQ 999"));
        check("the ticket shows the slot and the fee so far", dee.body.contains("\"slotId\":\"R-") && dee.body.contains("\"fee\":"));
        check("the owner's name is never sent", !dee.body.contains("Eve Secret") && !dee.body.contains("Walk In"));
        check("the answer lists Dee's plate numbers", dee.body.contains("\"plates\":[\"ABC 123\",\"VAN-777\"]"));

        Reply eve = call("GET", "/api/my/ticket", cookieFor("eve", "driver-pass-2"), null);
        check("Eve sees only her own car", eve.body.contains("EVE 555") && !eve.body.contains("ABC 123") && !eve.body.contains("VAN-777"));

        Reply newbie = call("GET", "/api/my/ticket", cookieFor("newbie", "driver-pass-3"), null);
        check("a driver with no plates sees no tickets", newbie.body.contains("\"tickets\":[]"));
    }

    private static void payingTests() throws Exception {
        String dee = cookieFor("dee", "driver-pass-1");
        String eve = cookieFor("eve", "driver-pass-2");
        int before = lot.getReceipts().size();

        checkEquals("cash is refused for drivers", 400,
                call("POST", "/api/my/pay", dee, form("ticketId", deeCar.getTicketId(), "method", "CASH", "detail", "500")).status);
        checkEquals("no payment method is refused", 400,
                call("POST", "/api/my/pay", dee, form("ticketId", deeCar.getTicketId(), "method", "", "detail", "")).status);
        checkEquals("a card number that is too short is refused", 400,
                call("POST", "/api/my/pay", dee, form("ticketId", deeCar.getTicketId(), "method", "CARD", "detail", "1234")).status);
        check("after those refusals the car is still parked", stillParked(deeCar));

        Reply stranger = call("POST", "/api/my/pay", dee, form("ticketId", eveCar.getTicketId(), "method", "CARD", "detail", "4111111111111111"));
        checkEquals("Dee cannot pay Eve's ticket (it looks like it does not exist)", 404, stranger.status);
        check("Eve's car is still parked", stillParked(eveCar));
        Reply walkIn = call("POST", "/api/my/pay", dee, form("ticketId", staffCar.getTicketId(), "method", "CARD", "detail", "4111111111111111"));
        checkEquals("Dee cannot pay the walk-in's ticket", 404, walkIn.status);
        check("the walk-in car is still parked", stillParked(staffCar));
        checkEquals("an unknown ticket is a 404", 404,
                call("POST", "/api/my/pay", dee, form("ticketId", "T-999", "method", "CARD", "detail", "4111111111111111")).status);
        checkEquals("no receipt was written by any of the refused payments", before, lot.getReceipts().size());

        Reply paid = call("POST", "/api/my/pay", dee, form("ticketId", deeCar.getTicketId(), "method", "CARD", "detail", "4111 1111 1111 1111"));
        checkEquals("Dee pays her own ticket by card", 200, paid.status);
        check("the receipt shows the vehicle and the method", paid.body.contains("\"plate\":\"ABC 123\"") && paid.body.contains("\"method\":\"CARD\""));
        check("the car is no longer parked", !stillParked(deeCar));
        check("the exit gate opened for her plate", lot.getExitGate().getState(LocalDateTime.now()) == GateState.OPEN
                && "ABC 123".equals(lot.getExitGate().getOpenedFor(LocalDateTime.now())));
        check("a receipt was written", lot.getReceipts().size() == before + 1);

        Reply after = call("GET", "/api/my/ticket", dee, null);
        String parkedPart = after.body.substring(0, after.body.indexOf("\"payments\":"));
        check("the paid ticket is gone from her parked list", !parkedPart.contains("\"ticketId\":\"" + deeCar.getTicketId() + "\""));
        check("her other vehicle is still listed", after.body.contains("VAN-777"));
        check("the payment shows in her recent payments", after.body.contains("\"payments\":[{\"ticketId\":\"" + deeCar.getTicketId() + "\""));
        checkEquals("paying the same ticket twice is refused", 404,
                call("POST", "/api/my/pay", dee, form("ticketId", deeCar.getTicketId(), "method", "CARD", "detail", "4111111111111111")).status);

        Reply eveSees = call("GET", "/api/my/ticket", eve, null);
        check("Dee's payment does not show in Eve's history", !eveSees.body.contains("ABC 123"));
        checkEquals("Eve pays online with an 11 digit mobile number", 200,
                call("POST", "/api/my/pay", eve, form("ticketId", eveCar.getTicketId(), "method", "ONLINE", "detail", "09171234567")).status);
        checkEquals("a mobile number with the wrong length is refused", 400,
                call("POST", "/api/my/pay", dee, form("ticketId", deeVan.getTicketId(), "method", "ONLINE", "detail", "0917")).status);
    }

    private static void plateTests(UserDirectory directory) throws Exception {
        String newbie = cookieFor("newbie", "driver-pass-3");

        Reply added = call("POST", "/api/my/plates/add", newbie, "plate=new+111");
        checkEquals("a driver can add a plate", 200, added.status);
        check("the answer lists it in capitals", added.body.contains("\"plates\":[\"NEW 111\"]"));
        checkEquals("the same plate with other spacing is not added twice", 200, call("POST", "/api/my/plates/add", newbie, "plate=new-111").status);
        check("still only one plate", call("GET", "/api/my/ticket", newbie, null).body.contains("\"plates\":[\"NEW 111\"]"));

        checkEquals("a plate with strange characters is refused", 400, call("POST", "/api/my/plates/add", newbie, "plate=%21%21%21").status);
        checkEquals("an empty plate is refused", 400, call("POST", "/api/my/plates/add", newbie, "plate=").status);

        UserDirectory reloaded = new UserDirectory();
        userRepository.load(reloaded);
        check("the new plate was saved to the users file",
                ((Driver) reloaded.findByUsername("newbie")).ownsPlate("NEW-111"));

        // a plate that is added shows its parked vehicle
        lot.parkVehicle(VehicleFactory.create("CAR", "NEW 111", "Somebody"), "REGULAR");
        check("adding a plate makes its parked vehicle appear", call("GET", "/api/my/ticket", newbie, null).body.contains("NEW 111"));

        Reply removed = call("POST", "/api/my/plates/remove", newbie, "plate=new111");
        checkEquals("a driver can remove a plate (spacing does not matter)", 200, removed.status);
        check("the plate list is empty again", removed.body.contains("\"plates\":[]"));
        check("the vehicle disappears from the page", call("GET", "/api/my/ticket", newbie, null).body.contains("\"tickets\":[]"));
        checkEquals("removing a plate that is not on the account is a 404", 404, call("POST", "/api/my/plates/remove", newbie, "plate=NOPE1").status);
        reloaded = new UserDirectory();
        userRepository.load(reloaded);
        check("the removal was saved too", !((Driver) reloaded.findByUsername("newbie")).ownsPlate("NEW 111"));

        for (int i = 1; i <= 10; i++) {
            call("POST", "/api/my/plates/add", newbie, "plate=LIM" + i + "0");
        }
        checkEquals("an eleventh plate is refused", 400, call("POST", "/api/my/plates/add", newbie, "plate=LIM110").status);
    }

    /* ---------- talking to the server ---------- */

    private static boolean stillParked(Ticket ticket) {
        return lot.getActiveTickets().contains(ticket);
    }

    private static String cookieFor(String username, String password) throws Exception {
        Reply reply = call("POST", "/api/login", null, form("username", username, "password", password));
        if (reply.token() == null) throw new IllegalStateException("could not sign in as " + username);
        return "sp_session=" + reply.token();
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

    /* ---------- tiny test helpers ---------- */

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
