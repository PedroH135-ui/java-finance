package br.com.javafinance.movements;
import br.com.javafinance.accounts.Account;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class LedgerService {
    private final JdbcTemplate jdbc; private final MovementRepository movements; private final Clock clock;
    public LedgerService(JdbcTemplate jdbc,MovementRepository movements,Clock clock) { this.jdbc=jdbc; this.movements=movements; this.clock=clock; }
    public void opening(Account account,BigDecimal amount) {
        Movement m=new Movement(); m.id=UUID.randomUUID(); m.ownerId=account.ownerId;
        m.description="Saldo inicial — "+account.name; m.amount=amount; m.kind=Movement.Kind.OPENING; m.status=Movement.Status.CONFIRMED;
        m.accountId=account.id; m.operationDate=account.openingDate; m.competence=account.openingDate.withDayOfMonth(1);
        m.dueDate=account.openingDate; m.settledDate=account.openingDate; m.requestKey=UUID.randomUUID(); m.createdAt=clock.instant();
        movements.saveAndFlush(m); post(m);
    }
    public void post(Movement m) {
        BigDecimal sourceAmount = (m.kind==Movement.Kind.EXPENSE || m.kind==Movement.Kind.TRANSFER)?m.amount.negate():m.amount;
        add(m,m.accountId,sourceAmount);
        if(m.kind==Movement.Kind.TRANSFER) add(m,m.destinationId,m.amount);
    }
    private void add(Movement m,UUID account,BigDecimal amount) {
        jdbc.update("INSERT INTO posting(id,owner_id,movement_id,account_id,amount) VALUES (?,?,?,?,?)",UUID.randomUUID(),m.ownerId,m.id,account,amount);
    }
    public BigDecimal balance(UUID owner,UUID account,LocalDate through) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(p.amount),0)::numeric(19,2)
            FROM posting p JOIN movement m ON m.id=p.movement_id AND m.owner_id=p.owner_id
            WHERE p.owner_id=? AND p.account_id=? AND m.status='CONFIRMED' AND m.settled_date<=?
            """,BigDecimal.class,owner,account,through);
    }
}

