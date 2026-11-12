package api;

import com.sun.net.httpserver.HttpExchange;
import exception.AuthenticationException;
import service.AuthService;
import service.UserDirectory;
import storage.UserRepository;
import user.Permission;
import user.User;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Signing in and out, who am I, and changing your own password. */
public class AuthApi {
    private final AuthService auth;
    private final UserDirectory directory;
    private final UserRepository repository;
    private final AccessGuard guard;

    public AuthApi(AuthService auth, UserDirectory directory, UserRepository repository, AccessGuard guard) {
        this.auth = auth;
        this.directory = directory;
        this.repository = repository;
        this.guard = guard;
    }

    public void register(WebServer server) {
        server.addRoute("/api/login", this::handleLogin);
        server.addRoute("/api/logout", this::handleLogout);
        server.addRoute("/api/me", this::handleMe);
        server.addRoute("/api/password", this::handlePassword);
        server.addRoute("/api/profile", this::handleProfile);
    }

    private void persist() {
        try {
            repository.save(directory);
        } catch (IOException e) {
            System.out.println("Warning: could not save the user accounts: " + e.getMessage());
        }
    }

    /* ---------- addresses ---------- */

    private void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        Map<String, String> form = readForm(exchange);
        String username = form.getOrDefault("username", "").trim();
        String password = form.getOrDefault("password", "");

        try {
            String token = auth.login(username, password);
            User user = auth.userFor(token);
            exchange.getResponseHeaders().add("Set-Cookie", sessionCookie(token));
            sendJson(exchange, 200, userJson(user));
        } catch (AuthenticationException e) {
            sendError(exchange, 401, e.getMessage());
        }
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        auth.logout(guard.tokenOf(exchange));
        exchange.getResponseHeaders().add("Set-Cookie", endedCookie());
        sendJson(exchange, 200, "{\"ok\":true}");
    }

    private void handleMe(HttpExchange exchange) throws IOException {
        User user = guard.userOf(exchange);
        if (user == null) {
            sendError(exchange, 401, "Please sign in.");
            return;
        }
        sendJson(exchange, 200, userJson(user));
    }

    private void handlePassword(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        User user = guard.userOf(exchange);
        if (user == null) {
            sendError(exchange, 401, "Please sign in.");
            return;
        }

        Map<String, String> form = readForm(exchange);
        String current = form.getOrDefault("current", "");
        String wanted = form.getOrDefault("newPassword", "");

        if (wanted.equals(current)) {
            sendError(exchange, 400, "Choose a password that is different from the current one.");
            return;
        }

        // a wrong current password is a 400, not a 401, so the browser stays on the page and shows the message
        try {
            auth.changePassword(user, current, wanted, guard.tokenOf(exchange));
            persist();
            sendJson(exchange, 200, "{\"ok\":true}");
        } catch (AuthenticationException | IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    /** Changes the signed-in person's own name. */
    private void handleProfile(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this address.");
            return;
        }
        User user = guard.userOf(exchange);
        if (user == null) {
            sendError(exchange, 401, "Please sign in.");
            return;
        }
        try {
            user.setName(readForm(exchange).getOrDefault("name", ""));
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
            return;
        }
        persist();
        sendJson(exchange, 200, userJson(user));
    }

    /* ---------- building the answers ---------- */

    private String userJson(User user) {
        StringBuilder permissions = new StringBuilder("[");
        for (Permission p : user.getPermissions()) {
            if (permissions.length() > 1) permissions.append(",");
            permissions.append(Json.quote(p.name()));
        }
        permissions.append("]");

        return "{"
                + "\"id\":" + Json.quote(user.getId())
                + ",\"name\":" + Json.quote(user.getName())
                + ",\"username\":" + Json.quote(user.getUsername())
                + ",\"role\":" + Json.quote(user.getRole().name())
                + ",\"detail\":" + Json.quote(user.getDetail())
                + ",\"permissions\":" + permissions
                + "}";
    }

    /** The cookie is hidden from page scripts (HttpOnly) and is not sent from other websites (SameSite). */
    private String sessionCookie(String token) {
        return AccessGuard.COOKIE_NAME + "=" + token + "; Path=/; HttpOnly; SameSite=Strict";
    }

    private String endedCookie() {
        return AccessGuard.COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict";
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
