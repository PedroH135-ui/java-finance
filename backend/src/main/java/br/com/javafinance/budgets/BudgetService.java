package br.com.javafinance.budgets;

import br.com.javafinance.common.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class BudgetService {
    public record Save(@NotBlank String amount, @PositiveOrZero Long version) {}
    public record Copy(@NotNull YearMonth sourceMonth) {}
    public record CopyResult(int copied, int skipped) {}
    public record Row(UUID categoryId, String name, boolean archived, boolean essential, BigDecimal limit,
                      Long version, BigDecimal spent, BigDecimal planned, BigDecimal remaining, BigDecimal percent) {}
    public record Totals(BigDecimal limit, BigDecimal spent, BigDecimal planned, BigDecimal remaining,
                         BigDecimal unbudgetedSpent, BigDecimal unbudgetedPlanned, long configured) {}
    public record Month(String month, List<Row> categories, Totals totals) {}
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public BudgetService(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    static void validate(YearMonth month) {
        if (month == null || month.getYear() < 1900 || month.getYear() > 2200)
            throw ApiException.bad("Escolha um mês entre 1900 e 2200.");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Month month(UUID owner, YearMonth month) {
        validate(month);
        var rows = jdbc.query("""
            SELECT c.id,c.name,c.archived,c.essential,b.amount,b.version,
              COALESCE(m.spent,0)::numeric(19,2) spent,COALESCE(m.planned,0)::numeric(19,2) planned
            FROM category c
            LEFT JOIN monthly_budget b ON b.owner_id=c.owner_id AND b.category_id=c.id AND b.month=?
            LEFT JOIN (
              SELECT category_id,SUM(amount) FILTER (WHERE status='CONFIRMED') spent,
                SUM(amount) FILTER (WHERE status='PLANNED') planned
              FROM movement WHERE owner_id=? AND competence=? AND kind='EXPENSE' AND status!='CANCELLED'
              GROUP BY category_id
            ) m ON m.category_id=c.id
            WHERE c.owner_id=? AND c.kind='EXPENSE' AND (NOT c.archived OR b.id IS NOT NULL OR m.category_id IS NOT NULL)
            ORDER BY c.archived,lower(c.name),c.id
            """, (rs, n) -> {
                BigDecimal limit = rs.getBigDecimal("amount"), spent = rs.getBigDecimal("spent");
                return new Row(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("archived"),
                    rs.getBoolean("essential"), limit, rs.getObject("version", Long.class), spent,
                    rs.getBigDecimal("planned"), limit == null ? null : limit.subtract(spent),
                    limit == null ? null : Money.percent(spent, limit));
            }, month.atDay(1), owner, month.atDay(1), owner);
        BigDecimal limit = zero(), spent = zero(), planned = zero(), unspent = zero(), unplanned = zero();
        long configured = 0;
        for (Row row : rows) {
            if (row.limit() == null) { unspent = unspent.add(row.spent()); unplanned = unplanned.add(row.planned()); }
            else { configured++; limit = limit.add(row.limit()); spent = spent.add(row.spent()); planned = planned.add(row.planned()); }
        }
        return new Month(month.toString(), rows, new Totals(limit, spent, planned, limit.subtract(spent), unspent, unplanned, configured));
    }

    @Transactional
    public void save(UUID owner, YearMonth month, UUID category, Save input) {
        validate(month);
        BigDecimal amount = Money.parse(input.amount());
        if (amount.signum() < 0) throw ApiException.bad("O limite não pode ser negativo.");
        var categoryRows = jdbc.queryForList("SELECT kind,archived FROM category WHERE id=? AND owner_id=? FOR UPDATE", category, owner);
        if (categoryRows.isEmpty()) throw ApiException.missing();
        if (!"EXPENSE".equals(categoryRows.getFirst().get("kind"))) throw ApiException.bad("Defina limites somente para categorias de despesa.");
        if (input.version() == null && Boolean.TRUE.equals(categoryRows.getFirst().get("archived")))
            throw ApiException.bad("Reative a categoria antes de criar um limite.");
        UUID id;
        if (input.version() == null) {
            id = UUID.randomUUID();
            if (jdbc.update("INSERT INTO monthly_budget(id,owner_id,category_id,month,amount) VALUES (?,?,?,?,?) ON CONFLICT(owner_id,category_id,month) DO NOTHING",
                id, owner, category, month.atDay(1), amount) == 0) throw changed();
        } else {
            if (input.version() < 0) throw ApiException.bad("Versão inválida.");
            var ids = jdbc.query("UPDATE monthly_budget SET amount=?,version=version+1 WHERE owner_id=? AND category_id=? AND month=? AND version=? RETURNING id",
                (rs, n) -> rs.getObject("id", UUID.class), amount, owner, category, month.atDay(1), input.version());
            if (ids.isEmpty()) throw changed();
            id = ids.getFirst();
        }
        audit.record(owner, id, "BUDGET_SAVED", "Limite de " + month + ": " + amount.toPlainString() + " BRL.");
    }

    @Transactional
    public CopyResult copy(UUID owner, YearMonth target, Copy input) {
        validate(target); validate(input.sourceMonth());
        if (target.equals(input.sourceMonth())) throw ApiException.bad("Escolha um mês de origem diferente do destino.");
        var source = jdbc.queryForList("""
            SELECT b.category_id,b.amount,c.archived FROM monthly_budget b
            JOIN category c ON c.id=b.category_id AND c.owner_id=b.owner_id
            WHERE b.owner_id=? AND b.month=? ORDER BY c.id FOR UPDATE OF c
            """, owner, input.sourceMonth().atDay(1));
        int copied = 0;
        for (var row : source) {
            if (Boolean.TRUE.equals(row.get("archived"))) continue;
            UUID id = UUID.randomUUID();
            int inserted = jdbc.update("INSERT INTO monthly_budget(id,owner_id,category_id,month,amount) VALUES (?,?,?,?,?) ON CONFLICT(owner_id,category_id,month) DO NOTHING",
                id, owner, row.get("category_id"), target.atDay(1), row.get("amount"));
            copied += inserted;
            if (inserted > 0) audit.record(owner, id, "BUDGET_COPIED", "Limite copiado de " + input.sourceMonth() + " para " + target + ".");
        }
        return new CopyResult(copied, source.size() - copied);
    }
    private static BigDecimal zero() { return new BigDecimal("0.00"); }
    private static ApiException changed() { return ApiException.conflict("O limite mudou ou já existe. Atualize a página antes de salvar."); }
}
