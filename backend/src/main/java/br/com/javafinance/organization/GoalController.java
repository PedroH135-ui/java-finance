package br.com.javafinance.organization;
import br.com.javafinance.users.UserPrincipal;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/goals")
public class GoalController {
    private final GoalService service;
    private final PlanningFinanceService finances;
    public GoalController(GoalService service,PlanningFinanceService finances){this.service=service;this.finances=finances;}
    @GetMapping List<GoalService.Goal> list(@AuthenticationPrincipal UserPrincipal p){return service.list(p.id());}
    @GetMapping("/finances") PlanningFinanceService.Context finances(@AuthenticationPrincipal UserPrincipal p){return finances.context(p.id());}
    @PutMapping("/finances") void finances(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody PlanningFinanceService.Input input){finances.save(p.id(),input);}
    @PostMapping void save(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody GoalService.Save input){service.save(p.id(),input);}
    @PostMapping("/chat") GoalService.Reply chat(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody GoalService.Chat input){return service.chat(p.id(),input);}
    @PatchMapping("/{id}") void progress(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody GoalService.Progress input){service.progress(p.id(),id,input);}
}
