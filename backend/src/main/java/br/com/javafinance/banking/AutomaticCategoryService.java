package br.com.javafinance.banking;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
public class AutomaticCategoryService {
    public record Status(boolean enabled,boolean running,long categorized,long awaiting,Instant lastRun,boolean available,long version) {}
    private static final String CANDIDATE="""
        owner_id=? AND provider_status='POSTED' AND NOT hidden AND state<>'IGNORED'
        AND movement_id IS NULL AND transfer_id IS NULL AND NOT category_auto_blocked
        AND category_source IS DISTINCT FROM 'MANUAL'
        AND NOT EXISTS(SELECT 1 FROM valid_financial_nature n WHERE n.entry_id=bank_entry.id AND n.owner_id=bank_entry.owner_id AND n.nature<>'ORDINARY')
        AND (category_id IS NULL OR category_description IS DISTINCT FROM description OR category_direction IS DISTINCT FROM direction)
        """;
    private final AutomaticCategoryScope scope;
    private final BankAssistanceService assistance;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AtomicBoolean running=new AtomicBoolean();
    private volatile Instant lastRun;
    public AutomaticCategoryService(AutomaticCategoryScope scope,BankAssistanceService assistance,JdbcTemplate jdbc,Clock clock) {
        this.scope=scope;this.assistance=assistance;this.jdbc=jdbc;this.clock=clock;
    }
    @Scheduled(fixedDelayString="${app.category-ai.automatic-poll-ms:10000}",initialDelayString="${app.category-ai.automatic-initial-delay-ms:10000}")
    public void runPending() {
        if(!scope.enabled() || !scope.activeFor(scope.owner()) || !running.compareAndSet(false,true))return;
        try {
            UUID owner=scope.owner();
            var ids=jdbc.query("SELECT id FROM bank_entry WHERE "+CANDIDATE+
                " AND category_auto_resolution IS DISTINCT FROM 'NO_CATEGORY' AND (category_auto_attempted_at IS NULL OR category_auto_attempted_at<?) ORDER BY date DESC,id LIMIT 3",
                (rs,n)->rs.getObject("id",UUID.class),owner,java.sql.Timestamp.from(clock.instant().minusSeconds(900)));
            for(UUID id:ids) {
                try {
                    // Network/model work happens before acquiring the write transaction and row locks.
                    var result=assistance.automaticSuggestion(owner,id);
                    assistance.applyAutomatic(owner,id,result.entryVersion(),result.categorySuggestion());
                }catch(RuntimeException ex) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("automatic_category_failed code=LOCAL_BATCH");
                }
            }
        }finally{lastRun=clock.instant();running.set(false);}
    }
    public Status status(UUID owner) {
        boolean available=scope.availableFor(owner),active=scope.activeFor(owner);
        long preferenceVersion=jdbc.query("SELECT version FROM category_automation_preference WHERE owner_id=?",(rs,n)->rs.getLong(1),owner).stream().findFirst().orElse(0L);
        if(!active)return new Status(false,false,0,0,null,available,preferenceVersion);
        long categorized=jdbc.queryForObject("SELECT count(*) FROM bank_entry WHERE owner_id=? AND category_source='AUTO' AND category_id IS NOT NULL",Long.class,owner);
        long awaiting=jdbc.queryForObject("SELECT count(*) FROM bank_entry WHERE "+CANDIDATE,Long.class,owner);
        if(scope.cloudEnabled()){
            var rows=jdbc.queryForList("SELECT last_run,lease_until>now() running FROM category_automation_job WHERE owner_id=?",owner);
            var row=rows.isEmpty()?Map.<String,Object>of():rows.getFirst();
            return new Status(true,Boolean.TRUE.equals(row.get("running")),categorized,awaiting,row.get("last_run")==null?null:((java.sql.Timestamp)row.get("last_run")).toInstant(),true,preferenceVersion);
        }
        return new Status(true,running.get(),categorized,awaiting,lastRun,true,preferenceVersion);
    }
}
