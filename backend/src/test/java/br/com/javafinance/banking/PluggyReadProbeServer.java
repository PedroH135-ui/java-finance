package br.com.javafinance.banking;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Temporary protected deployment only. Starts no Spring application and has no persistence. */
public final class PluggyReadProbeServer {
    private static final AtomicBoolean USED = new AtomicBoolean();
    private static volatile String result = "Leitura ainda nao executada.";

    public static void main(String[] args) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"))), 0);
        server.createContext("/", exchange -> {
            if (!exchange.getRequestURI().getPath().equals("/")) { send(exchange, 404, "Nao encontrado."); return; }
            if (!exchange.getRequestMethod().equals("GET")) { send(exchange, 405, "Metodo nao permitido."); return; }
            send(exchange, 200, "<!doctype html><meta charset=utf-8><title>Teste isolado Pluggy v2</title>"
                + "<h1>Teste isolado Pluggy v2</h1><p>15/10/2026 a 12/11/2026. Sem banco de dados, importacao ou alteracao de saldo.</p>"
                + "<form method=post action=/probe><button" + (USED.get() ? " disabled" : "")
                + ">Executar leitura uma vez</button></form><pre>" + escape(result) + "</pre>");
        });
        server.createContext("/probe", exchange -> {
            if (!exchange.getRequestURI().toString().equals("/probe") || !exchange.getRequestMethod().equals("POST")) {
                send(exchange, 405, "Metodo nao permitido."); return;
            }
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (origin == null || host == null || !sameOrigin(origin, host)) { send(exchange, 403, "Origem recusada."); return; }
            if (USED.compareAndSet(false, true)) result = runOnce();
            send(exchange, 200, "<!doctype html><meta charset=utf-8><title>Resultado Pluggy v2</title>"
                + "<h1>Resultado da leitura isolada</h1><pre>" + escape(result) + "</pre><p>Sem importacao e sem persistencia.</p>");
        });
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    static boolean sameOrigin(String origin, String host) {
        try {
            URI uri = URI.create(origin);
            return "https".equals(uri.getScheme()) && uri.getRawUserInfo() == null && uri.getPort() == -1
                && host.equalsIgnoreCase(uri.getHost()) && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException ex) { return false; }
    }

    private static String runOnce() {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("java.class.path"), PluggyReadProbe.class.getName(), "2026-10-15", "2026-11-12");
        // The child process cannot receive database, webhook, application or Vercel credentials.
        var variables = new HashMap<>(process.environment());
        process.environment().clear();
        for (String key : List.of("PLUGGY_CLIENT_ID", "PLUGGY_CLIENT_SECRET", "PLUGGY_ITEM_IDS")) {
            if (variables.containsKey(key)) process.environment().put(key, variables.get(key));
        }
        variables.clear();
        Process child = null;
        try {
            child = process.start();
            if (!child.waitFor(100, TimeUnit.SECONDS)) { child.destroyForcibly(); return "{\"status\":\"TIMEOUT\",\"writes\":0}"; }
            String stdout = new String(child.getInputStream().readNBytes(16384), StandardCharsets.UTF_8);
            var lines = stdout.lines().filter(line -> line.startsWith("PLUGGY_PROBE_RESULT=")).toList();
            if (lines.size() == 1) return lines.getFirst().substring("PLUGGY_PROBE_RESULT=".length());
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            // Never return command errors, raw responses or stack traces.
        } finally {
            process.environment().clear();
            if (child != null && child.isAlive()) child.destroyForcibly();
        }
        return "{\"status\":\"PROBE_ERROR\",\"writes\":0}";
    }

    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    private static void send(HttpExchange exchange, int status, String body) throws java.io.IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
    }
}
