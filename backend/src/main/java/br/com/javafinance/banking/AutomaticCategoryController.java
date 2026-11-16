package br.com.javafinance.banking;

import br.com.javafinance.users.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/banking/category-automation")
public class AutomaticCategoryController {
    private final AutomaticCategoryService service;
    private final CloudCategoryJobs jobs;
    public AutomaticCategoryController(AutomaticCategoryService service,CloudCategoryJobs jobs){this.service=service;this.jobs=jobs;}
    @GetMapping AutomaticCategoryService.Status status(@AuthenticationPrincipal UserPrincipal user){return service.status(user.id());}
    public record Change(boolean enabled,@jakarta.validation.constraints.PositiveOrZero long version) {}
    @PostMapping void preference(@AuthenticationPrincipal UserPrincipal user,@jakarta.validation.Valid @RequestBody Change change){jobs.preference(user.id(),change.enabled(),change.version());}
    @PostMapping("/retry") void retry(@AuthenticationPrincipal UserPrincipal user){jobs.request(user.id());}
}
