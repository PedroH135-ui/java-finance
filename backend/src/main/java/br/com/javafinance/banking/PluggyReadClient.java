package br.com.javafinance.banking;

import br.com.javafinance.common.*;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Fixed host and read-only financial endpoints. No payments, item creation or refresh endpoints. */
@Component
public class PluggyReadClient implements BankProvider {
    private final BankProperties config;
    private final ObjectMapper json;
    private final HttpClient http;
    private String token;
    private Instant tokenExpiry = Instant.EPOCH;
    @org.springframework.beans.factory.annotation.Autowired
    public PluggyReadClient(BankProperties config, ObjectMapper mapper) {
        this(config, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    PluggyReadClient(BankProperties config, ObjectMapper mapper, HttpClient http) {
        this.config = config; this.json = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS); this.http = http;
    }
    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenExpiry)) return token;
        if (!config.ready()) throw ApiException.bad("Configure a conexão pessoal no servidor antes de consultar o banco.");
        try {
            var body = json.writeValueAsString(Map.of("clientId", config.getClientId(), "clientSecret", config.getClientSecret()));
            JsonNode result = exchange(HttpRequest.newBuilder(URI.create("https://api.pluggy.ai/auth")).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build());
            String key = result.path("apiKey").asText();
            if (key.isBlank()) throw unavailable();
            token = key; tokenExpiry = Instant.now().plusSeconds(6000); return token;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw unavailable(); }
    }
    private JsonNode get(String path) {
        // Only these fixed paths may reach the provider. Never accept a URL from a user or provider response.
        if (!(path.startsWith("/items/") || path.startsWith("/accounts?") || path.startsWith("/transactions?"))) throw new IllegalArgumentException("Read endpoint required");
        return exchange(HttpRequest.newBuilder(URI.create("https://api.pluggy.ai" + path)).timeout(Duration.ofSeconds(20))
            .header("X-API-KEY", token()).GET().build());
    }
    private JsonNode exchange(HttpRequest request) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (response.statusCode() == 401) { token = null; tokenExpiry = Instant.EPOCH; }
                    throw unavailable();
                }
                byte[] bytes = stream.readNBytes(2_000_001);
                if (bytes.length > 2_000_000) throw unavailable();
                JsonNode result=json.readTree(bytes);
                if(result==null) throw unavailable();
                return result;
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (java.io.IOException | IllegalArgumentException e) { throw unavailable(); }
    }
    @Override public Snapshot accounts(UUID item) {
        if (!config.getItemIds().contains(item)) throw ApiException.missing();
        try {
            JsonNode info = get("/items/" + item);
            if (!item.toString().equals(info.path("id").asText())) throw unavailable();
            if (!"UPDATED".equals(info.path("status").asText()))
                throw ApiException.bad("A conexão ainda não está atualizada. Confira a autorização no Meu Pluggy.");
            JsonNode result = get("/accounts?itemId=" + item + "&type=BANK").path("results");
            if (!result.isArray() || result.size() > 100) throw unavailable();
            List<RemoteAccount> accounts = new ArrayList<>();
            for (JsonNode a : result) {
                if (!item.toString().equals(a.path("itemId").asText())) throw unavailable();
                if (!"BANK".equals(a.path("type").asText()) || !"BRL".equals(a.path("currencyCode").asText())) continue;
                accounts.add(new RemoteAccount(UUID.fromString(a.path("id").asText()), text(a,"name",100), "BANK", "BRL", decimal(a,"balance",false)));
            }
            String updated = info.path("lastUpdatedAt").asText("");
            return new Snapshot(updated.isBlank() ? null : Instant.parse(updated), List.copyOf(accounts));
        } catch (IllegalArgumentException | DateTimeException e) { throw unavailable(); }
    }
    @Override public List<RemoteEntry> transactions(UUID account, LocalDate from, LocalDate to) {
        try {
            List<RemoteEntry> all = new ArrayList<>();
            long started=System.nanoTime();
            for (int page = 1; page <= 20; page++) {
                if(System.nanoTime()-started>60_000_000_000L) throw unavailable();
                JsonNode result = get("/transactions?accountId=" + account + "&from=" + from.minusDays(1) + "&to=" + to.plusDays(1) + "&pageSize=100&page=" + page);
                JsonNode entries = result.path("results"); int pages = result.path("totalPages").asInt(-1);
                if (!entries.isArray() || entries.size() > 100 || pages < 0 || pages > 20) throw ApiException.bad("Consulta grande ou incompleta. Consulte um intervalo menor (máximo de 2.000 lançamentos).");
                for (JsonNode entry : entries) {
                    if (!account.toString().equals(entry.path("accountId").asText()) || !"BRL".equals(entry.path("currencyCode").asText())) throw unavailable();
                    LocalDate date = LocalDate.parse(entry.path("date").asText().substring(0,10));
                    if (date.isBefore(from) || date.isAfter(to)) continue;
                    String direction = entry.path("type").asText(), status = entry.path("status").asText();
                    if (!Set.of("DEBIT","CREDIT").contains(direction) || !Set.of("POSTED","PENDING").contains(status)) throw unavailable();
                    all.add(new RemoteEntry(text(entry,"id",200), text(entry,"description",200), decimal(entry,"amount",true), date, direction, status));
                }
                if (page >= pages) return List.copyOf(all);
            }
            throw unavailable();
        } catch (IllegalArgumentException | DateTimeException | IndexOutOfBoundsException e) { throw unavailable(); }
    }
    private static String text(JsonNode node, String key, int max) {
        if(!node.path(key).isTextual()) throw unavailable();
        String value = node.path(key).asText("").strip(); if (value.isBlank() || value.length() > max) throw unavailable(); return value;
    }
    private static BigDecimal decimal(JsonNode node, String key, boolean positive) {
        if (!node.path(key).isNumber()) throw unavailable();
        BigDecimal value = node.get(key).decimalValue(); if (positive) value = value.abs();
        try { value = Money.parse(value.stripTrailingZeros().toPlainString()); } catch (ApiException e) { throw unavailable(); }
        if (positive && value.signum() <= 0) throw unavailable(); return value;
    }
    private static ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "Não foi possível consultar o Meu Pluggy. Confira as credenciais, a autorização e tente novamente. Nenhum lançamento foi confirmado.");
    }
}
