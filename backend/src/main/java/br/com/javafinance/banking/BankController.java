package br.com.javafinance.banking;

import br.com.javafinance.users.UserPrincipal;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/banking")
public class BankController {
    private final BankService service;
    public BankController(BankService service) { this.service=service; }
    @GetMapping("/config") BankService.Config config(@AuthenticationPrincipal UserPrincipal p) { return service.config(p.id()); }
    @GetMapping("/items/{item}/accounts") BankProvider.Snapshot discover(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID item) { return service.discover(p.id(),item); }
    @GetMapping("/links") List<BankService.Link> links(@AuthenticationPrincipal UserPrincipal p) { return service.links(p.id()); }
    @PostMapping("/links") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    void bind(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody BankService.Bind input) { service.bind(p.id(),input); }
    @PostMapping("/links/{id}/sync") Map<String,Integer> sync(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody BankService.Sync input) { return Map.of("read",service.sync(p.id(),id,input)); }
    @GetMapping("/links/{id}/entries") List<BankService.Entry> entries(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="false") boolean pending) { return service.entries(p.id(),id,page,pending); }
    @PostMapping("/entries/{id}/{action}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void review(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody BankService.Review input) { service.review(p.id(),id,action,input); }
    @PostMapping("/entries/{id}/visibility") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void visibility(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody BankService.Visibility input) { service.visibility(p.id(),id,input); }
    @PostMapping("/links/{id}/disconnect") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void disconnect(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id) { service.disconnect(p.id(),id); }
    @PostMapping("/links/{id}/reconnect") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void reconnect(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id) { service.reconnect(p.id(),id); }
}
