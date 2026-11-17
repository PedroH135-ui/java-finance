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
    @GetMapping InvestmentService.Summary summary(@AuthenticationPrincipal UserPrincipal p,@RequestParam(required=false) java.time.YearMonth month){
        if(month==null)return service.summary(p.id());
        if(month.getYear()<1900||month.getYear()>2200)throw br.com.javafinance.common.ApiException.bad("Escolha um mês entre 1900 e 2200.");
        return service.summary(p.id(),month.atDay(1),month.atEndOfMonth());
    }
    @GetMapping("/positions") java.util.List<InvestmentService.Position> positions(@AuthenticationPrincipal UserPrincipal p){return service.positions(p.id());}
    @PostMapping void create(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody InvestmentService.Input input){service.save(p.id(),null,input);}
    @PatchMapping("/{id}") void edit(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody InvestmentService.Input input){service.save(p.id(),id,input);}
}
