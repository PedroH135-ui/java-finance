package br.com.javafinance.banking;
import br.com.javafinance.users.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/banking")
public class BankProductsController {
 private final BankProductsService service;
 public BankProductsController(BankProductsService service){this.service=service;}
 @GetMapping("/products") BankProductsService.Summary summary(@AuthenticationPrincipal UserPrincipal user){return service.summary(user.id());}
 @GetMapping("/card-charges/{id}") BankProductsService.Charge charge(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id){return service.charge(user.id(),id);}
 @PostMapping("/links/{id}/products/read") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 void read(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id,@Valid @RequestBody BankProductsService.Read input){service.read(user.id(),id,input);}
 @PostMapping("/links/{id}/products/pause") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 void pause(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id){service.pause(user.id(),id);}
 @PostMapping("/card-bills/{id}/reconcile") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 void reconcile(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id,@Valid @RequestBody BankProductsService.Reconcile input){service.reconcile(user.id(),id,input);}
 @PostMapping("/card-bills/{id}/undo") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 void undo(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id){service.undo(user.id(),id);}
 @PostMapping("/card-charges/{id}/category") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 void category(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id,@Valid @RequestBody BankProductsService.CategoryChange input){service.categorize(user.id(),id,input);}
}
