package br.com.javafinance.movements;
import br.com.javafinance.users.UserPrincipal;
import br.com.javafinance.common.AuditService;
import static br.com.javafinance.movements.MovementDtos.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/movements")
public class MovementController {
    private final MovementService service; private final AuditService audit;
    public MovementController(MovementService service,AuditService audit) { this.service=service; this.audit=audit; }
    @GetMapping Page list(@AuthenticationPrincipal UserPrincipal p,@RequestParam LocalDate from,@RequestParam LocalDate to,
        @RequestParam(required=false) UUID account,@RequestParam(required=false) UUID category,@RequestParam(required=false) Movement.Kind kind,
        @RequestParam(required=false) Movement.Status status,@RequestParam(defaultValue="") String text,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="operationDate") String sort,
        @RequestParam(defaultValue="desc") String direction,@RequestParam(defaultValue="operationDate") String basis) { return service.list(p.id(),from,to,account,category,kind,status,text,page,size,sort,direction,basis); }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    View create(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody Create input) { return service.create(p.id(),input); }
    @GetMapping("/{id}") View get(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id) { return service.get(p.id(),id); }
    @GetMapping("/{id}/audit") List<Map<String,Object>> audit(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id) { service.get(p.id(),id); return audit.forResource(p.id(),id); }
    @PostMapping("/{id}/confirm") View confirm(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody Change input) { return service.confirm(p.id(),id,input); }
    @PostMapping("/{id}/cancel") View cancel(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody Change input) { return service.cancel(p.id(),id,input); }
    @PatchMapping("/{id}") View notes(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody NoteChange input) { return service.notes(p.id(),id,input); }
}

