package tests;

import api.AccessGuard;
import api.AuthApi;
import api.DriverApi;
import api.NotificationApi;
import api.ParkingApi;
import api.ReservationApi;
import api.UserApi;
import api.WebServer;
import app.DemoData;
import exception.ParkingFullException;
import model.Reservation;
import model.Ticket;
import model.VehicleFactory;
import security.AccessPolicy;
import security.SessionManager;
import service.AuthService;
import service.ParkingLot;
import service.UserDirectory;
import storage.FileParkingRepository;
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
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain Java tests for slot reservations (the rules, saving, and the addresses) and for the notification bell.
 * The HTTP parts start a real server on a free port. Run this class and read the PASS / FAIL lines.
 */
public class ReservationTests {
    private static int passed = 0;
    private static int failed = 0;

    private static HttpClient client;
    private static String base;
    private static ParkingLot lot;
    private static FileParkingRepository lotRepository;
    private static Path folder;

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
        folder = Files.createTempDirectory("smartpark-reservation-test");
        Path web = Files.createDirectories(folder.resolve("web"));
        Files.writeString(web.resolve("index.html"), "<html>app</html>");

        lot = DemoData.createEmptyLot();
        lotRepository = new FileParkingRepository(folder.resolve("lot.txt"));

        UserDirectory directory = new UserDirectory();
        directory.create(Role.ADMINISTRATOR, "Ada Admin", "ada", "admin-pass-1", "");
        directory.create(Role.ATTENDANT, "Ben Attendant", "ben", "attendant-pass-1", "DAY");
        directory.create(Role.DRIVER, "Dee Driver", "dee", "driver-pass-1", "ABC 123;VAN-777");
        directory.create(Role.DRIVER, "Eve Driver", "eve", "driver-pass-2", "EVE 555");
        FileUserRepository userRepository = new FileUserRepository(folder.resolve("users.txt"));

        AuthService auth = new AuthService(directory, new SessionManager(Duration.ofMinutes(30)));
        AccessGuard guard = new AccessGuard(auth, AccessPolicy.standard());

        WebServer server = new WebServer(0, web);
        server.setGuard(guard);
        new AuthApi(auth, directory, userRepository, guard).register(server);
        UserApi userApi = new UserApi(auth, directory, userRepository, guard);
        userApi.setEventLog(lot.getEvents());
        userApi.register(server);
        new ParkingApi(lot, lotRepository).register(server);
        new DriverApi(lot, lotRepository, directory, userRepository, guard).register(server);
        new ReservationApi(lot, lotRepository, guard).register(server);
        new NotificationApi(lot, guard).register(server);
        server.start();

        client = HttpClient.newHttpClient();
        base = "http://127.0.0.1:" + server.getPort();

