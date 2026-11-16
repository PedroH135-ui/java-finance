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
    public record Status(boolean enabled,boolean running,long categorized,long awaiting,Instant lastRun) {}
    private static final String CANDIDATE="""
        owner_id=? AND provider_status='POSTED' AND NOT hidden AND state<>'IGNORED'
        AND movement_id IS NULL AND transfer_id IS NULL AND NOT category_auto_blocked
        AND category_source IS DISTINCT FROM 'MANUAL'
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
                " AND (category_auto_attempted_at IS NULL OR category_auto_attempted_at<?) ORDER BY date DESC,id LIMIT 3",
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
        if(!scope.activeFor(owner))return new Status(false,false,0,0,null);
        long categorized=jdbc.queryForObject("SELECT count(*) FROM bank_entry WHERE owner_id=? AND category_source='AUTO' AND category_id IS NOT NULL",Long.class,owner);
        long awaiting=jdbc.queryForObject("SELECT count(*) FROM bank_entry WHERE "+CANDIDATE,Long.class,owner);
        return new Status(true,running.get(),categorized,awaiting,lastRun);
    }
}
