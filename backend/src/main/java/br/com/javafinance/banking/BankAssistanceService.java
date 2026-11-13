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
        CategoryAdvisor.Suggestion categorySuggestion,List<Peer> transferCandidates,Transfer transfer) {}
    public record CategoryChange(@PositiveOrZero long version,UUID categoryId) {}
    public record TransferChange(@PositiveOrZero long version,@NotNull UUID peerId,@PositiveOrZero long peerVersion) {}
    public record UndoTransfer(@PositiveOrZero long version) {}
    private final JdbcTemplate jdbc; private final CategoryAdvisor advisor; private final AuditService audit; private final Clock clock;
    public BankAssistanceService(JdbcTemplate jdbc,CategoryAdvisor advisor,AuditService audit,Clock clock) {
        this.jdbc=jdbc;this.advisor=advisor;this.audit=audit;this.clock=clock;
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
        var none=new CategoryAdvisor.Suggestion(null,"NONE","NONE","Não há sugestão para este registro.",0,false);
        String unavailable=can?"":transfer!=null?"Identificado como transferência. Desfaça essa identificação antes de categorizar.":"Categorize apenas registros efetivados, visíveis e sem lançamento associado. Para um lançamento associado, utilize seus detalhes.";
        if(!can || current!=null)return new Assistance(version(e),current,can,unavailable,none,candidates,transfer);
        String kind="DEBIT".equals(e.get("direction"))?"EXPENSE":"INCOME";
        var categories=jdbc.query("SELECT id,name FROM category WHERE owner_id=? AND kind=? AND NOT archived ORDER BY name LIMIT 100",
            (rs,n)->new CategoryAdvisor.Category(rs.getString("id"),rs.getString("name")),owner,kind);
        var history=jdbc.query("""
            SELECT f.description,f.category_id FROM financial_record f
            JOIN category c ON c.id=f.category_id AND c.owner_id=f.owner_id
            WHERE f.owner_id=? AND f.kind=? AND f.status='CONFIRMED' AND NOT f.hidden AND NOT c.archived
            ORDER BY f.operation_date DESC,f.id LIMIT 100
            """,(rs,n)->new CategoryAdvisor.Example(rs.getString("description"),rs.getString("category_id")),owner,kind);
        var suggestion=advisor.suggest(new CategoryAdvisor.Request((String)e.get("description"),categories,history));
        return new Assistance(version(e),current,true,"",suggestion,candidates,transfer);
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
        jdbc.update("UPDATE bank_entry SET category_id=?,category_description=?,category_direction=?,version=version+1 WHERE id=? AND owner_id=?",
            input.categoryId(),input.categoryId()==null?null:e.get("description"),input.categoryId()==null?null:e.get("direction"),id,owner);
        audit.record(owner,id,"BANK_CATEGORY_CHANGED",input.categoryId()==null?"Categoria removida pelo usuário, sem alterar saldo.":"Categoria escolhida pelo usuário, sem criar lançamento ou alterar saldo.");
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
