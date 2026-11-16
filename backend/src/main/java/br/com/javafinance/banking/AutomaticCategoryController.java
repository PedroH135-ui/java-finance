package br.com.javafinance.banking;

import br.com.javafinance.users.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/banking/category-automation")
public class AutomaticCategoryController {
    private final AutomaticCategoryService service;
    public AutomaticCategoryController(AutomaticCategoryService service){this.service=service;}
    @GetMapping AutomaticCategoryService.Status status(@AuthenticationPrincipal UserPrincipal user){return service.status(user.id());}
}
