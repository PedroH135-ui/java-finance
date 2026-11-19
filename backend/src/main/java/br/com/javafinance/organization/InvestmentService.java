package br.com.javafinance.organization;
import br.com.javafinance.common.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class InvestmentService {
    public record Position(UUID id,String name,String institution,String instrument,BigDecimal reportedValue,LocalDate valueDate,long version) {}
    public record Input(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=100) String institution,@NotBlank String instrument,@NotBlank String reportedValue,@NotNull LocalDate valueDate,@PositiveOrZero long version) {}
    public record Flow(UUID id,String description,BigDecimal amount,LocalDate date,String nature,String instrument,UUID positionId) {}
    public record Suggested(UUID id,String description,String nature,String instrument) {}
    public record Summary(List<Position> positions,List<Flow> flows,long totalFlows,BigDecimal contributions,BigDecimal withdrawals,BigDecimal netContributions,List<Suggested> suggestions) {}
    private final JdbcTemplate jdbc;private final AuditService audit;private final FinancialNatureService natures;
    public InvestmentService(JdbcTemplate jdbc,AuditService audit,FinancialNatureService natures){this.jdbc=jdbc;this.audit=audit;this.natures=natures;}
    public List<Position> positions(UUID owner){return jdbc.query("SELECT id,name,institution,instrument,reported_value,value_date,version FROM investment_position WHERE owner_id=? ORDER BY institution,name,id",(rs,n)->new Position(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("institution"),rs.getString("instrument"),rs.getBigDecimal("reported_value"),rs.getObject("value_date",LocalDate.class),rs.getLong("version")),owner);}
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Summary summary(UUID owner){
        return summary(owner,LocalDate.of(1900,1,1),LocalDate.of(2200,12,31));
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Summary summary(UUID owner,LocalDate from,LocalDate to){
        if(from==null||to==null||to.isBefore(from))throw ApiException.bad("Período inválido.");
        var positions=jdbc.query("SELECT * FROM investment_position WHERE owner_id=? ORDER BY name,id",(rs,n)->new Position(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("institution"),rs.getString("instrument"),rs.getBigDecimal("reported_value"),rs.getObject("value_date",LocalDate.class),rs.getLong("version")),owner);
        String where=" FROM bank_entry b JOIN valid_financial_nature n ON n.entry_id=b.id AND n.owner_id=b.owner_id WHERE b.owner_id=? AND NOT b.hidden AND n.nature IN ('INVESTMENT_IN','INVESTMENT_OUT') AND b.date BETWEEN ? AND ?";
        var flows=jdbc.query("SELECT b.id,b.description,b.amount,b.date,n.nature,n.instrument,n.position_id"+where+" ORDER BY b.date DESC,b.id LIMIT 100",(rs,i)->new Flow(rs.getObject(1,UUID.class),rs.getString(2),rs.getBigDecimal(3),rs.getObject(4,LocalDate.class),rs.getString(5),rs.getString(6),rs.getObject(7,UUID.class)),owner,from,to);
        var totals=jdbc.queryForMap("SELECT count(*) count,coalesce(sum(b.amount) FILTER(WHERE n.nature='INVESTMENT_IN'),0) contributions,coalesce(sum(b.amount) FILTER(WHERE n.nature='INVESTMENT_OUT'),0) withdrawals"+where,owner,from,to);
        var in=(BigDecimal)totals.get("contributions");var out=(BigDecimal)totals.get("withdrawals");
        var rules=natures.rules();
        var suggestions=jdbc.query("SELECT b.id,b.description,b.direction FROM bank_entry b LEFT JOIN valid_financial_nature n ON n.entry_id=b.id AND n.owner_id=b.owner_id WHERE b.owner_id=? AND NOT b.hidden AND b.provider_status='POSTED' AND b.state<>'IGNORED' AND b.movement_id IS NULL AND b.transfer_id IS NULL AND n.entry_id IS NULL AND b.date BETWEEN ? AND ? AND EXISTS(SELECT 1 FROM financial_knowledge k WHERE k.active AND k.nature LIKE 'INVESTMENT_%' AND k.direction=b.direction AND financial_description_terms(b.description) @> string_to_array(k.match_text,' ')) ORDER BY b.date DESC,b.id LIMIT 100",(rs,i)->{
            var k=natures.recognize(rs.getString(2),rs.getString(3),rules);return k!=null&&k.nature().startsWith("INVESTMENT_")?new Suggested(rs.getObject(1,UUID.class),rs.getString(2),k.nature(),k.instrument()):null;
        },owner,from,to).stream().filter(Objects::nonNull).toList();
        return new Summary(positions,flows,((Number)totals.get("count")).longValue(),in,out,in.subtract(out),suggestions);
    }
    @Transactional public void save(UUID owner,UUID id,Input input){
        var value=Money.parse(input.reportedValue());
        if(value.signum()<0 || !Set.of("RDB","CDB","OTHER").contains(input.instrument()) || input.valueDate().getYear()<1900 || input.valueDate().getYear()>2200)throw ApiException.bad("Revise produto, data e valor informado.");
        UUID resource=id==null?UUID.randomUUID():id;
        if(id==null)jdbc.update("INSERT INTO investment_position(id,owner_id,name,institution,instrument,reported_value,value_date) VALUES (?,?,?,?,?,?,?)",resource,owner,input.name().strip(),input.institution().strip(),input.instrument(),value,input.valueDate());
        else {
            var found=jdbc.queryForList("SELECT version,instrument FROM investment_position WHERE id=? AND owner_id=? FOR UPDATE",id,owner);
            if(found.isEmpty())throw ApiException.missing();
            if(((Number)found.getFirst().get("version")).longValue()!=input.version())throw ApiException.conflict("O investimento mudou. Atualize.");
            if(!found.getFirst().get("instrument").equals(input.instrument()))throw ApiException.bad("O tipo do produto não pode ser trocado; crie outro cadastro.");
            jdbc.update("UPDATE investment_position SET name=?,institution=?,reported_value=?,value_date=?,version=version+1 WHERE id=? AND owner_id=?",input.name().strip(),input.institution().strip(),value,input.valueDate(),id,owner);
        }
        audit.record(owner,resource,"INVESTMENT_POSITION_SAVED","Posição informativa salva; sem pagamentos, movimentações ou alteração do saldo bancário.");
    }
}
