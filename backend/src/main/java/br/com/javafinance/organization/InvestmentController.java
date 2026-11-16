package br.com.javafinance.organization;
import br.com.javafinance.users.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/investments")
public class InvestmentController {
    private final InvestmentService service;
    public InvestmentController(InvestmentService service){this.service=service;}
    @GetMapping InvestmentService.Summary summary(@AuthenticationPrincipal UserPrincipal p){return service.summary(p.id());}
    @PostMapping void create(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody InvestmentService.Input input){service.save(p.id(),null,input);}
    @PatchMapping("/{id}") void edit(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody InvestmentService.Input input){service.save(p.id(),id,input);}
}
