package br.com.javafinance.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** Explicitly invoked read-only probe. No Spring context, BankService, JDBC or persistence. */
public final class PluggyReadProbe {
    private PluggyReadProbe() {}

    public static void main(String[] args) throws Exception {
        var json = new ObjectMapper();
        var result = new LinkedHashMap<String,Object>();
        result.put("checkedAt", Instant.now().toString());
        result.put("writes", 0);
        long started = System.nanoTime();
        int exit = 1;
        try {
            if (args.length != 2) throw new IllegalArgumentException();
            LocalDate from = LocalDate.parse(args[0]), to = LocalDate.parse(args[1]);
            if (to.isBefore(from) || from.plusDays(89).isBefore(to)) throw new IllegalArgumentException();
            String clientId = required("PLUGGY_CLIENT_ID"), secret = required("PLUGGY_CLIENT_SECRET");
            String configuredItem = System.getenv("PLUGGY_PROBE_ITEM_ID");
            if (configuredItem == null || configuredItem.isBlank()) configuredItem = required("PLUGGY_ITEM_IDS");
            // Multiple configured items require an explicit choice; never query all of them.
            UUID item = UUID.fromString(configuredItem.strip());
            var config = new BankProperties();
            config.setClientId(clientId); config.setClientSecret(secret);
            // Local marker only: the probe never accesses an application user or database.
            config.setOwnerId(new UUID(0, 0)); config.setItemIds(Set.of(item));
            var client = new PluggyReadClient(config, json.findAndRegisterModules());
            var snapshot = client.accounts(item);
            if (snapshot.accounts().size() != 1) {
                result.put("status", "ACCOUNT_SELECTION_REQUIRED");
            } else {
                long accountsFinished = System.nanoTime();
                var entries = client.transactions(snapshot.accounts().getFirst().id(), from, to);
                result.put("status", "SUCCESS");
                result.put("endpoint", "/v2/transactions");
                result.put("from", from.toString()); result.put("to", to.toString());
                result.put("recordsValidated", entries.size());
                result.put("accountsDurationMs", (accountsFinished - started) / 1_000_000);
                result.put("transactionsDurationMs", (System.nanoTime() - accountsFinished) / 1_000_000);
                exit = 0;
            }
        } catch (BankReadException ex) {
            result.put("status", "PROVIDER_ERROR");
            result.put("stage", ex.stage.name()); result.put("code", ex.code.name());
            result.put("reference", ex.reference);
        } catch (RuntimeException ex) {
            // Even exception text can contain credentials or provider data. Never print it.
            result.put("status", "INVALID_INPUT_OR_UNEXPECTED_ERROR");
        }
        result.put("durationMs", (System.nanoTime() - started) / 1_000_000);
        System.out.println("PLUGGY_PROBE_RESULT=" + json.writeValueAsString(result));
        System.exit(exit);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException();
        return value.strip();
    }
}
