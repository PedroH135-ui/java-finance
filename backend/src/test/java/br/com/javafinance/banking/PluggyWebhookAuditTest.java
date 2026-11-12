package br.com.javafinance.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PluggyWebhookAuditTest {
    final ObjectMapper json = new ObjectMapper();
    @Test void activeWebhookMustMatchExactUrlEventAndSecretWithoutExposingValues() {
        var hook = Map.of("url", PluggyWebhookAudit.TARGET, "event", "item/updated", "headers", Map.of("x-javafinance-webhook", "private-value"));
        var body = json.valueToTree(Map.of("results", List.of(hook), "totalPages", 1));
        // disabledAt is explicitly required to establish that the subscription is active.
        ((com.fasterxml.jackson.databind.node.ObjectNode)body.path("results").get(0)).putNull("disabledAt");
        var result = PluggyWebhookAudit.inspect(body, "private-value");
        assertThat(result).containsEntry("status", "VERIFIED");
        assertThat(result.toString()).doesNotContain("private-value", "headers", "apiKey");
        assertThat(PluggyWebhookAudit.inspect(body, "different")).containsEntry("status", "AUTH_MISMATCH");
    }
    @Test void unrelatedHooksDuplicatesAndIncompleteListsDoNotCountAsVerified() {
        var unrelated = Map.of("url", "https://other.example/hook", "event", "item/updated");
        var matching = Map.of("url", PluggyWebhookAudit.TARGET, "event", "item/updated");
        assertThat(PluggyWebhookAudit.inspect(json.valueToTree(Map.of("results", List.of(unrelated), "totalPages", 1)), "secret")).containsEntry("status", "MISSING");
        assertThat(PluggyWebhookAudit.inspect(json.valueToTree(Map.of("results", List.of(), "totalPages", 0)), "secret")).containsEntry("status", "MISSING");
        assertThat(PluggyWebhookAudit.inspect(json.valueToTree(Map.of("results", List.of(matching, matching), "totalPages", 1)), "secret")).containsEntry("status", "AMBIGUOUS");
        assertThat(PluggyWebhookAudit.inspect(json.valueToTree(Map.of("results", List.of(matching), "totalPages", 2)), "secret")).containsEntry("status", "INCOMPLETE_LIST");
    }
}
