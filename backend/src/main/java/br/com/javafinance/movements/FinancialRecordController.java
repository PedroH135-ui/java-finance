package br.com.javafinance.movements;

import br.com.javafinance.users.UserPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/movements/feed")
public class FinancialRecordController {
    private final FinancialRecordService service;
    public FinancialRecordController(FinancialRecordService service) { this.service=service; }
    @GetMapping FinancialRecordService.Page list(@AuthenticationPrincipal UserPrincipal p,@RequestParam LocalDate from,@RequestParam LocalDate to,
        @RequestParam(required=false) UUID account,@RequestParam(required=false) String category,@RequestParam(required=false) Movement.Kind kind,
        @RequestParam(required=false) Movement.Status status,@RequestParam(defaultValue="") String text,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="operationDate") String sort,
        @RequestParam(defaultValue="desc") String direction,@RequestParam(defaultValue="operationDate") String basis,
        @RequestParam(defaultValue="false") boolean hidden) {
        return service.list(p.id(),from,to,account,category,kind,status,text,page,size,sort,direction,basis,hidden);
    }
}
