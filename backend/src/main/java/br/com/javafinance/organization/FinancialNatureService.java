package br.com.javafinance.organization;

import br.com.javafinance.common.*;
import java.text.Normalizer;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialNatureService {
    public enum Nature {ORDINARY,OWN_TRANSFER,CARD_PAYMENT,INVESTMENT_IN,INVESTMENT_OUT}
    public record Knowledge(String nature,String instrument,String explanation,String sourceUrl) {}
    public record Rule(String matchText,String direction,Knowledge knowledge) {}
    public record Status(String confirmedNature,String instrument,UUID positionId,boolean changed,Knowledge suggestion) {}
    public record Change(@PositiveOrZero long version,@NotNull Nature nature,@NotBlank String instrument,UUID positionId,boolean confirmed) {}
    private final JdbcTemplate jdbc;private final AuditService audit;
    public FinancialNatureService(JdbcTemplate jdbc,AuditService audit){this.jdbc=jdbc;this.audit=audit;}
    public Knowledge recognize(String description,String direction){
        return recognize(description,direction,rules());
    }
    public List<Rule> rules(){return jdbc.query("SELECT * FROM financial_knowledge WHERE active ORDER BY length(match_text) DESC,id",(rs,n)->new Rule(rs.getString("match_text"),rs.getString("direction"),new Knowledge(rs.getString("nature"),rs.getString("instrument"),rs.getString("explanation"),rs.getString("source_url"))));}
    public Knowledge recognize(String description,String direction,List<Rule> rules){
        String text=Normalizer.normalize(description,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
        Set<String> terms=new HashSet<>(Arrays.asList(text.replaceAll("[^a-z0-9]+"," ").strip().split("\\s+")));
        return rules.stream().filter(r->r.direction().equals(direction)&&terms.containsAll(Arrays.asList(r.matchText().split(" ")))).map(Rule::knowledge).findFirst().orElse(null);
    }
    public Status status(UUID owner,UUID entry){
        var rows=jdbc.queryForList("SELECT b.description,b.direction,n.nature,n.instrument,n.position_id,n.entry_id IS NOT NULL AND v.entry_id IS NULL changed FROM bank_entry b LEFT JOIN bank_financial_nature n ON n.entry_id=b.id AND n.owner_id=b.owner_id LEFT JOIN valid_financial_nature v ON v.entry_id=b.id AND v.owner_id=b.owner_id WHERE b.id=? AND b.owner_id=?",entry,owner);
        if(rows.isEmpty())throw ApiException.missing();var r=rows.getFirst();boolean changed=Boolean.TRUE.equals(r.get("changed"));
        return new Status(changed?null:(String)r.get("nature"),(String)r.get("instrument"),(UUID)r.get("position_id"),changed,recognize((String)r.get("description"),(String)r.get("direction")));
    }
    @Transactional public void confirm(UUID owner,UUID entry,Change change){
        if(!change.confirmed())throw ApiException.bad("Confirme explicitamente a natureza financeira.");
        var rows=jdbc.queryForList("SELECT *,md5(concat_ws('|',description,amount::text,date::text,direction,provider_status)) signature FROM bank_entry WHERE id=? AND owner_id=? FOR UPDATE",entry,owner);
        if(rows.isEmpty())throw ApiException.missing();var r=rows.getFirst();
        if(((Number)r.get("version")).longValue()!=change.version())throw ApiException.conflict("O registro mudou. Atualize a página.");
        if(r.get("movement_id")!=null || !"POSTED".equals(r.get("provider_status")) || "IGNORED".equals(r.get("state")) || Boolean.TRUE.equals(r.get("hidden")))throw ApiException.bad("Use um registro bancário efetivado e visível, sem lançamento associado.");
        if(r.get("transfer_id")!=null)throw ApiException.bad("Desfaça primeiro o vínculo da transferência existente para mudar a natureza.");
        boolean investment=Set.of(Nature.INVESTMENT_IN,Nature.INVESTMENT_OUT).contains(change.nature());
        if(!Set.of("NONE","RDB","CDB","OTHER").contains(change.instrument()) || (investment && "NONE".equals(change.instrument())))throw ApiException.bad("Informe o tipo do investimento.");
        if((change.nature()==Nature.INVESTMENT_IN || change.nature()==Nature.CARD_PAYMENT) && !"DEBIT".equals(r.get("direction")) || change.nature()==Nature.INVESTMENT_OUT && !"CREDIT".equals(r.get("direction")))throw ApiException.bad("Natureza incompatível com a direção do registro.");
        if(change.positionId()!=null){
            if(!investment || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM investment_position WHERE id=? AND owner_id=? AND instrument=?)",Boolean.class,change.positionId(),owner,change.instrument())))throw ApiException.missing();
        }
        jdbc.update("INSERT INTO bank_financial_nature(entry_id,owner_id,nature,instrument,position_id,entry_signature) VALUES (?,?,?,?,?,?) ON CONFLICT(entry_id) DO UPDATE SET nature=EXCLUDED.nature,instrument=EXCLUDED.instrument,position_id=EXCLUDED.position_id,entry_signature=EXCLUDED.entry_signature,confirmed_at=now()",entry,owner,change.nature().name(),investment?change.instrument():"NONE",investment?change.positionId():null,r.get("signature"));
        jdbc.update("UPDATE bank_entry SET version=version+1 WHERE id=? AND owner_id=?",entry,owner);
        audit.record(owner,entry,"FINANCIAL_NATURE_CONFIRMED","Natureza confirmada pelo usuário; somente indicadores, sem alterar saldos.");
    }
}
