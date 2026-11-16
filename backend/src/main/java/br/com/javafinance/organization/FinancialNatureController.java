package br.com.javafinance.organization;
import br.com.javafinance.users.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/banking/entries/{id}/nature")
public class FinancialNatureController {
    private final FinancialNatureService service;
    public FinancialNatureController(FinancialNatureService service){this.service=service;}
    @GetMapping FinancialNatureService.Status status(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id){return service.status(p.id(),id);}
    @PostMapping void confirm(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody FinancialNatureService.Change input){service.confirm(p.id(),id,input);}
}
