package br.com.javafinance.accounts;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** One source per account: stored bank snapshot while linked, postings for manual accounts. */
@Service
public class AccountBalanceService {
    public enum Source { BANK, CALCULATED }
    public record Balance(BigDecimal amount, Source source, Instant updatedAt, Instant checkedAt, boolean readActive,UUID linkId) {}
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public AccountBalanceService(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }

    public Map<UUID,Balance> current(UUID owner) {
        Map<UUID,Balance> result=new HashMap<>();
        jdbc.query("""
            SELECT a.id,(CASE WHEN l.id IS NOT NULL THEN l.reported_balance ELSE COALESCE(p.total,0) END)::numeric(19,2) balance,
              l.id IS NOT NULL bank_linked,l.id bank_link_id,l.provider_updated_at,l.last_checked_at,COALESCE(l.active,false) read_active
            FROM account a
            LEFT JOIN bank_link l ON l.account_id=a.id AND l.owner_id=a.owner_id
            LEFT JOIN (
              SELECT p.account_id,SUM(p.amount) total FROM posting p
              JOIN movement m ON m.id=p.movement_id AND m.owner_id=p.owner_id
              WHERE p.owner_id=? AND m.status='CONFIRMED' AND m.settled_date<=? GROUP BY p.account_id
            ) p ON p.account_id=a.id
            WHERE a.owner_id=?
            """,rs->{
                var updated=rs.getTimestamp("provider_updated_at"); var checked=rs.getTimestamp("last_checked_at");
                result.put(rs.getObject("id",UUID.class),new Balance(rs.getBigDecimal("balance"),
                    rs.getBoolean("bank_linked")?Source.BANK:Source.CALCULATED,
                    updated==null?null:updated.toInstant(),checked==null?null:checked.toInstant(),rs.getBoolean("read_active"),rs.getObject("bank_link_id",UUID.class)));
            },owner,LocalDate.now(clock),owner);
        return result;
    }
}
