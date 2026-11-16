package br.com.javafinance.banking;

import br.com.javafinance.common.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankAssistanceService {
    public record Peer(UUID id,String accountName,String description,BigDecimal amount,LocalDate date,long version) {}
    public record Transfer(UUID id,UUID peerId,long version,boolean valid) {}
    public record Assistance(long entryVersion,UUID currentCategoryId,boolean canCategorize,String unavailableReason,
        CategoryAdvisor.Suggestion categorySuggestion,List<Peer> transferCandidates,Transfer transfer,CategoryState categoryState) {}
    public record CategoryState(String source,String method,String confidence,boolean automaticBlocked,boolean automaticEnabled) {}
    public record CategoryChange(@PositiveOrZero long version,UUID categoryId) {}
    public record TransferChange(@PositiveOrZero long version,@NotNull UUID peerId,@PositiveOrZero long peerVersion) {}
    public record UndoTransfer(@PositiveOrZero long version) {}
    private final JdbcTemplate jdbc; private final CategoryAdvisor advisor; private final AuditService audit; private final Clock clock;
    private final AutomaticCategoryScope automatic;
    public BankAssistanceService(JdbcTemplate jdbc,CategoryAdvisor advisor,AuditService audit,Clock clock,AutomaticCategoryScope automatic) {
        this.jdbc=jdbc;this.advisor=advisor;this.audit=audit;this.clock=clock;this.automatic=automatic;
    }
    private Map<String,Object> entry(UUID owner,UUID id) {
        var rows=jdbc.queryForList("SELECT e.*,l.account_id FROM bank_entry e JOIN bank_link l ON l.id=e.link_id AND l.owner_id=e.owner_id WHERE e.id=? AND e.owner_id=?",id,owner);
        if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();
    }
    private static LocalDate date(Map<String,Object> row) {return ((java.sql.Date)row.get("date")).toLocalDate();}
    private static long version(Map<String,Object> row) {return ((Number)row.get("version")).longValue();}
    private static boolean eligible(Map<String,Object> row) {
        return row.get("movement_id")==null && row.get("transfer_id")==null && !Boolean.TRUE.equals(row.get("hidden"))
            && !"IGNORED".equals(row.get("state")) && "POSTED".equals(row.get("provider_status"));
    }
    public Assistance assistance(UUID owner,UUID id) {
        return assistance(owner,id,false);
    }
    public Assistance automaticSuggestion(UUID owner,UUID id) {
        if(!automatic.activeFor(owner))throw ApiException.missing();
        return assistance(owner,id,true);
    }
    private Assistance assistance(UUID owner,UUID id,boolean forAutomatic) {
        var e=entry(owner,id); boolean can=eligible(e); Transfer transfer=null;
        if(e.get("transfer_id")!=null) {
            var p=jdbc.queryForMap("SELECT p.*,EXISTS(SELECT 1 FROM valid_bank_transfer v WHERE v.id=p.id AND v.owner_id=p.owner_id) valid FROM bank_transfer_pair p WHERE p.id=? AND p.owner_id=?",e.get("transfer_id"),owner);
            UUID peer=(UUID)(id.equals(p.get("debit_entry_id"))?p.get("credit_entry_id"):p.get("debit_entry_id"));
            transfer=new Transfer((UUID)p.get("id"),peer,version(p),Boolean.TRUE.equals(p.get("valid")));
        }
        List<Peer> candidates=can?jdbc.query("""
            SELECT x.*,a.name account_name FROM bank_entry x
            JOIN bank_link l ON l.id=x.link_id AND l.owner_id=x.owner_id
            JOIN account a ON a.id=l.account_id AND a.owner_id=x.owner_id
            WHERE x.owner_id=? AND l.account_id<>? AND x.direction<>? AND x.amount=?
              AND x.date BETWEEN ? AND ? AND x.provider_status='POSTED' AND NOT x.hidden AND x.state<>'IGNORED'
              AND x.movement_id IS NULL AND x.transfer_id IS NULL
            ORDER BY abs(x.date-?::date),a.name,x.id LIMIT 10
            """,(rs,n)->new Peer(rs.getObject("id",UUID.class),rs.getString("account_name"),rs.getString("description"),rs.getBigDecimal("amount"),rs.getObject("date",LocalDate.class),rs.getLong("version")),
            owner,e.get("account_id"),e.get("direction"),e.get("amount"),date(e).minusDays(2),date(e).plusDays(2),date(e)):List.of();
        UUID current=Objects.equals(e.get("category_description"),e.get("description")) && Objects.equals(e.get("category_direction"),e.get("direction"))?(UUID)e.get("category_id"):null;
        boolean enabled=automatic.activeFor(owner);
        var categoryState=new CategoryState((String)e.get("category_source"),(String)e.get("category_auto_method"),(String)e.get("category_auto_confidence"),Boolean.TRUE.equals(e.get("category_auto_blocked")),enabled);
        var none=new CategoryAdvisor.Suggestion(null,"NONE","NONE","Não há sugestão para este registro.",0,false);
        String unavailable=can?"":transfer!=null?"Identificado como transferência. Desfaça essa identificação antes de categorizar.":"Categorize apenas registros efetivados, visíveis e sem lançamento associado. Para um lançamento associado, utilize seus detalhes.";
        if(!can || current!=null || (enabled && !forAutomatic) || (forAutomatic && (categoryState.automaticBlocked() || "MANUAL".equals(categoryState.source()) || !candidates.isEmpty())))
            return new Assistance(version(e),current,can,unavailable,none,candidates,transfer,categoryState);
        String kind="DEBIT".equals(e.get("direction"))?"EXPENSE":"INCOME";
        var categories=jdbc.query("SELECT id,name FROM category WHERE owner_id=? AND kind=? AND NOT archived ORDER BY name LIMIT 100",
            (rs,n)->new CategoryAdvisor.Category(rs.getString("id"),rs.getString("name")),owner,kind);
        var history=jdbc.query("""
            SELECT f.description,f.category_id FROM financial_record f
            JOIN category c ON c.id=f.category_id AND c.owner_id=f.owner_id
            LEFT JOIN bank_entry b ON b.id=f.bank_entry_id AND b.owner_id=f.owner_id
            WHERE f.owner_id=? AND f.kind=? AND f.status='CONFIRMED' AND NOT f.hidden AND NOT c.archived
              AND b.category_source IS DISTINCT FROM 'AUTO'
            ORDER BY f.operation_date DESC,f.id LIMIT 100
            """,(rs,n)->new CategoryAdvisor.Example(rs.getString("description"),rs.getString("category_id")),owner,kind);
        var request=new CategoryAdvisor.Request((String)e.get("description"),categories,history);
        // Only original, still-valid LLM decisions qualify. Reused copies cannot recursively train themselves.
        var remembered=forAutomatic?jdbc.query("""
            SELECT b.description,b.category_id FROM bank_entry b
            JOIN category c ON c.id=b.category_id AND c.owner_id=b.owner_id
            WHERE b.owner_id=? AND b.direction=? AND c.kind=? AND NOT c.archived
              AND b.category_source='AUTO' AND b.category_auto_method='LLM'
              AND b.provider_status='POSTED' AND NOT b.hidden AND b.state<>'IGNORED'
              AND b.movement_id IS NULL AND b.transfer_id IS NULL
              AND b.category_description=b.description AND b.category_direction=b.direction
              AND lower(regexp_replace(btrim(b.description),'\\s+',' ','g'))=?
            ORDER BY b.date DESC,b.id LIMIT 100
            """,(rs,n)->new CategoryAdvisor.Example(rs.getString("description"),rs.getString("category_id")),
            owner,e.get("direction"),kind,CategoryAdvisor.memoryKey((String)e.get("description"))):List.<CategoryAdvisor.Example>of();
        var suggestion=forAutomatic?advisor.suggest(owner,kind,request,remembered):advisor.suggest(owner,kind,request);
        return new Assistance(version(e),current,true,"",suggestion,candidates,transfer,categoryState);
    }
    private Map<UUID,Map<String,Object>> lockEntries(UUID owner,List<UUID> ids) {
        var initial=ids.stream().distinct().map(id->entry(owner,id)).toList();
        initial.stream().map(e->(UUID)e.get("link_id")).distinct().sorted().forEach(link->
            jdbc.queryForObject("SELECT id FROM bank_link WHERE id=? AND owner_id=? FOR UPDATE",UUID.class,link,owner));
        var result=new HashMap<UUID,Map<String,Object>>();
        ids.stream().distinct().sorted().forEach(id->{jdbc.queryForObject("SELECT id FROM bank_entry WHERE id=? AND owner_id=? FOR UPDATE",UUID.class,id,owner); result.put(id,entry(owner,id));});
        return result;
    }
    private static void checkVersion(Map<String,Object> e,long expected) {
        if(version(e)!=expected)throw ApiException.conflict("O registro mudou. Feche os detalhes e consulte novamente.");
    }
    @Transactional public void category(UUID owner,UUID id,CategoryChange input) {
        var e=lockEntries(owner,List.of(id)).get(id);checkVersion(e,input.version());
        if(!eligible(e))throw ApiException.bad("Este registro não pode ser categorizado agora.");
        if(input.categoryId()!=null) {
            var rows=jdbc.queryForList("SELECT kind,archived FROM category WHERE id=? AND owner_id=? FOR UPDATE",input.categoryId(),owner);
            if(rows.isEmpty())throw ApiException.missing();
            if(Boolean.TRUE.equals(rows.getFirst().get("archived")) || !Objects.equals(rows.getFirst().get("kind"),"DEBIT".equals(e.get("direction"))?"EXPENSE":"INCOME"))throw ApiException.bad("Escolha uma categoria ativa compatível.");
        }
        jdbc.update("UPDATE bank_entry SET category_id=?,category_description=?,category_direction=?,category_source='MANUAL',category_auto_blocked=true,category_auto_method=null,category_auto_confidence=null,version=version+1 WHERE id=? AND owner_id=?",
            input.categoryId(),input.categoryId()==null?null:e.get("description"),input.categoryId()==null?null:e.get("direction"),id,owner);
        audit.record(owner,id,"BANK_CATEGORY_CHANGED",input.categoryId()==null?"Categoria removida pelo usuário, sem alterar saldo.":"Categoria escolhida pelo usuário, sem criar lançamento ou alterar saldo.");
    }
    @Transactional public boolean applyAutomatic(UUID owner,UUID id,long expected,CategoryAdvisor.Suggestion suggestion) {
        if(!automatic.activeFor(owner))throw ApiException.missing();
        var e=lockEntries(owner,List.of(id)).get(id);
        boolean current=e.get("category_id")!=null && Objects.equals(e.get("category_description"),e.get("description")) && Objects.equals(e.get("category_direction"),e.get("direction"));
        if(version(e)!=expected || !eligible(e) || current || Boolean.TRUE.equals(e.get("category_auto_blocked")) || "MANUAL".equals(e.get("category_source")))return false;
        jdbc.update("UPDATE bank_entry SET category_auto_attempted_at=? WHERE id=? AND owner_id=?",java.sql.Timestamp.from(clock.instant()),id,owner);
        if(suggestion==null || suggestion.categoryId()==null || !Set.of("HISTORY","RULE","LLM","MEMORY").contains(suggestion.method()) || !Set.of("HIGH","MEDIUM","LOW").contains(suggestion.confidence()))return false;
        UUID category=UUID.fromString(suggestion.categoryId());
        var categories=jdbc.queryForList("SELECT kind,archived FROM category WHERE id=? AND owner_id=? FOR UPDATE",category,owner);
        if(categories.isEmpty() || Boolean.TRUE.equals(categories.getFirst().get("archived")) || !Objects.equals(categories.getFirst().get("kind"),"DEBIT".equals(e.get("direction"))?"EXPENSE":"INCOME"))return false;
        jdbc.update("UPDATE bank_entry SET category_id=?,category_description=?,category_direction=?,category_source='AUTO',category_auto_method=?,category_auto_confidence=?,version=version+1 WHERE id=? AND owner_id=?",
            category,e.get("description"),e.get("direction"),suggestion.method(),suggestion.confidence(),id,owner);
        audit.record(owner,id,"BANK_CATEGORY_AUTO","Categoria automática local revisável; método "+suggestion.method()+". Sem criar lançamento ou alterar saldo.");
        return true;
    }
    @Transactional public void allowAutomatic(UUID owner,UUID id,long expected) {
        if(!automatic.activeFor(owner))throw ApiException.missing();
        var e=lockEntries(owner,List.of(id)).get(id);checkVersion(e,expected);
        boolean valid=e.get("category_id")!=null && Objects.equals(e.get("category_description"),e.get("description")) && Objects.equals(e.get("category_direction"),e.get("direction"));
        if(!eligible(e) || valid)throw ApiException.bad("Remova a categoria antes de permitir nova classificação automática.");
        jdbc.update("UPDATE bank_entry SET category_id=null,category_description=null,category_direction=null,category_source=null,category_auto_blocked=false,category_auto_attempted_at=null,category_auto_method=null,category_auto_confidence=null,version=version+1 WHERE id=? AND owner_id=?",id,owner);
        audit.record(owner,id,"BANK_CATEGORY_AUTO_ALLOWED","Usuário permitiu nova classificação automática local, sem alterar saldo.");
    }
    @Transactional public void confirmTransfer(UUID owner,UUID id,TransferChange input) {
        if(input.peerId()==null || id.equals(input.peerId()))throw ApiException.bad("Escolha outra operação em outra conta própria.");
        var locked=lockEntries(owner,List.of(id,input.peerId())); var a=locked.get(id);var b=locked.get(input.peerId());
        checkVersion(a,input.version());checkVersion(b,input.peerVersion());
        if(!eligible(a)||!eligible(b)||Objects.equals(a.get("account_id"),b.get("account_id"))||Objects.equals(a.get("direction"),b.get("direction"))
            || ((BigDecimal)a.get("amount")).compareTo((BigDecimal)b.get("amount"))!=0 || Math.abs(java.time.temporal.ChronoUnit.DAYS.between(date(a),date(b)))>2)
            throw ApiException.bad("São necessárias operações efetivadas opostas, de mesmo valor, em contas diferentes e até dois dias de diferença.");
        var d="DEBIT".equals(a.get("direction"))?a:b; var c=d==a?b:a; UUID pair=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO bank_transfer_pair(id,owner_id,debit_entry_id,credit_entry_id,amount,debit_date,credit_date,debit_description,credit_description,previous_debit_category,previous_credit_category,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """,pair,owner,d.get("id"),c.get("id"),d.get("amount"),date(d),date(c),d.get("description"),c.get("description"),d.get("category_id"),c.get("category_id"),java.sql.Timestamp.from(clock.instant()));
        for(UUID entryId:List.of(id,input.peerId())) {
            jdbc.update("UPDATE bank_entry SET transfer_id=?,category_id=null,version=version+1 WHERE id=? AND owner_id=?",pair,entryId,owner);
            audit.record(owner,entryId,"BANK_TRANSFER_CONFIRMED","Transferência própria confirmada pelo usuário; fora de renda e gastos, sem alterar saldo.");
        }
    }
    @Transactional public void undoTransfer(UUID owner,UUID id,UndoTransfer input) {
        var e=entry(owner,id);
        if(e.get("transfer_id")==null)throw ApiException.conflict("Este registro não está identificado como transferência.");
        var initial=jdbc.queryForMap("SELECT * FROM bank_transfer_pair WHERE id=? AND owner_id=?",e.get("transfer_id"),owner);
        lockEntries(owner,List.of((UUID)initial.get("debit_entry_id"),(UUID)initial.get("credit_entry_id")));
        var p=jdbc.queryForMap("SELECT * FROM bank_transfer_pair WHERE id=? AND owner_id=? FOR UPDATE",initial.get("id"),owner);
        if(!Boolean.TRUE.equals(p.get("active"))||version(p)!=input.version())throw ApiException.conflict("A identificação mudou. Atualize os detalhes.");
        jdbc.update("UPDATE bank_transfer_pair SET active=false,version=version+1 WHERE id=? AND owner_id=?",p.get("id"),owner);
        for(String side:List.of("debit","credit")) {
            UUID entryId=(UUID)p.get(side+"_entry_id");
            jdbc.update("UPDATE bank_entry SET transfer_id=null,category_id=?,version=version+1 WHERE id=? AND owner_id=? AND transfer_id=?",p.get("previous_"+side+"_category"),entryId,owner,p.get("id"));
            audit.record(owner,entryId,"BANK_TRANSFER_UNDONE","Identificação de transferência desfeita pelo usuário, sem alterar saldo.");
        }
    }
}
