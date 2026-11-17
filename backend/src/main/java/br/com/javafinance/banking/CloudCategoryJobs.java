package br.com.javafinance.banking;

import br.com.javafinance.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable owner wake-ups; leased work and versioned writes survive duplicate delivery/restarts. */
@Service
public class CloudCategoryJobs {
    private CardCategoryService cardCategories;
    @org.springframework.beans.factory.annotation.Autowired
    public void cardCategories(CardCategoryService service){this.cardCategories=service;}
    public record Preference(boolean enabled,long version) {}
    private static final String CANDIDATE="""
        owner_id=? AND provider_status='POSTED' AND NOT hidden AND state<>'IGNORED'
        AND movement_id IS NULL AND transfer_id IS NULL AND NOT category_auto_blocked
        AND category_source IS DISTINCT FROM 'MANUAL'
        AND NOT EXISTS(SELECT 1 FROM valid_financial_nature n WHERE n.entry_id=bank_entry.id AND n.owner_id=bank_entry.owner_id AND n.nature<>'ORDINARY')
        AND category_auto_resolution IS DISTINCT FROM 'NO_CATEGORY'
        AND (category_id IS NULL OR category_description IS DISTINCT FROM description OR category_direction IS DISTINCT FROM direction)
        AND (category_auto_attempted_at IS NULL OR category_auto_attempted_at<now()-interval '15 minutes')
        """;
    private final CloudCategoryPolicy policy;
    private final BankAssistanceService assistance;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final URI queue;
    private final String token;
    private final ObjectMapper mapper;
    private AutomaticCategoryScope local;
    @org.springframework.beans.factory.annotation.Autowired
    public void localScope(AutomaticCategoryScope local){this.local=local;}
    public void prepareForLink(UUID owner){
        if(policy.enabled() || local!=null && local.availableFor(owner))
            jdbc.update("INSERT INTO category_automation_preference(owner_id,enabled) VALUES (?,true) ON CONFLICT DO NOTHING",owner);
    }
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    public CloudCategoryJobs(CloudCategoryPolicy policy,BankAssistanceService assistance,JdbcTemplate jdbc,
        PlatformTransactionManager manager,ObjectMapper mapper,
        @Value("${app.category-ai.url:http://127.0.0.1:8123}") String url,
        @Value("${app.category-ai.job-token:}") String token) {
        this.policy=policy;this.assistance=assistance;this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);
        this.queue=URI.create(url).resolve("/jobs");this.token=token;this.mapper=mapper;
        if(policy.enabled() && (!"https".equals(queue.getScheme()) || token.length()<32))
            throw new IllegalArgumentException("Cloud jobs require HTTPS and an independent job token.");
    }
    public Preference preference(UUID owner){
        var rows=jdbc.query("SELECT enabled,version FROM category_automation_preference WHERE owner_id=?",
            (rs,n)->new Preference(rs.getBoolean(1),rs.getLong(2)),owner);
        return rows.isEmpty()?new Preference(false,0):rows.getFirst();
    }
    public void preference(UUID owner,boolean enabled,long expected){
        if(!policy.enabled() && !(local!=null && local.availableFor(owner)))throw ApiException.bad("A classificação automática não está disponível neste ambiente.");
        tx.executeWithoutResult(s->{
            jdbc.update("INSERT INTO category_automation_preference(owner_id) VALUES (?) ON CONFLICT DO NOTHING",owner);
            int changed=jdbc.update("UPDATE category_automation_preference SET enabled=?,version=version+1,updated_at=now() WHERE owner_id=? AND version=?",enabled,owner,expected);
            if(changed!=1)throw ApiException.conflict("A configuração mudou. Atualize a página.");
        });
        if(enabled)request(owner);
    }
    public void request(UUID owner){
        if(!policy.activeFor(owner))return;
        jdbc.update("""
            INSERT INTO category_automation_job(owner_id) VALUES (?) ON CONFLICT(owner_id)
            DO UPDATE SET requested_version=category_automation_job.requested_version+1
            """,owner);
        dispatch(owner);
    }
    @org.springframework.context.event.EventListener
    public void afterRead(BankService.ReadCompleted event){
        // The banking transaction has already committed. AI infrastructure cannot fail the bank read.
        try{request(event.owner());}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("category_enqueue_failed code=RETRY_ON_NEXT_SYNC");}
    }
    @org.springframework.context.event.EventListener
    public void afterProductRead(BankProductsService.ReadCompleted event){
        try{request(event.owner());}catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(getClass()).warn("card_category_enqueue_failed code=RETRY_ON_NEXT_SYNC");}
    }
    @org.springframework.transaction.event.TransactionalEventListener(phase=org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void afterExplicitRetry(BankAssistanceService.CategoryWorkRequested event){
        // The original transaction already committed; persist the wake-up in its own transaction.
        try{
            var fresh=new TransactionTemplate(tx.getTransactionManager());
            fresh.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            fresh.executeWithoutResult(s->request(event.owner()));
        }
        catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("category_enqueue_failed code=RETRY_ON_NEXT_SYNC");}
    }
    /** Failure to publish leaves the SQL outbox pending; a later sync/manual retry/daily pass recovers it. */
    public boolean dispatch(UUID owner){
        if(!policy.activeFor(owner))return true;
        try {
            var builder=HttpRequest.newBuilder(queue).timeout(Duration.ofSeconds(5))
                .header("Content-Type","application/json").header("Authorization","Bearer "+token);
            String oidc=VercelRuntimeIdentity.token();
            if(!oidc.isBlank())builder.header("x-vercel-trusted-oidc-idp-token",oidc);
            var response=http.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("owner",owner)))).build(),HttpResponse.BodyHandlers.discarding());
            org.slf4j.LoggerFactory.getLogger(getClass()).info("category_queue_publish status={} runtime_identity={}",response.statusCode(),!oidc.isBlank());
            return response.statusCode()==202;
        }catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
        catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("category_queue_publish_failed code=RETRY_PENDING");return false;}
    }
    public void retryPending(){
        if(!policy.enabled())return;
        jdbc.query("SELECT j.owner_id FROM category_automation_job j JOIN category_automation_preference p USING(owner_id) WHERE p.enabled AND j.requested_version>j.completed_version LIMIT 100",
            (rs,n)->rs.getObject(1,UUID.class)).forEach(this::dispatch);
    }
    /** Returns true while another batch is needed; a busy lease is retried by the durable queue. */
    public boolean process(UUID owner){
        if(!policy.activeFor(owner))return false;
        UUID lease=UUID.randomUUID();
        Long generation=tx.execute(s->{
            var rows=jdbc.queryForList("UPDATE category_automation_job SET lease_token=?,lease_until=now()+interval '2 minutes',attempts=attempts+1 WHERE owner_id=? AND requested_version>completed_version AND (lease_until IS NULL OR lease_until<now()) RETURNING requested_version",lease,owner);
            return rows.isEmpty()?null:((Number)rows.getFirst().get("requested_version")).longValue();
        });
        if(generation==null){
            boolean pending=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM category_automation_job WHERE owner_id=? AND requested_version>completed_version)",Boolean.class,owner));
            if(pending)throw new CategoryAdvisor.TemporarilyUnavailable();
            return false;
        }
        boolean success=false;
        try {
            var ids=jdbc.query("SELECT id FROM bank_entry WHERE "+CANDIDATE+" ORDER BY date DESC,id LIMIT 3",(rs,n)->rs.getObject(1,UUID.class),owner);
            for(UUID id:ids){
                if(!policy.activeFor(owner))break;
                var result=assistance.automaticSuggestion(owner,id);
                assistance.applyAutomatic(owner,id,result.entryVersion(),result.categorySuggestion());
            }
            if(ids.size()<3)cardCategories.process(owner,3-ids.size());
            boolean more=policy.activeFor(owner) && (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM bank_entry WHERE "+CANDIDATE+")",Boolean.class,owner))||cardCategories.pending(owner));
            jdbc.update("UPDATE category_automation_job SET completed_version=CASE WHEN ? THEN completed_version ELSE ? END,last_run=now() WHERE owner_id=? AND lease_token=?",more,generation,owner,lease);
            success=true;
            return more || Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM category_automation_job WHERE owner_id=? AND requested_version>completed_version)",Boolean.class,owner));
        }finally{
            jdbc.update("UPDATE category_automation_job SET lease_token=null,lease_until=null WHERE owner_id=? AND lease_token=?",owner,lease);
            if(!success)org.slf4j.LoggerFactory.getLogger(getClass()).warn("category_job_failed code=RETRY_PENDING");
        }
    }
}
