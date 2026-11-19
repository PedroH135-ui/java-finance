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
                         @NotBlank @Size(max=19) String amount) {}
    public enum Status { EXPECTED, IDENTIFIED, REVIEW }
    public record LinkedAccount(UUID id, String name) {}
    public record Row(UUID id, String description, String accountName, LocalDate dueDate, BigDecimal amount,
                      Status status, UUID entryId, LocalDate bankDate) {}
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
        var through = from.plusDays(29);
        var accounts = jdbc.query("""
            SELECT a.id, a.name FROM bank_link l JOIN account a ON a.id=l.account_id AND a.owner_id=l.owner_id
            WHERE l.owner_id=? AND l.active AND NOT a.archived ORDER BY lower(a.name), a.id
            """, (rs, n) -> new LinkedAccount(rs.getObject("id", UUID.class), rs.getString("name")), owner);

        // Compute links from the current snapshot. A corrected or hidden bank entry is re-evaluated
        // on the next read. Include nearby commitments outside the display window to detect conflicts.
        var items = jdbc.query("""
            WITH nearby AS (
                SELECT * FROM budget_commitment WHERE owner_id=? AND due_date BETWEEN ? AND ?
            ), candidates AS (
                SELECT c.id AS commitment_id, e.id AS entry_id, e.date AS bank_date,
                    count(*) OVER (PARTITION BY c.id) AS entries_for_commitment,
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
                SELECT DISTINCT ON (commitment_id) * FROM candidates ORDER BY commitment_id, entry_id
            )
            SELECT c.id, c.description, a.name, c.due_date, c.amount, r.entry_id, r.bank_date,
                CASE WHEN r.entry_id IS NULL THEN 'EXPECTED'
                    WHEN r.entries_for_commitment=1 AND r.commitments_for_entry=1 THEN 'IDENTIFIED'
                    ELSE 'REVIEW' END AS status
            FROM nearby c JOIN account a ON a.id=c.account_id AND a.owner_id=c.owner_id
            LEFT JOIN resolved r ON r.commitment_id=c.id
            WHERE c.due_date BETWEEN ? AND ? ORDER BY c.due_date, c.id
            """, (rs, n) -> {
                var status = Status.valueOf(rs.getString("status"));
                return new Row(rs.getObject("id", UUID.class), rs.getString("description"), rs.getString("name"),
                    rs.getObject("due_date", LocalDate.class), rs.getBigDecimal("amount"), status,
                    status == Status.IDENTIFIED ? rs.getObject("entry_id", UUID.class) : null,
                    status == Status.IDENTIFIED ? rs.getObject("bank_date", LocalDate.class) : null);
            }, owner, from.minusDays(2L * MATCH_DAYS), through.plusDays(2L * MATCH_DAYS),
            MATCH_DAYS, MATCH_DAYS, from, through);
        return new Upcoming(from, through, accounts, items, total(items, Status.EXPECTED), total(items, Status.REVIEW));
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
            INSERT INTO budget_commitment(id, owner_id, account_id, description, statement_description, due_date, amount)
            VALUES (?, ?, ?, ?, lower(regexp_replace(btrim(?), '[[:space:]]+', ' ', 'g')), ?, ?)
            ON CONFLICT (owner_id, due_date, account_id, amount, statement_description) DO NOTHING
            """, UUID.randomUUID(), owner, input.accountId(), input.description().strip(),
            input.statementDescription().strip(), input.dueDate(), amount);
        if (inserted == 0) throw ApiException.conflict("Já existe um compromisso com esses dados.");
    }

    @Transactional
    public void remove(UUID owner, UUID id) {
        if (jdbc.update("DELETE FROM budget_commitment WHERE owner_id=? AND id=?", owner, id) == 0) {
            throw ApiException.missing();
        }
    }
}
