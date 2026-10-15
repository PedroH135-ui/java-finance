package br.com.javafinance.categories;
import br.com.javafinance.users.UserPrincipal;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service=service; }
    @GetMapping List<CategoryService.View> list(@AuthenticationPrincipal UserPrincipal p) { return service.list(p.id()); }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    CategoryService.View create(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody CategoryService.Create input) { return service.create(p.id(),input); }
    @PatchMapping("/{id}") CategoryService.View edit(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody CategoryService.Edit input) { return service.edit(p.id(),id,input); }
}

