package br.com.javafinance.banking;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/** Explicit maintenance tool: authentication and webhook metadata only, never financial data or JDBC. */
public final class PluggyWebhookAudit {
    static final String TARGET = "https://java-finance.vercel.app/api/banking/hooks/pluggy";
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Map<String,Object> result;
        try {
            var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
            String id = required("PLUGGY_CLIENT_ID"), secret = required("PLUGGY_CLIENT_SECRET");
            String expected = required("PLUGGY_WEBHOOK_SECRET");
            if (expected.length() < 32) throw new IllegalArgumentException();
            var auth = request(http, HttpRequest.newBuilder(URI.create("https://api.pluggy.ai/auth"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("clientId", id, "clientSecret", secret)))));
            String token = auth.path("apiKey").asText();
            if (token.isBlank()) throw new IllegalArgumentException();
            var hooks = request(http, HttpRequest.newBuilder(URI.create("https://api.pluggy.ai/webhooks"))
                .header("X-API-KEY", token).GET());
            result = inspect(hooks, expected);
        } catch (SafeHttpException ex) {
            result = Map.of("status", "HTTP_ERROR", "httpStatus", ex.status);
        } catch (Exception ex) {
            result = Map.of("status", "AUDIT_FAILED");
        }
        System.out.println("PLUGGY_WEBHOOK_RESULT=" + JSON.writeValueAsString(result));
    }

    static Map<String,Object> inspect(JsonNode response, String expected) {
        JsonNode hooks = response.path("results");
        int pages = response.path("totalPages").asInt(-1);
        if (!hooks.isArray() || hooks.size() > 500 || (pages != 1 && !(pages == 0 && hooks.isEmpty())))
            return Map.of("status", "INCOMPLETE_LIST");
        var matching = new ArrayList<JsonNode>();
        int otherEvents = 0;
        for (JsonNode hook : hooks) {
            if (!TARGET.equals(hook.path("url").asText())) continue;
            if ("item/updated".equals(hook.path("event").asText())) matching.add(hook);
            else otherEvents++;
        }
        var result = new LinkedHashMap<String,Object>();
        result.put("matchingSubscriptions", matching.size());
        result.put("otherEventsAtEndpoint", otherEvents);
        if (matching.isEmpty()) { result.put("status", "MISSING"); return result; }
        if (matching.size() > 1) { result.put("status", "AMBIGUOUS"); return result; }
        var hook = matching.getFirst();
        boolean active = hook.path("disabledAt").isNull();
        var headerValues = new ArrayList<String>();
        hook.path("headers").fields().forEachRemaining(entry -> {
            if ("X-JavaFinance-Webhook".equalsIgnoreCase(entry.getKey()) && entry.getValue().isTextual()) headerValues.add(entry.getValue().textValue());
        });
        boolean correct = headerValues.size() == 1 && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), headerValues.getFirst().getBytes(StandardCharsets.UTF_8));
        result.put("active", active);
        result.put("authenticationMatches", correct);
        result.put("status", !active ? "DISABLED" : !correct ? "AUTH_MISMATCH" : "VERIFIED");
        return result;
    }

    private static JsonNode request(HttpClient http, HttpRequest.Builder request) throws Exception {
        var response = http.send(request.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var stream = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new SafeHttpException(response.statusCode());
            if (response.statusCode() == 204) {
                var empty = JSON.createObjectNode().put("totalPages", 0);
                empty.putArray("results");
                return empty;
            }
            byte[] body = stream.readNBytes(1_000_001);
            if (body.length > 1_000_000) throw new IllegalArgumentException();
            return JSON.readTree(body);
        }
    }
    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException();
        return value;
    }
    private static final class SafeHttpException extends Exception {
        final int status;
        SafeHttpException(int status) { this.status = status; }
    }
}
