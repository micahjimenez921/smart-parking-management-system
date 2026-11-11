package api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import security.AccessPolicy;
import security.AccessPolicy.Decision;
import service.AuthService;
import user.User;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Checks every API request before it is handled: who is signed in (from the session cookie),
 * and whether the access rules allow that person to use the address.
 */
public class AccessGuard {
    public static final String COOKIE_NAME = "sp_session";

    private final AuthService auth;
    private final AccessPolicy policy;

    public AccessGuard(AuthService auth, AccessPolicy policy) {
        this.auth = auth;
        this.policy = policy;
    }

    /** The session token in the request's cookie, or null if there is none. */
    public String tokenOf(HttpExchange exchange) {
        List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null) return null;
        for (String header : headers) {
            for (String part : header.split(";")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && pair[0].equals(COOKIE_NAME)) return pair[1];
            }
        }
        return null;
    }

    /** The signed-in user, or null if the cookie is missing, unknown, or expired. */
    public User userOf(HttpExchange exchange) {
        return auth.userFor(tokenOf(exchange));
    }

    /** Wraps a handler so it only runs when the access rules allow the request. */
    public HttpHandler protect(HttpHandler handler) {
        return exchange -> {
            String path = exchange.getRequestURI().getPath();
            Decision decision = policy.check(path, userOf(exchange));

            if (decision == Decision.ALLOWED) {
                handler.handle(exchange);
            } else if (decision == Decision.UNAUTHENTICATED) {
                sendError(exchange, 401, "Please sign in.");
            } else {
                sendError(exchange, 403, "Your account is not allowed to do this.");
            }
        };
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = ("{\"error\":" + Json.quote(message) + "}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
