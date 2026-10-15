package br.com.javafinance.accounts;
import br.com.javafinance.common.*;
import br.com.javafinance.movements.LedgerService;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AccountService {
    public record Create(@NotBlank @Size(max=100) String name, @Size(max=100) String institution,
        @NotNull Account.Kind kind, @NotBlank String openingBalance, @NotNull LocalDate openingDate) {}
    public record Edit(@NotBlank @Size(max=100) String name, @Size(max=100) String institution,
        boolean archived, long version) {}
    public record View(UUID id,String name,String institution,Account.Kind kind,String currency,
        LocalDate openingDate,boolean archived,long version,BigDecimal balance) {}
    private final AccountRepository accounts; private final LedgerService ledger; private final Clock clock; private final AuditService audit;
    public AccountService(AccountRepository accounts, LedgerService ledger, Clock clock, AuditService audit) {
        this.accounts=accounts; this.ledger=ledger; this.clock=clock; this.audit=audit;
    }
    @Transactional(readOnly=true) public List<View> list(UUID owner) { return accounts.findByOwnerIdOrderByNameAsc(owner).stream().map(this::view).toList(); }
    @Transactional public View create(UUID owner, Create input) {
        if(input.openingDate().isAfter(LocalDate.now(clock))) throw ApiException.bad("O saldo inicial não pode ter data futura.");
        var balance=Money.parse(input.openingBalance());
        Account a=new Account(); a.id=UUID.randomUUID(); a.ownerId=owner; a.name=input.name().strip();
        a.institution=input.institution()==null?"":input.institution().strip(); a.kind=input.kind(); a.openingDate=input.openingDate();
        accounts.saveAndFlush(a); ledger.opening(a,balance);
        audit.record(owner,a.id,"ACCOUNT_CREATED","Conta e saldo inicial registrados.");
        return view(a);
    }
    @Transactional public View edit(UUID owner,UUID id,Edit input) {
        Account a=accounts.lock(id,owner).orElseThrow(ApiException::missing);
        if(a.version!=input.version()) throw ApiException.conflict("A conta mudou. Atualize a página.");
        a.name=input.name().strip(); a.institution=input.institution()==null?"":input.institution().strip(); a.archived=input.archived();
        accounts.flush(); audit.record(owner,id,"ACCOUNT_UPDATED",a.archived?"Conta arquivada.":"Dados da conta atualizados.");
        return view(a);
    }
    private View view(Account a) {
        return new View(a.id,a.name,a.institution,a.kind,a.currency,a.openingDate,a.archived,a.version,ledger.balance(a.ownerId,a.id,LocalDate.now(clock)));
    }
}

