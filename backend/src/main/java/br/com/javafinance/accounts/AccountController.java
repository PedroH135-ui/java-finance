package br.com.javafinance.accounts;
import br.com.javafinance.users.UserPrincipal;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service=service; }
    @GetMapping List<AccountService.View> list(@AuthenticationPrincipal UserPrincipal p) { return service.list(p.id()); }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    AccountService.View create(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody AccountService.Create input) { return service.create(p.id(),input); }
    @PatchMapping("/{id}") AccountService.View edit(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody AccountService.Edit input) { return service.edit(p.id(),id,input); }
}

