package br.com.javafinance.budgets;

import br.com.javafinance.common.ApiException;
import br.com.javafinance.common.Money;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommitmentService {
    public record Create(@NotNull UUID accountId, @NotBlank @Size(max=200) String description,
                         @NotBlank @Size(max=200) String statementDescription, @NotNull LocalDate dueDate,
                         @NotBlank @Size(max=19) String amount, boolean monthly) {
        public Create(UUID accountId,String description,String statementDescription,LocalDate dueDate,String amount) {
            this(accountId,description,statementDescription,dueDate,amount,false);
        }
    }
    public enum Status { EXPECTED, IDENTIFIED, REVIEW }
    public record LinkedAccount(UUID id, String name) {}
    public record Row(UUID id, String description, String accountName, LocalDate dueDate, BigDecimal amount,
                      boolean monthly, boolean representedByMovement, Status status, UUID entryId, LocalDate bankDate) {}
    public record Upcoming(LocalDate from, LocalDate through, List<LinkedAccount> accounts,
                           List<Row> items, BigDecimal pendingTotal, BigDecimal reviewTotal) {}

    private static final int MATCH_DAYS = 3;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CommitmentService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Upcoming upcoming(UUID owner) {
        var from = LocalDate.now(clock);
        return upcomingThrough(owner,from,from.plusDays(29));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Upcoming upcomingThrough(UUID owner,LocalDate from,LocalDate through) {
        var accounts = jdbc.query("""
            SELECT a.id, a.name FROM bank_link l JOIN account a ON a.id=l.account_id AND a.owner_id=l.owner_id
            WHERE l.owner_id=? AND l.active AND NOT a.archived ORDER BY lower(a.name), a.id
            """, (rs, n) -> new LinkedAccount(rs.getObject("id", UUID.class), rs.getString("name")), owner);

        // Compute links from the current snapshot. A corrected or hidden bank entry is re-evaluated
        // on the next read. Include nearby commitments outside the display window to detect conflicts.
        var items = jdbc.query("""
            WITH nearby AS (
                SELECT c.id, c.owner_id, c.account_id, c.description, c.statement_description, c.monthly,
                       c.amount, (c.due_date + make_interval(months=>i))::date AS due_date
                FROM budget_commitment c
                CROSS JOIN LATERAL generate_series(0, CASE WHEN c.monthly THEN
                    (extract(year FROM age(date_trunc('month', ?::date), date_trunc('month', c.due_date))) * 12
                     + extract(month FROM age(date_trunc('month', ?::date), date_trunc('month', c.due_date))))::int
                    ELSE 0 END) AS i
                WHERE c.owner_id=? AND c.due_date<=?
            ), candidates AS (
                SELECT c.id AS commitment_id, c.due_date, e.id AS entry_id, e.date AS bank_date,
                    count(*) OVER (PARTITION BY c.id,c.due_date) AS entries_for_commitment,
                    count(*) OVER (PARTITION BY e.id) AS commitments_for_entry
                FROM nearby c
                JOIN bank_link l ON l.owner_id=c.owner_id AND l.account_id=c.account_id
                JOIN bank_entry e ON e.owner_id=l.owner_id AND e.link_id=l.id
                    AND e.date BETWEEN c.due_date-? AND c.due_date+?
                    AND e.amount=c.amount
                    AND lower(regexp_replace(btrim(e.description), '[[:space:]]+', ' ', 'g'))=c.statement_description
                WHERE e.direction='DEBIT' AND e.provider_status='POSTED'
                    AND NOT e.hidden AND e.state NOT IN ('IGNORED','CHANGED') AND e.transfer_id IS NULL
                    AND NOT EXISTS (
                        SELECT 1 FROM valid_financial_nature n
                        WHERE n.owner_id=e.owner_id AND n.entry_id=e.id AND n.nature<>'ORDINARY'
                    )
            ), resolved AS (
                SELECT DISTINCT ON (commitment_id,due_date) * FROM candidates ORDER BY commitment_id,due_date,entry_id
            )
            SELECT c.id, c.description, a.name, c.monthly, c.due_date, c.amount,
                EXISTS (SELECT 1 FROM movement m WHERE m.owner_id=c.owner_id AND m.account_id=c.account_id
                    AND m.status='PLANNED' AND m.kind='EXPENSE' AND m.due_date=c.due_date AND m.amount=c.amount
                    AND lower(regexp_replace(trim(m.description),'[[:space:]]+',' ','g')) IN
                    (c.statement_description,lower(regexp_replace(trim(c.description),'[[:space:]]+',' ','g')))) AS represented,
                r.entry_id, r.bank_date,
                CASE WHEN count(*) OVER (PARTITION BY c.account_id,c.statement_description,c.amount,c.due_date)>1 THEN 'REVIEW'
                    WHEN r.entry_id IS NULL THEN 'EXPECTED'
                    WHEN r.entries_for_commitment=1 AND r.commitments_for_entry=1 THEN 'IDENTIFIED'
                    ELSE 'REVIEW' END AS status
            FROM nearby c JOIN account a ON a.id=c.account_id AND a.owner_id=c.owner_id
            LEFT JOIN resolved r ON r.commitment_id=c.id AND r.due_date=c.due_date
            WHERE c.due_date<=? ORDER BY c.due_date, c.id
            """, (rs, n) -> {
                var status = Status.valueOf(rs.getString("status"));
                return new Row(rs.getObject("id", UUID.class), rs.getString("description"), rs.getString("name"),
                    rs.getObject("due_date", LocalDate.class), rs.getBigDecimal("amount"), rs.getBoolean("monthly"), rs.getBoolean("represented"), status,
                    status == Status.IDENTIFIED ? rs.getObject("entry_id", UUID.class) : null,
                    status == Status.IDENTIFIED ? rs.getObject("bank_date", LocalDate.class) : null);
            }, through.plusDays(6), through.plusDays(6), owner, through.plusDays(6),
            MATCH_DAYS, MATCH_DAYS, through);
        items = items.stream().filter(row -> !row.dueDate().isBefore(from) || row.status()!=Status.IDENTIFIED).toList();
        return new Upcoming(from, through, accounts, items, total(items, Status.EXPECTED), total(items, Status.REVIEW));
    }

    public record Suggestion(UUID accountId,String description,BigDecimal amount,LocalDate nextDate) {}

    public List<Suggestion> suggestions(UUID owner) {
        LocalDate today=LocalDate.now(clock);
        return jdbc.query("""
            WITH debits AS (
              SELECT l.account_id, lower(regexp_replace(trim(e.description),'[[:space:]]+',' ','g')) AS description,
                     e.amount,e.date,date_trunc('month',e.date)::date AS month
              FROM bank_entry e JOIN bank_link l ON l.id=e.link_id AND l.owner_id=e.owner_id
              JOIN consumption_record f ON f.bank_entry_id=e.id AND f.owner_id=e.owner_id
              WHERE e.owner_id=? AND l.active AND NOT f.hidden AND f.kind='EXPENSE' AND f.status='CONFIRMED'
                AND e.direction='DEBIT' AND e.provider_status='POSTED' AND e.state NOT IN ('IGNORED','CHANGED')
                AND e.date BETWEEN ? AND ?
            ), recent AS (
              SELECT *, dense_rank() OVER (PARTITION BY account_id,description,amount ORDER BY month DESC) AS recency FROM debits
            ), patterns AS (
              SELECT account_id,description,amount,(max(date)+interval '1 month')::date AS next_date
              FROM recent WHERE recency<=3 GROUP BY account_id,description,amount
              HAVING count(*)=3 AND count(DISTINCT month)=3
                AND min(month)=(max(month)-interval '2 months')::date
                AND max(extract(day FROM date))-min(extract(day FROM date))<=3
            )
            SELECT * FROM patterns p WHERE next_date BETWEEN ? AND ?
              AND NOT EXISTS (SELECT 1 FROM budget_commitment c WHERE c.owner_id=? AND c.account_id=p.account_id
                AND c.statement_description=p.description AND (c.monthly OR c.due_date=p.next_date))
            ORDER BY next_date,description LIMIT 25
            """,(rs,i)->new Suggestion(rs.getObject(1,UUID.class),rs.getString(2),rs.getBigDecimal(3),rs.getObject(4,LocalDate.class)),
            owner,today.withDayOfMonth(1).minusMonths(3),today,today,today.plusDays(29),owner);
    }

    private BigDecimal total(List<Row> items, Status status) {
        return items.stream().filter(item -> item.status() == status).map(Row::amount)
            .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    @Transactional
    public void create(UUID owner, Create input) {
        var amount = Money.parse(input.amount());
        if (amount.signum() <= 0) throw ApiException.bad("Informe um valor maior que zero.");
        var today = LocalDate.now(clock);
        if (input.dueDate().isBefore(today) || input.dueDate().isAfter(today.plusDays(29))) {
            throw ApiException.bad("O vencimento deve estar nos próximos 30 dias, incluindo hoje.");
        }
        var account = jdbc.query("""
            SELECT l.account_id FROM bank_link l JOIN account a ON a.id=l.account_id AND a.owner_id=l.owner_id
            WHERE l.owner_id=? AND l.account_id=? AND l.active AND NOT a.archived FOR UPDATE OF l
            """, (rs, row) -> rs.getObject(1, UUID.class), owner, input.accountId());
        if (account.isEmpty()) throw ApiException.bad("Escolha uma conta bancária vinculada e ativa.");
        int inserted = jdbc.update("""
            INSERT INTO budget_commitment(id, owner_id, account_id, description, statement_description, due_date, amount, monthly)
            VALUES (?, ?, ?, ?, lower(regexp_replace(btrim(?), '[[:space:]]+', ' ', 'g')), ?, ?, ?)
            ON CONFLICT (owner_id, due_date, account_id, amount, statement_description) DO NOTHING
            """, UUID.randomUUID(), owner, input.accountId(), input.description().strip(),
            input.statementDescription().strip(), input.dueDate(), amount,input.monthly());
        if (inserted == 0) throw ApiException.conflict("Já existe um compromisso com esses dados.");
    }

    @Transactional
    public void remove(UUID owner, UUID id) {
        if (jdbc.update("DELETE FROM budget_commitment WHERE owner_id=? AND id=?", owner, id) == 0) {
            throw ApiException.missing();
        }
    }
}
