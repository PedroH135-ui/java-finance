package br.com.javafinance.banking;

import br.com.javafinance.common.*;
import jakarta.validation.constraints.PositiveOrZero;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit personal instructions; independent of cloud AI and monetary postings. */
@Service
public class SalaryService {
    public record Mark(@PositiveOrZero long version, boolean futureReceipts) {}
    public record Toggle(@PositiveOrZero long version, boolean active) {}
    public record Rule(UUID id, UUID linkId, String account, String description, boolean active, boolean categoryActive, long version,boolean blockedByCorrection) {}
    public record ReadRule(String key, UUID category, LocalDate startsOn,Instant createdAt) {}
    private final JdbcTemplate jdbc; private final AuditService audit; private final Clock clock;
    public SalaryService(JdbcTemplate jdbc,AuditService audit,Clock clock){this.jdbc=jdbc;this.audit=audit;this.clock=clock;}
    static String key(String value){return CategoryAdvisor.memoryKey(value);}
    public List<Rule> list(UUID owner){return jdbc.query("""
        SELECT r.id,r.link_id,a.name,r.description,r.active,NOT c.archived,r.version,
          EXISTS(SELECT 1 FROM category_correction x WHERE x.owner_id=r.owner_id AND x.direction='CREDIT' AND x.category_id<>r.category_id
            AND x.description_key=r.description_key) blocked FROM salary_rule r
        JOIN bank_link l ON l.id=r.link_id AND l.owner_id=r.owner_id JOIN account a ON a.id=l.account_id AND a.owner_id=r.owner_id
        JOIN category c ON c.id=r.category_id AND c.owner_id=r.owner_id WHERE r.owner_id=? ORDER BY r.created_at,r.id
        """,(rs,n)->new Rule(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getBoolean(5),rs.getBoolean(6),rs.getLong(7),rs.getBoolean(8)),owner);}
    @Transactional public void mark(UUID owner,UUID id,Mark input){
        if(jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",owner).isEmpty())throw ApiException.missing();
        var links=jdbc.query("SELECT link_id FROM bank_entry WHERE id=? AND owner_id=?",(rs,n)->rs.getObject(1,UUID.class),id,owner);
        if(links.isEmpty())throw ApiException.missing();UUID link=links.getFirst();
        jdbc.queryForObject("SELECT id FROM bank_link WHERE id=? AND owner_id=? FOR UPDATE",UUID.class,link,owner);
        var entry=jdbc.queryForMap("SELECT * FROM bank_entry WHERE id=? AND owner_id=? FOR UPDATE",id,owner);
        if(((Number)entry.get("version")).longValue()!=input.version())throw ApiException.conflict("O registro mudou. Feche os detalhes e consulte novamente.");
        if(!"CREDIT".equals(entry.get("direction"))||!"POSTED".equals(entry.get("provider_status"))||Boolean.TRUE.equals(entry.get("hidden"))||"IGNORED".equals(entry.get("state"))||entry.get("movement_id")!=null||entry.get("transfer_id")!=null)
            throw ApiException.bad("Marque como salário somente um recebimento bancário efetivado, visível e sem vínculo de transferência ou lançamento.");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM valid_financial_nature WHERE owner_id=? AND entry_id=? AND nature<>'ORDINARY')",Boolean.class,owner,id)))
            throw ApiException.bad("Revise a natureza financeira antes de marcar este recebimento como salário.");
        var candidates=jdbc.query("SELECT id,name,archived FROM category WHERE owner_id=? AND kind='INCOME' ORDER BY id",(rs,n)->Map.of("id",rs.getObject(1,UUID.class),"name",rs.getString(2),"archived",rs.getBoolean(3)),owner)
            .stream().filter(c->Set.of("salário","salario").contains(key((String)c.get("name")))).toList();
        UUID category;
        if(candidates.isEmpty()){
            category=UUID.randomUUID();jdbc.update("INSERT INTO category(id,owner_id,name,kind,essential,archived,version) VALUES (?,?,?,'INCOME',false,false,0)",category,owner,"Salário");
        }else{
            var active=candidates.stream().filter(c->!Boolean.TRUE.equals(c.get("archived"))).findFirst().orElseThrow(()->ApiException.bad("Reative a categoria Salário antes de usar esta ação."));
            category=(UUID)active.get("id");
        }
        jdbc.update("UPDATE bank_entry SET category_id=?,category_description=description,category_direction=direction,category_source='MANUAL',category_auto_blocked=true,category_auto_method=null,category_auto_confidence=null,version=version+1 WHERE id=? AND owner_id=?",category,id,owner);
        if(input.futureReceipts())jdbc.update("""
            INSERT INTO salary_rule(id,owner_id,link_id,description_key,description,category_id,starts_on) VALUES (?,?,?,?,?,?,?)
            ON CONFLICT(owner_id,link_id,description_key) DO UPDATE SET category_id=EXCLUDED.category_id,active=true,starts_on=EXCLUDED.starts_on,effective_at=now(),version=salary_rule.version+1
            """,UUID.randomUUID(),owner,link,key((String)entry.get("description")),entry.get("description"),category,LocalDate.now(clock));
        audit.record(owner,id,"SALARY_MARKED",input.futureReceipts()?"Recebimento marcado como salário; regra pessoal criada para próximos recebimentos. Sem alterar previsão ou saldo.":"Recebimento marcado como salário, sem regra futura ou alteração de previsão/saldo.");
    }
    @Transactional public void toggle(UUID owner,UUID id,Toggle input){
        var links=jdbc.query("SELECT link_id FROM salary_rule WHERE id=? AND owner_id=?",(rs,n)->rs.getObject(1,UUID.class),id,owner);
        if(links.isEmpty())throw ApiException.missing();
        jdbc.queryForObject("SELECT id FROM bank_link WHERE id=? AND owner_id=? FOR UPDATE",UUID.class,links.getFirst(),owner);
        if(jdbc.update("UPDATE salary_rule SET active=?,version=version+1,starts_on=?,effective_at=CASE WHEN ? THEN now() ELSE effective_at END WHERE id=? AND owner_id=? AND version=?",input.active(),LocalDate.now(clock),input.active(),id,owner,input.version())==0){
            if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM salary_rule WHERE id=? AND owner_id=?)",Boolean.class,id,owner)))throw ApiException.missing();
            throw ApiException.conflict("A regra mudou. Atualize a lista.");
        }
        audit.record(owner,id,"SALARY_RULE_UPDATED","Preferência da regra de salário atualizada, sem reclassificar registros existentes.");
    }
    public List<ReadRule> rulesForRead(UUID owner,UUID link){return jdbc.query("SELECT r.description_key,r.category_id,r.starts_on,r.effective_at FROM salary_rule r JOIN category c ON c.id=r.category_id AND c.owner_id=r.owner_id WHERE r.owner_id=? AND r.link_id=? AND r.active AND NOT c.archived AND c.kind='INCOME'",(rs,n)->new ReadRule(rs.getString(1),rs.getObject(2,UUID.class),rs.getObject(3,LocalDate.class),rs.getTimestamp(4).toInstant()),owner,link);}
    public void applyNew(UUID owner,UUID id,BankProvider.RemoteEntry entry,List<ReadRule> rules){
        if(!"CREDIT".equals(entry.direction())||!"POSTED".equals(entry.status()))return;
        rules.stream().filter(r->r.key().equals(key(entry.description()))&&!entry.date().isBefore(r.startsOn())).findFirst().ifPresent(rule->{
            int changed=jdbc.update("""
                UPDATE bank_entry SET category_id=?,category_description=description,category_direction=direction,category_source='RULE',category_auto_method='RULE',category_auto_confidence='HIGH',category_auto_blocked=true,version=version+1
                WHERE id=? AND owner_id=? AND category_id IS NULL AND category_source IS DISTINCT FROM 'MANUAL' AND NOT category_auto_blocked AND NOT hidden AND movement_id IS NULL AND transfer_id IS NULL
                AND EXISTS(SELECT 1 FROM category c WHERE c.id=? AND c.owner_id=? AND NOT c.archived AND c.kind='INCOME')
                AND NOT EXISTS(SELECT 1 FROM category_correction x WHERE x.owner_id=? AND x.direction='CREDIT' AND x.description_key=? AND x.category_id<>?)
                AND first_seen_at>=?
                """,rule.category(),id,owner,rule.category(),owner,owner,CategoryAdvisor.memoryKey(entry.description()),rule.category(),java.sql.Timestamp.from(rule.createdAt()));
            if(changed>0)audit.record(owner,id,"SALARY_RULE_APPLIED","Regra pessoal de recebimento aplicada, sem IA, lançamento ou alteração de saldo.");
        });
    }
}
