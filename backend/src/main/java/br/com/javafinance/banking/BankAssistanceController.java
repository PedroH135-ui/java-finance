package br.com.javafinance.banking;

import br.com.javafinance.users.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/banking/entries/{id}")
public class BankAssistanceController {
    private final BankAssistanceService service;
    public BankAssistanceController(BankAssistanceService service) {this.service=service;}
    @GetMapping("/assistance") BankAssistanceService.Assistance assistance(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id) {return service.assistance(p.id(),id);}
    @PostMapping("/category") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void category(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody BankAssistanceService.CategoryChange input) {service.category(p.id(),id,input);}
    @PostMapping("/transfer") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void transfer(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody BankAssistanceService.TransferChange input) {service.confirmTransfer(p.id(),id,input);}
    @PostMapping("/transfer/undo") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void undo(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody BankAssistanceService.UndoTransfer input) {service.undoTransfer(p.id(),id,input);}
}
