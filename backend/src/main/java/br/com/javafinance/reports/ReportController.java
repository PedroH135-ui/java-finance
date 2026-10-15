package br.com.javafinance.reports;
import br.com.javafinance.common.ApiException;
import br.com.javafinance.users.UserPrincipal;
import java.time.YearMonth;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service=service; }
    @GetMapping("/summary") ReportService.Summary summary(@AuthenticationPrincipal UserPrincipal user,@RequestParam String month) {
        YearMonth period;
        try { period=YearMonth.parse(month); } catch(java.time.format.DateTimeParseException ex) { throw ApiException.bad("Use um mês no formato AAAA-MM."); }
        return service.summary(user.id(),period);
    }
}

