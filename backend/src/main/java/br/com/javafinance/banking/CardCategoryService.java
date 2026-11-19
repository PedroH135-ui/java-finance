package br.com.javafinance.banking;
import br.com.javafinance.common.AuditService;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses the existing durable owner queue and advisor; never changes bill totals or balances. */
@Service
public class CardCategoryService {
 private static final String ELIGIBLE="owner_id=? AND present AND provider_status='POSTED' AND direction='DEBIT' AND NOT category_auto_blocked AND category_source IS DISTINCT FROM 'MANUAL' AND (category_id IS NULL OR category_description IS DISTINCT FROM description OR category_direction IS DISTINCT FROM direction)";
 public static final String CANDIDATE=ELIGIBLE+" AND category_auto_resolution IS NULL";
 private final JdbcTemplate jdbc;private final AutomaticCategoryScope scope;private final CategoryAdvisor advisor;private final TransactionTemplate tx;private final AuditService audit;private final Clock clock;
 public CardCategoryService(JdbcTemplate jdbc,AutomaticCategoryScope scope,CategoryAdvisor advisor,PlatformTransactionManager manager,AuditService audit,Clock clock){this.jdbc=jdbc;this.scope=scope;this.advisor=advisor;this.tx=new TransactionTemplate(manager);this.audit=audit;this.clock=clock;}
 public long awaitingCount(UUID owner){return jdbc.queryForObject("SELECT count(*) FROM bank_card_charge WHERE "+ELIGIBLE,Long.class,owner);}
 public long categorizedCount(UUID owner){return jdbc.queryForObject("SELECT count(*) FROM bank_card_charge WHERE owner_id=? AND category_source='AUTO' AND category_id IS NOT NULL",Long.class,owner);}
 public boolean pending(UUID owner){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM bank_card_charge WHERE "+CANDIDATE+")",Boolean.class,owner));}
 public void process(UUID owner,int limit){
  if(!scope.activeFor(owner)||limit<=0)return;
  var entries=jdbc.queryForList("SELECT id,description,version FROM bank_card_charge WHERE "+CANDIDATE+" ORDER BY date DESC,id LIMIT ?",owner,Math.min(limit,3));
  if(entries.isEmpty())return;
  var categories=jdbc.query("SELECT id,name FROM category WHERE owner_id=? AND kind='EXPENSE' AND NOT archived ORDER BY id LIMIT 100",(rs,n)->new CategoryAdvisor.Category(rs.getString(1),rs.getString(2)),owner);
  var history=jdbc.query("""
   SELECT description,category_id FROM (
    SELECT b.description,b.category_id,b.date FROM bank_entry b JOIN category c ON c.id=b.category_id AND c.owner_id=b.owner_id
    WHERE b.owner_id=? AND b.direction='DEBIT' AND b.category_source='MANUAL' AND b.category_description=b.description AND b.category_direction=b.direction AND NOT b.hidden AND NOT c.archived AND c.kind='EXPENSE'
    UNION ALL SELECT x.description,x.category_id,x.date FROM bank_card_charge x JOIN category c ON c.id=x.category_id AND c.owner_id=x.owner_id
    WHERE x.owner_id=? AND x.category_source='MANUAL' AND x.category_description=x.description AND x.category_direction=x.direction AND x.present AND NOT c.archived AND c.kind='EXPENSE'
   ) h ORDER BY date DESC LIMIT 100
   """,(rs,n)->new CategoryAdvisor.Example(rs.getString(1),rs.getString(2)),owner,owner);
  var memory=jdbc.query("SELECT x.description,x.category_id FROM bank_card_charge x JOIN category c ON c.id=x.category_id AND c.owner_id=x.owner_id WHERE x.owner_id=? AND x.present AND x.category_source='AUTO' AND x.category_auto_method='LLM' AND x.category_description=x.description AND NOT c.archived ORDER BY x.date DESC LIMIT 100",(rs,n)->new CategoryAdvisor.Example(rs.getString(1),rs.getString(2)),owner);
  for(var entry:entries){
   if(!scope.activeFor(owner))return;
   var exact=correction(owner,(String)entry.get("description"));
   var proposal=exact==null?advisor.suggest(owner,"EXPENSE",new CategoryAdvisor.Request((String)entry.get("description"),categories,history,"UNKNOWN"),memory):exact;
   tx.executeWithoutResult(status->{
    if(!scope.lockActive(owner))return;
    var rows=jdbc.queryForList("SELECT * FROM bank_card_charge WHERE id=? AND "+CANDIDATE+" FOR UPDATE",entry.get("id"),owner);
    if(rows.isEmpty()||((Number)rows.getFirst().get("version")).longValue()!=((Number)entry.get("version")).longValue())return;
    var latest=correction(owner,(String)entry.get("description"));var result=latest==null?proposal:latest;
    jdbc.update("UPDATE bank_card_charge SET category_auto_resolution='NO_CATEGORY',category_auto_attempted_at=? WHERE id=? AND owner_id=?",java.sql.Timestamp.from(clock.instant()),entry.get("id"),owner);
    if(result==null||result.categoryId()==null||(latest==null&&exact==null&&categories.stream().noneMatch(c->c.id().equals(result.categoryId())))||!Set.of("HISTORY","RULE","RAG","LLM","MEMORY").contains(result.method())||!Set.of("HIGH","MEDIUM","LOW").contains(result.confidence()))return;
    UUID category=UUID.fromString(result.categoryId());
    if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM category WHERE id=? AND owner_id=? AND kind='EXPENSE' AND NOT archived)",Boolean.class,category,owner)))return;
    jdbc.update("UPDATE bank_card_charge SET category_id=?,category_description=description,category_direction=direction,category_source='AUTO',category_auto_method=?,category_auto_confidence=?,category_auto_resolution='APPLIED',version=version+1 WHERE id=? AND owner_id=?",category,result.method(),result.confidence(),entry.get("id"),owner);
    audit.record(owner,(UUID)entry.get("id"),"CARD_CATEGORY_AUTOMATIC","Categoria sugerida aplicada ao detalhe do cartão, sem alterar saldos; correções manuais continuam prioritárias.");
   });
  }
 }
 private CategoryAdvisor.Suggestion correction(UUID owner,String description){
  var rows=jdbc.query("SELECT x.category_id FROM category_correction x JOIN category c ON c.id=x.category_id AND c.owner_id=x.owner_id WHERE x.owner_id=? AND x.direction='DEBIT' AND x.description_key=? AND c.kind='EXPENSE' AND NOT c.archived",(rs,n)->rs.getString(1),owner,CategoryAdvisor.memoryKey(description));
  return rows.isEmpty()?null:new CategoryAdvisor.Suggestion(rows.getFirst(),"HIGH","HISTORY","Correção manual do perfil, sem LLM.",1,false);
 }
}
