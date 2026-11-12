package api;

import com.sun.net.httpserver.HttpExchange;
import exception.AuthenticationException;
import security.PasswordHasher;
import service.AuthService;
import service.UserDirectory;
import storage.UserRepository;
import user.Permission;
import user.Role;
import user.User;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Managing accounts. An administrator lists, adds, resets and deletes accounts of any role.
 * Anyone can register, but only as a driver, and only a few times from one place.
 */
public class UserApi {
    public static final int REGISTRATIONS_PER_ADDRESS = 5;
    public static final int REGISTRATIONS_IN_TOTAL = 30;
    public static final Duration REGISTRATION_WINDOW = Duration.ofMinutes(10);

    private final AuthService auth;
    private final UserDirectory directory;
    private final UserRepository repository;
    private final AccessGuard guard;

    private final Map<String, Deque<Instant>> registrationsByAddress = new HashMap<>();
    private final Deque<Instant> registrations = new ArrayDeque<>();

    public UserApi(AuthService auth, UserDirectory directory, UserRepository repository, AccessGuard guard) {
        this.auth = auth;
        this.directory = directory;
        this.repository = repository;
        this.guard = guard;
    }

    public void register(WebServer server) {
        server.addRoute("/api/users", this::handleList);
        server.addRoute("/api/users/add", this::handleAdd);
        server.addRoute("/api/users/reset", this::handleReset);
        server.addRoute("/api/users/remove", this::handleRemove);
        server.addRoute("/api/register", this::handleRegister);
    }

    /* ---------- the administrator's addresses ---------- */

    private void handleList(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use GET for this address.");
            return;
        }
        StringBuilder list = new StringBuilder("[");
        synchronized (directory) {
            for (User u : directory.getUsers()) {
                if (list.length() > 1) list.append(",");
                list.append(userJson(u));
            }
        }
        sendJson(exchange, 200, "{\"users\":" + list.append("]") + "}");
    }

    private void handleAdd(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        Map<String, String> form = readForm(exchange);

        Role role;
        try {
            role = Role.parse(form.get("role"));
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
            return;
        }

        String password = form.getOrDefault("password", "");
        boolean generated = password.isEmpty();
        if (generated) password = PasswordHasher.generate(12);

        User created;
        try {
            synchronized (directory) {
                created = directory.create(role, form.getOrDefault("name", "").trim(),
                        form.getOrDefault("username", ""), password, form.getOrDefault("detail", ""));
                persist();
            }
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
            return;
        }

        sendJson(exchange, 200, "{\"user\":" + userJson(created)
                + (generated ? ",\"generatedPassword\":" + Json.quote(password) : "") + "}");
    }

    private void handleReset(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        User me = guard.userOf(exchange);
        Map<String, String> form = readForm(exchange);
        String id = form.getOrDefault("id", "");

        if (me != null && me.getId().equals(id)) {
            sendError(exchange, 400, "To change your own password, use the My account page.");
            return;
        }

        String password = form.getOrDefault("password", "");
        boolean generated = password.isEmpty();
        if (generated) password = PasswordHasher.generate(12);

        try {
            synchronized (directory) {
                auth.resetPassword(id, password);
                persist();
            }
        } catch (IllegalArgumentException e) {
            sendError(exchange, directory.findById(id) == null ? 404 : 400, e.getMessage());
            return;
        }
        sendJson(exchange, 200, "{\"ok\":true"
                + (generated ? ",\"generatedPassword\":" + Json.quote(password) : "") + "}");
    }

    private void handleRemove(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        User me = guard.userOf(exchange);
        String id = readForm(exchange).getOrDefault("id", "");

        if (me != null && me.getId().equals(id)) {
            sendError(exchange, 400, "You cannot delete your own account while you are signed in to it.");
            return;
        }

        try {
            synchronized (directory) {
                auth.removeUser(id);
                persist();
            }
        } catch (IllegalStateException e) {
            sendError(exchange, 400, e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            sendError(exchange, 404, e.getMessage());
            return;
        }
        sendJson(exchange, 200, "{\"ok\":true}");
    }

    /* ---------- driver self-registration ---------- */

    private void handleRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        Map<String, String> form = readForm(exchange);
        String address = exchange.getRemoteAddress().getAddress().getHostAddress();

        String password = form.getOrDefault("password", "");
        if (!password.equals(form.getOrDefault("again", password))) {
            sendError(exchange, 400, "The two passwords are not the same.");
            return;
        }

        User created;
        synchronized (directory) {
            if (!registrationAllowed(address, Instant.now())) {
                sendError(exchange, 429, "Too many accounts were created from here. Try again in a few minutes.");
                return;
            }
            try {
                // the role is never read from the request: self-registration can only make drivers
                created = directory.create(Role.DRIVER, form.getOrDefault("name", "").trim(),
                        form.getOrDefault("username", ""), password, form.getOrDefault("plate", ""));
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, e.getMessage());
                return;
            }
            recordRegistration(address, Instant.now());
            persist();
        }

        // sign the new driver straight in
        try {
            String token = auth.login(created.getUsername(), password);
            exchange.getResponseHeaders().add("Set-Cookie",
                    AccessGuard.COOKIE_NAME + "=" + token + "; Path=/; HttpOnly; SameSite=Strict");
        } catch (AuthenticationException e) {
            sendError(exchange, 500, "The account was created. Please sign in.");
            return;
        }
        sendJson(exchange, 200, userJson(created));
    }

    private boolean registrationAllowed(String address, Instant now) {
        Instant cutoff = now.minus(REGISTRATION_WINDOW);
        while (!registrations.isEmpty() && registrations.peekFirst().isBefore(cutoff)) registrations.pollFirst();
        if (registrations.size() >= REGISTRATIONS_IN_TOTAL) return false;

        Deque<Instant> mine = registrationsByAddress.get(address);
        if (mine == null) return true;
        while (!mine.isEmpty() && mine.peekFirst().isBefore(cutoff)) mine.pollFirst();
        if (mine.isEmpty()) registrationsByAddress.remove(address);
        return mine.size() < REGISTRATIONS_PER_ADDRESS;
    }

    private void recordRegistration(String address, Instant now) {
        registrations.addLast(now);
        registrationsByAddress.computeIfAbsent(address, a -> new ArrayDeque<>()).addLast(now);
    }

    /* ---------- building the answers ---------- */

    // never includes the password hash
    private String userJson(User user) {
        return "{"
                + "\"id\":" + Json.quote(user.getId())
                + ",\"name\":" + Json.quote(user.getName())
                + ",\"username\":" + Json.quote(user.getUsername())
                + ",\"role\":" + Json.quote(user.getRole().name())
                + ",\"detail\":" + Json.quote(user.getDetail())
                + ",\"permissions\":" + permissionsJson(user)
                + "}";
    }

    private String permissionsJson(User user) {
        StringBuilder sb = new StringBuilder("[");
        for (Permission p : user.getPermissions()) {
            if (sb.length() > 1) sb.append(",");
            sb.append(Json.quote(p.name()));
        }
        return sb.append("]").toString();
    }

    private void persist() {
        try {
            repository.save(directory);
        } catch (IOException e) {
            System.out.println("Warning: could not save the user accounts: " + e.getMessage());
        }
    }

    /* ---------- small helpers ---------- */

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(20_000);
        return parseForm(new String(bytes, StandardCharsets.UTF_8));
    }

    private Map<String, String> parseForm(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isEmpty()) return values;

        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, "{\"error\":" + Json.quote(message) + "}");
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
