package br.com.javafinance.banking;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable inbox and expiring database leases, independent of a warm JVM or logged-in browser. */
@Service
public class BankAutoSync {
    private final BankProperties config;
    private final BankService bank;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;
    public BankAutoSync(BankProperties config, BankService bank, JdbcTemplate jdbc, Clock clock, PlatformTransactionManager manager) {
        this.config=config; this.bank=bank; this.jdbc=jdbc; this.clock=clock; tx=new TransactionTemplate(manager);
    }
    public boolean enqueue(UUID item, String eventId) {
        if(!config.automationReady() || !config.getItemIds().contains(item)) return false;
        return Boolean.TRUE.equals(tx.execute(status->{
            int inserted=jdbc.update("INSERT INTO bank_sync_event(owner_id,event_id,item_id,received_at) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
                config.getOwnerId(),eventId,item,ts(clock.instant()));
            if(inserted==0) return false;
            jdbc.update("""
                INSERT INTO bank_sync_job(owner_id,item_id,requested_version) VALUES (?,?,1)
                ON CONFLICT(owner_id,item_id) DO UPDATE SET requested_version=bank_sync_job.requested_version+1
                """,config.getOwnerId(),item);
            return true;
        }));
    }
    public boolean daily() {
        if(!config.automationReady()) return false;
        boolean done=true;
        for(UUID item:config.getItemIds().stream().sorted().toList()) {
            enqueue(item,"daily:"+LocalDate.now(clock)+":"+item);
            done=process(item) && done;
        }
        return done;
    }
    public boolean process(UUID item) {
        if(!config.automationReady() || !config.getItemIds().contains(item)) return true;
        UUID owner=config.getOwnerId(),token=UUID.randomUUID();
        // Capture a generation: another event received during this run must remain pending.
        var claimed=jdbc.queryForList("""
            UPDATE bank_sync_job SET lease_token=?,lease_until=?
            WHERE owner_id=? AND item_id=? AND completed_version<requested_version
              AND (lease_until IS NULL OR lease_until<=?) RETURNING requested_version
            """,token,ts(clock.instant().plusSeconds(600)),owner,item,ts(clock.instant()));
        if(claimed.isEmpty()) {
            return !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM bank_sync_job WHERE owner_id=? AND item_id=? AND completed_version<requested_version)",Boolean.class,owner,item));
        }
        long generation=((Number)claimed.getFirst().get("requested_version")).longValue();
        boolean complete=true;
        try {
            LocalDate today=LocalDate.now(clock);
            for(var link:bank.links(owner).stream().filter(l->l.itemId().equals(item) && l.active()).toList()) {
                if(link.importFrom().isAfter(today)) continue; // Never cross the user's opening balance boundary.
                LocalDate from=link.autoCheckedThrough()==null ? link.importFrom()
                    : link.autoCheckedThrough().isBefore(today.minusDays(1)) ? link.autoCheckedThrough().minusDays(6) : today.minusDays(92);
                if(from.isBefore(link.importFrom())) from=link.importFrom();
                LocalDate to=from.plusDays(92).isBefore(today) ? from.plusDays(92) : today;
                try {
                    bank.sync(owner,link.id(),new BankService.Sync(from,to));
                    jdbc.update("UPDATE bank_link SET auto_checked_through=GREATEST(COALESCE(auto_checked_through,?),?),auto_error_at=null WHERE id=? AND owner_id=? AND active=true",to,to,link.id(),owner);
                    if(to.isBefore(today)) complete=false; // Resume large histories on the next delivery/daily run.
                } catch(RuntimeException ex) {
                    // Retain the job and cursor; no raw provider exception or financial payload in logs.
                    jdbc.update("UPDATE bank_link SET auto_error_at=? WHERE id=? AND owner_id=? AND active=true",ts(clock.instant()),link.id(),owner);
                    complete=false;
                }
            }
            jdbc.update("""
                UPDATE bank_sync_job SET completed_version=CASE WHEN ? THEN ? ELSE completed_version END,
                  last_error_at=CAST(? AS timestamptz),lease_token=null,lease_until=null
                WHERE owner_id=? AND item_id=? AND lease_token=?
                """,complete,generation,complete?null:ts(clock.instant()),owner,item,token);
            return complete;
        } catch(RuntimeException ex) {
            jdbc.update("UPDATE bank_sync_job SET last_error_at=?,lease_token=null,lease_until=null WHERE owner_id=? AND item_id=? AND lease_token=?",ts(clock.instant()),owner,item,token);
            return false;
        }
    }
    private static java.sql.Timestamp ts(Instant value) { return java.sql.Timestamp.from(value); }
}
