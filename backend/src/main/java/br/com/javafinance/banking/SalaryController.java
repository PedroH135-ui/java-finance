package br.com.javafinance.banking;
import br.com.javafinance.users.UserPrincipal;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/banking")
public class SalaryController {
    private final SalaryService service;
    public SalaryController(SalaryService service){this.service=service;}
    @PostMapping("/entries/{id}/salary") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void mark(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id,@Valid @RequestBody SalaryService.Mark input){service.mark(user.id(),id,input);}
    @GetMapping("/salary-rules") List<SalaryService.Rule> list(@AuthenticationPrincipal UserPrincipal user){return service.list(user.id());}
    @PutMapping("/salary-rules/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void toggle(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id,@Valid @RequestBody SalaryService.Toggle input){service.toggle(user.id(),id,input);}
}
