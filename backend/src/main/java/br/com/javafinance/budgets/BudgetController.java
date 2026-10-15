package br.com.javafinance.budgets;

import br.com.javafinance.users.UserPrincipal;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetService service;
    public BudgetController(BudgetService service) { this.service = service; }
    @GetMapping BudgetService.Month month(@AuthenticationPrincipal UserPrincipal p, @RequestParam YearMonth month) {
        return service.month(p.id(), month);
    }
    @PutMapping("/{month}/categories/{category}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void save(@AuthenticationPrincipal UserPrincipal p, @PathVariable YearMonth month, @PathVariable UUID category, @Valid @RequestBody BudgetService.Save input) {
        service.save(p.id(), month, category, input);
    }
    @PostMapping("/{month}/copy")
    BudgetService.CopyResult copy(@AuthenticationPrincipal UserPrincipal p, @PathVariable YearMonth month, @Valid @RequestBody BudgetService.Copy input) {
        return service.copy(p.id(), month, input);
    }
}
