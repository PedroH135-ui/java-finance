package br.com.javafinance.banking;

import br.com.javafinance.common.ApiException;
import br.com.javafinance.movements.FinancialRecordService;
import br.com.javafinance.users.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/banking/category-groups")
public class CategoryGroupController {
    public record Member(@NotNull UUID id,@PositiveOrZero long version) {}
    public record Review(@NotNull UUID categoryId,@NotNull @Size(min=2,max=100) List<@NotNull @Valid Member> entries) {}
    private final FinancialRecordService records;
    private final BankAssistanceService categories;
    public CategoryGroupController(FinancialRecordService records,BankAssistanceService categories) {
        this.records=records;this.categories=categories;
    }

    @PostMapping @Transactional
    void apply(@AuthenticationPrincipal UserPrincipal user,@Valid @RequestBody Review review) {
        var entries=review.entries().stream().sorted(Comparator.comparing(Member::id)).toList();
        Set<UUID> ids=new HashSet<>();
        Set<List<?>> keys=new HashSet<>();
        categories.lockEntries(user.id(),entries.stream().map(Member::id).toList());
        for(var entry:entries) {
            if(!ids.add(entry.id()))throw ApiException.bad("Registro repetido.");
            var r=records.entry(user.id(),entry.id());
            if(!Objects.equals(r.bankEntryVersion(),entry.version()) || r.hidden() || r.categoryId()!=null ||
                r.suggestedNature()!=null || r.financialNature()!=null || r.categorySource()!=null ||
                !"POSTED".equals(r.bankProviderStatus()) || !"BANK".equals(r.source()))
                throw ApiException.conflict("O grupo mudou. Confira os registros novamente.");
            keys.add(List.of(r.accountId(),r.kind(),CategoryAdvisor.memoryKey(r.description())));
        }
        if(keys.size()!=1)throw ApiException.bad("Selecione registros da mesma conta, descrição e direção.");
        for(var entry:entries) {
            long current=records.entry(user.id(),entry.id()).bankEntryVersion();
            categories.category(user.id(),entry.id(),new BankAssistanceService.CategoryChange(current,review.categoryId()));
        }
    }
}