        try {
            runSection("Reservation rules", () -> ruleTests());
            runSection("Expiry", () -> expiryTests());
            runSection("Saving and loading", () -> savingTests());
            runSection("Who may use the reservation addresses", () -> accessTests());
            runSection("A driver reserves", () -> driverTests());
            runSection("Staff see and cancel reservations", () -> staffTests());
            runSection("Parking a vehicle that has a reservation", () -> arrivalTests());
            runSection("Notifications", () -> notificationTests());
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

    /** Puts the lot back to empty, with no reservations. */
    private static void resetLot() {
        for (Reservation r : lot.getReservations()) lot.cancelReservation(r.getId(), false);
        for (Ticket t : lot.getActiveTickets()) {
            try {
                lot.exitVehicle(t.getTicketId());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /* ---------- the groups ---------- */

    private static void ruleTests() throws Exception {
        resetLot();
        int before = lot.getAvailableCount();

        Reservation r = lot.reserve("U-001", "abc 123", "REGULAR");
        check("a reservation holds a slot of the chosen type", r.getSlot().getSlotType().equals("REGULAR"));
        check("the plate is saved in capitals", r.getPlateNumber().equals("ABC 123"));
        checkEquals("it is held for the set time", (long) ParkingLot.RESERVATION_MINUTES,
                Duration.between(r.getCreatedAt(), r.getExpiresAt()).toMinutes());
        checkEquals("the free count drops by one", before - 1, lot.getAvailableCount());
        checkEquals("the reserved count is one", 1, lot.getReservedCount());
        check("the slot counts as reserved", lot.isReserved(r.getSlot()));
        check("the slot is still physically empty", r.getSlot().isAvailable());
        check("the next free slot given out is a different one", lot.findAvailableSlot("REGULAR") != r.getSlot());

        // reserve every other regular slot, then nobody else can park in the reserved one
        lot.reserve("U-002", "B2", "REGULAR");
        lot.reserve("U-002", "B3", "REGULAR");
        lot.reserve("U-002", "B4", "REGULAR");
        check("with all regular slots held, none is free for strangers", lot.findAvailableSlot("REGULAR") == null);
        boolean refused = false;
        try {
            lot.parkVehicle(VehicleFactory.create("CAR", "ZZZ 999", "Stranger"), "REGULAR");
        } catch (ParkingFullException e) {
            refused = true;
        }
        check("a stranger cannot park in a held slot", refused);
        boolean full = false;
        try {
            lot.reserve("U-003", "B5", "REGULAR");
        } catch (ParkingFullException e) {
            full = true;
        }
        check("a type with no free slot cannot be reserved", full);

        boolean twice = false;
        try {
            lot.reserve("U-003", "abc-123", "VIP");
        } catch (IllegalStateException e) {
            twice = e.getMessage().contains("already has a reservation");
        }
        check("a plate cannot hold two reservations", twice);

        lot.parkVehicle(VehicleFactory.create("CAR", "PARKED 1", "Someone"), "VIP");
        boolean parked = false;
        try {
            lot.reserve("U-003", "parked 1", "VIP");
        } catch (IllegalStateException e) {
            parked = e.getMessage().contains("already parked");
        }
        check("a parked plate cannot reserve", parked);

        boolean badPlate = false;
        try {
            lot.reserve("U-003", "!!", "VIP");
        } catch (IllegalArgumentException e) {
            badPlate = true;
        }
        check("a bad plate number is refused", badPlate);

        boolean slotGuard = false;
        try {
            lot.removeSlot(r.getSlot().getSlotId());
        } catch (IllegalStateException e) {
            slotGuard = e.getMessage().contains("reserved");
        }
        check("a reserved slot cannot be removed", slotGuard);

        lot.cancelReservation(r.getId(), false);
        check("cancelling frees the slot", !lot.isReserved(r.getSlot()));
        boolean missing = false;
        try {
            lot.cancelReservation("R-999", false);
        } catch (IllegalArgumentException e) {
            missing = true;
        }
        check("cancelling an unknown reservation is refused", missing);
        resetLot();
    }

    private static void expiryTests() throws Exception {
        resetLot();
        LocalDateTime longAgo = LocalDateTime.now().minusMinutes(ParkingLot.RESERVATION_MINUTES + 10);
        Reservation old = lot.reserve("U-001", "OLD 111", "VIP", longAgo);
        Reservation fresh = lot.reserve("U-001", "NEW 222", "VIP");
        check("a reservation past its time is gone the next time anything looks", lot.findReservation(old.getId()) == null);
        check("its slot was handed out again", fresh.getSlot() == old.getSlot());
        check("a running reservation is untouched", lot.findReservation(fresh.getId()) != null);
        checkEquals("only one reservation is left", 1, lot.getReservations().size());
        check("the plate of an expired reservation can reserve again", lot.reserve("U-001", "OLD 111", "REGULAR") != null);
        resetLot();
    }

    private static void savingTests() throws Exception {
        resetLot();
        Reservation running = lot.reserve("U-007", "SAVE 1", "HANDICAPPED");
        lot.reserve("U-007", "GONE 2", "VIP", LocalDateTime.now().minusMinutes(90));
        lotRepository.save(lot);

        ParkingLot loaded = DemoData.createEmptyLot();
        lotRepository.load(loaded);
        Reservation back = loaded.findReservation(running.getId());
        check("a running reservation is loaded back", back != null);
        check("with the same plate, owner and slot", back != null && back.getPlateNumber().equals("SAVE 1")
                && back.getOwnerId().equals("U-007") && back.getSlot().getSlotId().equals(running.getSlot().getSlotId()));
        check("and the same end time", back != null && back.getExpiresAt().equals(running.getExpiresAt()));
        check("an expired one is not loaded", loaded.getReservations().size() == 1);
        check("its slot stays held after loading", back != null && loaded.isReserved(back.getSlot()));
        check("the counter carries on, so ids are never reused", loaded.getReservationCounter() >= lot.getReservationCounter());
        resetLot();
    }

    private static void accessTests() throws Exception {
        resetLot();
        String ben = cookieFor("ben", "attendant-pass-1");
        String dee = cookieFor("dee", "driver-pass-1");
        String ada = cookieFor("ada", "admin-pass-1");

        checkEquals("signed-out visitors get 401 on the driver list", 401, call("GET", "/api/my/reservations", null, null).status);
        checkEquals("an attendant cannot use the driver list (403)", 403, call("GET", "/api/my/reservations", ben, null).status);
        checkEquals("an administrator cannot reserve as a driver (403)", 403,
                call("POST", "/api/my/reserve", ada, form("plate", "ABC 123", "slotType", "REGULAR")).status);
        checkEquals("a driver cannot see the staff list (403)", 403, call("GET", "/api/reservations", dee, null).status);
        checkEquals("a driver cannot cancel as staff (403)", 403, call("POST", "/api/reservations/cancel", dee, form("id", "R-001")).status);
        checkEquals("signed-out visitors get 401 on the staff list", 401, call("GET", "/api/reservations", null, null).status);
        checkEquals("an attendant sees the staff list", 200, call("GET", "/api/reservations", ben, null).status);
        checkEquals("an administrator sees the staff list", 200, call("GET", "/api/reservations", ada, null).status);
        checkEquals("GET is refused on reserve (405)", 405, call("GET", "/api/my/reserve", dee, null).status);
        checkEquals("POST is refused on the driver list (405)", 405, call("POST", "/api/my/reservations", dee, "").status);
    }

    private static void driverTests() throws Exception {
        resetLot();
        String dee = cookieFor("dee", "driver-pass-1");
        String eve = cookieFor("eve", "driver-pass-2");

        String options = call("GET", "/api/my/reservations", dee, null).body;
        check("the options list every slot type with its free count", options.contains("\"type\":\"REGULAR\"")
                && options.contains("\"free\":4") && options.contains("\"type\":\"VIP\""));
        check("the driver's plates are listed", options.contains("ABC 123") && options.contains("VAN-777"));
        check("the hold time and the limit are listed", options.contains("\"holdMinutes\":30") && options.contains("\"max\":2"));

        Reply ok = call("POST", "/api/my/reserve", dee, form("plate", "abc123", "slotType", "REGULAR"));
        checkEquals("a driver reserves for their own plate", 200, ok.status);
        check("the reservation names the plate as saved on the account", ok.body.contains("\"plate\":\"ABC 123\""));
        check("it names a slot and the minutes left", ok.body.contains("\"slotId\":\"R-") && ok.body.contains("\"minutesLeft\":30"));
        String id = field(ok.body, "id");

        String list = call("GET", "/api/my/reservations", dee, null).body;
        check("it shows in the driver's list", list.contains("\"reservations\":[{\"id\":\"" + id));
        check("another driver does not see it", call("GET", "/api/my/reservations", eve, null).body.contains("\"reservations\":[]"));
        check("the free count in the options dropped", list.contains("\"free\":3"));

        checkEquals("the same plate cannot reserve twice (409)", 409,
                call("POST", "/api/my/reserve", dee, form("plate", "ABC 123", "slotType", "VIP")).status);
        checkEquals("a plate that is not on the account is refused (400)", 400,
                call("POST", "/api/my/reserve", dee, form("plate", "EVE 555", "slotType", "REGULAR")).status);
        checkEquals("an unknown slot type is refused (400)", 400,
                call("POST", "/api/my/reserve", dee, form("plate", "VAN-777", "slotType", "ROCKET")).status);
        checkEquals("the second reservation works", 200,
                call("POST", "/api/my/reserve", dee, form("plate", "VAN-777", "slotType", "VIP")).status);

        directoryAddPlate("dee", "THIRD 3");
        checkEquals("a third running reservation is refused (409)", 409,
                call("POST", "/api/my/reserve", dee, form("plate", "THIRD 3", "slotType", "REGULAR")).status);

        checkEquals("one driver cannot cancel another's reservation (404)", 404,
                call("POST", "/api/my/reserve/cancel", eve, form("id", id)).status);
        check("it is still there", call("GET", "/api/my/reservations", dee, null).body.contains(id));
        checkEquals("the owner cancels it", 200, call("POST", "/api/my/reserve/cancel", dee, form("id", id)).status);
        check("it is gone", !call("GET", "/api/my/reservations", dee, null).body.contains("\"id\":\"" + id));
        checkEquals("cancelling it again is a 404", 404, call("POST", "/api/my/reserve/cancel", dee, form("id", id)).status);
        check("the changes were saved to the data file", Files.readString(folder.resolve("lot.txt")).contains("RESERVATION"));
        resetLot();
    }

    private static void directoryAddPlate(String username, String plate) throws Exception {
        // the driver adds a plate through the real address, like the page does
        String cookie = cookieFor(username, username.equals("dee") ? "driver-pass-1" : "driver-pass-2");
        checkEquals("(setup) the plate " + plate + " is added", 200,
                call("POST", "/api/my/plates/add", cookie, form("plate", plate)).status);
    }

    private static void staffTests() throws Exception {
        resetLot();
        String ben = cookieFor("ben", "attendant-pass-1");
        String ada = cookieFor("ada", "admin-pass-1");
        String eve = cookieFor("eve", "driver-pass-2");

        Reply made = call("POST", "/api/my/reserve", eve, form("plate", "EVE 555", "slotType", "VIP"));
        String id = field(made.body, "id");

        String list = call("GET", "/api/reservations", ben, null).body;
        check("staff see the reservation with its plate and slot", list.contains(id) && list.contains("EVE 555") && list.contains("\"slotId\":\"V-"));

        String slots = call("GET", "/api/slots", ben, null).body;
        check("the map marks the slot as reserved, not free", slots.contains("\"reserved\":true") && slots.contains("\"reservedFor\":\"EVE 555\""));
        String stats = call("GET", "/api/stats", ben, null).body;
        check("the stats count it", stats.contains("\"reserved\":1") && stats.contains("\"available\":7") && stats.contains("\"occupied\":0"));

        checkEquals("an attendant cancels it", 200, call("POST", "/api/reservations/cancel", ben, form("id", id)).status);
        check("the driver hears about it", call("GET", "/api/notifications", eve, null).body.contains("Reservation cancelled"));
        checkEquals("cancelling an unknown one is a 404", 404, call("POST", "/api/reservations/cancel", ada, form("id", "R-999")).status);

        Reply again = call("POST", "/api/my/reserve", eve, form("plate", "EVE 555", "slotType", "VIP"));
        String id2 = field(again.body, "id");
        checkEquals("an administrator can cancel too", 200, call("POST", "/api/reservations/cancel", ada, form("id", id2)).status);
        resetLot();
    }

    private static void arrivalTests() throws Exception {
        resetLot();
        String ben = cookieFor("ben", "attendant-pass-1");
        String dee = cookieFor("dee", "driver-pass-1");

        Reply made = call("POST", "/api/my/reserve", dee, form("plate", "ABC 123", "slotType", "VIP"));
        String held = field(made.body, "slotId");

        Reply parked = call("POST", "/api/park", ben,
                form("plate", "abc 123", "owner", "Dee Driver", "vehicleType", "CAR", "slotType", "REGULAR"));
        checkEquals("the attendant parks the reserving vehicle", 200, parked.status);
        check("it goes to the reserved slot, even though another type was picked", parked.body.contains("\"slotId\":\"" + held + "\""));
        check("the answer says it used a reservation", parked.body.contains("\"fromReservation\":true"));
        checkEquals("the reservation is used up", 0, lot.getReservations().size());
        check("the driver is welcomed", call("GET", "/api/notifications", dee, null).body.contains("Welcome"));

        Reply plain = call("POST", "/api/park", ben,
                form("plate", "NOBOOK 1", "owner", "Walk In", "vehicleType", "CAR", "slotType", "REGULAR"));
        check("a vehicle without a reservation does not say so", plain.status == 200 && !plain.body.contains("fromReservation"));
        resetLot();
    }

    private static void notificationTests() throws Exception {
        resetLot();
        String ben = cookieFor("ben", "attendant-pass-1");
        String ada = cookieFor("ada", "admin-pass-1");
        String dee = cookieFor("dee", "driver-pass-1");
        String eve = cookieFor("eve", "driver-pass-2");

        checkEquals("signed-out visitors get 401", 401, call("GET", "/api/notifications", null, null).status);
        checkEquals("POST is refused (405)", 405, call("POST", "/api/notifications", ben, "").status);

        // a driver reserves: staff hear about it, the other driver does not
        call("POST", "/api/my/reserve", dee, form("plate", "ABC 123", "slotType", "REGULAR"));
        String staffView = call("GET", "/api/notifications", ben, null).body;
        check("staff hear that a slot was reserved", staffView.contains("Slot reserved") && staffView.contains("ABC 123"));
        check("administrators hear it too", call("GET", "/api/notifications", ada, null).body.contains("Slot reserved"));
        check("a driver does not see staff events", !call("GET", "/api/notifications", eve, null).body.contains("Slot reserved"));

        // the lot filling up
        lot.parkVehicle(VehicleFactory.create("CAR", "FILL 1", "A"), "HANDICAPPED");
        lot.parkVehicle(VehicleFactory.create("CAR", "FILL 2", "B"), "HANDICAPPED");
        lot.parkVehicle(VehicleFactory.create("CAR", "FILL 3", "C"), "REGULAR");
        lot.parkVehicle(VehicleFactory.create("CAR", "FILL 4", "D"), "REGULAR");
        staffView = call("GET", "/api/notifications", ben, null).body;
        check("staff are warned when a type is full", staffView.contains("Handicapped slots are full") && staffView.contains("\"level\":\"error\""));
        check("and when a type is almost full (held slots count as taken)", staffView.contains("Regular slots are almost full"));
        check("a driver gets no lot warnings", !call("GET", "/api/notifications", dee, null).body.contains("almost full"));
        String errorFirst = staffView.substring(staffView.indexOf("\"items\":[") + 9);
        check("problems are listed first", errorFirst.startsWith("{\"id\":\"live:full:"));

        // a long stay
        lot.restoreTicket(new Ticket("T-900", VehicleFactory.create("CAR", "LONG 1", "E"),
                lot.findSlot("V-01"), LocalDateTime.now().minusHours(14)));
        check("staff are warned about a long stay", call("GET", "/api/notifications", ben, null).body.contains("Long stay"));

        // a reservation that is about to run out, and one that already did
        resetLot();
        lot.reserve("U-003", "ABC 123", "REGULAR", LocalDateTime.now().minusMinutes(ParkingLot.RESERVATION_MINUTES - 3));
        check("staff are told a reservation is running out", call("GET", "/api/notifications", ben, null).body.contains("Reservation running out"));
        String ownerView = call("GET", "/api/notifications", ben, null).body;
        check("each notification has an id, a level, a title, a text and a time", ownerView.contains("\"id\":")
                && ownerView.contains("\"level\":") && ownerView.contains("\"title\":") && ownerView.contains("\"text\":") && ownerView.contains("\"time\":"));

        // a driver whose own reservation expired
        String deeId = idOf("dee");
        resetLot();
        lot.reserve(deeId, "ABC 123", "REGULAR", LocalDateTime.now().minusMinutes(ParkingLot.RESERVATION_MINUTES + 5));
        String afterExpiry = call("GET", "/api/notifications", dee, null).body;
        check("the owner is told the reservation expired", afterExpiry.contains("Reservation expired") && afterExpiry.contains("ran out"));
        check("another driver is not", !call("GET", "/api/notifications", eve, null).body.contains("Reservation expired"));
        check("staff are told too", call("GET", "/api/notifications", ben, null).body.contains("Reservation expired"));

        lot.reserve(deeId, "VAN-777", "VIP", LocalDateTime.now().minusMinutes(ParkingLot.RESERVATION_MINUTES - 2));
        check("the owner gets a warning when it is about to run out",
                call("GET", "/api/notifications", dee, null).body.contains("Your reservation is running out"));
        check("another driver does not", !call("GET", "/api/notifications", eve, null).body.contains("Your reservation is running out"));

        // a new driver account: administrators only
        Reply register = call("POST", "/api/register", null,
                form("name", "Rita Newcomer", "username", "rita", "password", "rita-pass-123", "again", "rita-pass-123"));
        check("a new driver account is announced to administrators",
                register.status == 200 && call("GET", "/api/notifications", ada, null).body.contains("New driver account"));
        check("but not to attendants", !call("GET", "/api/notifications", ben, null).body.contains("New driver account"));

        // a driver pays online
        resetLot();
        lot.parkVehicle(VehicleFactory.create("CAR", "EVE 555", "Eve"), "REGULAR");
        Ticket eveCar = lot.findTicketByPlate("EVE 555");
        Reply paid = call("POST", "/api/my/pay", eve, form("ticketId", eveCar.getTicketId(), "method", "CARD", "detail", "4111111111111111"));
        checkEquals("(setup) the driver pays", 200, paid.status);
        check("staff are told about the payment", call("GET", "/api/notifications", ben, null).body.contains("Online payment"));
        check("the driver gets a receipt notice", call("GET", "/api/notifications", eve, null).body.contains("Payment received"));
        resetLot();
    }

    /* ---------- helpers ---------- */

    private static String idOf(String username) throws Exception {
        String cookie = cookieFor(username, username.equals("dee") ? "driver-pass-1" : "driver-pass-2");
        return field(call("GET", "/api/me", cookie, null).body, "id");
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
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
