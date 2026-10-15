package br.com.javafinance.banking;

import br.com.javafinance.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class BankAutomationController {
    private final BankProperties config; private final BankAutoSync sync; private final ObjectMapper mapper;
    public BankAutomationController(BankProperties config,BankAutoSync sync,ObjectMapper mapper) { this.config=config; this.sync=sync; this.mapper=mapper; }
    @PostMapping("/api/banking/hooks/pluggy")
    ResponseEntity<Void> webhook(HttpServletRequest request) throws IOException {
        authorize(request.getHeader("X-JavaFinance-Webhook"),config.getWebhookSecret());
        byte[] bytes=request.getInputStream().readNBytes(8193);
        if(bytes.length>8192) return ResponseEntity.status(413).build();
        UUID item,eventId;
        try {
            var body=mapper.readTree(bytes);
            if(body==null || !body.isObject()) throw new IllegalArgumentException();
            if(!"item/updated".equals(body.path("event").asText())) return ResponseEntity.noContent().build();
            item=UUID.fromString(body.path("itemId").asText()); eventId=UUID.fromString(body.path("eventId").asText());
        } catch(IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw ApiException.bad("Notificação inválida.");
        }
        sync.enqueue(item,"pluggy:"+eventId);
        // Work remains durable if delivery times out or the container is interrupted; retries are idempotent.
        return result(sync.process(item));
    }
    @GetMapping("/api/banking/jobs/daily")
    ResponseEntity<Void> daily(HttpServletRequest request) {
        authorize(request.getHeader("Authorization"),"Bearer "+config.getCronSecret());
        return result(sync.daily());
    }
    private void authorize(String received,String expected) {
        if(!config.automationReady() || received==null || received.length()>256 || !MessageDigest.isEqual(received.getBytes(StandardCharsets.UTF_8),expected.getBytes(StandardCharsets.UTF_8)))
            throw new ApiException(HttpStatus.UNAUTHORIZED,"Notificação não autorizada.");
    }
    private static ResponseEntity<Void> result(boolean done) {
        return done ? ResponseEntity.noContent().build() : ResponseEntity.status(503).header("Retry-After","300").build();
    }
}
