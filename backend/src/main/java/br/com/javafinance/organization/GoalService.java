package br.com.javafinance.organization;
import br.com.javafinance.common.*;
import br.com.javafinance.banking.CategoryAdvisor;
import br.com.javafinance.reports.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class GoalService {
    public record State(String target,String saved,Integer months,String title) {}
    public record Chat(@NotBlank @Size(max=600) String message,@Valid State state) {}
    public record Reply(String message,State state,BigDecimal monthly,BigDecimal available,boolean ready,String method) {}
    public record Save(@NotBlank @Size(max=100) String title,@NotBlank String target,@NotBlank String saved,@Min(1) @Max(120) int months,boolean confirmed) {}
    public record Goal(UUID id,String title,BigDecimal target,BigDecimal saved,int months,BigDecimal monthly,long version) {}
    public record Progress(@NotBlank String saved,@PositiveOrZero long version) {}
    private final JdbcTemplate jdbc;private final AuditService audit;private final CategoryAdvisor advisor;private final ReportService reports;private final Clock clock;
    private final com.github.benmanes.caffeine.cache.Cache<UUID,java.util.concurrent.atomic.AtomicInteger> chatRequests=com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofMinutes(1)).build();
    public GoalService(JdbcTemplate jdbc,AuditService audit,CategoryAdvisor advisor,ReportService reports,Clock clock){this.jdbc=jdbc;this.audit=audit;this.advisor=advisor;this.reports=reports;this.clock=clock;}
    private static BigDecimal value(String raw){var v=Money.parse(raw);if(v.signum()<0)throw ApiException.bad("Use valores não negativos.");return v;}
    private static BigDecimal monthly(BigDecimal target,BigDecimal saved,int months){return target.subtract(saved).max(BigDecimal.ZERO).divide(BigDecimal.valueOf(months),2,RoundingMode.CEILING);}
    public Reply chat(UUID owner,Chat input){
        if(chatRequests.get(owner,key->new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet()>20)throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"Aguarde um minuto antes de continuar a conversa.");
        State old=input.state()==null?new State(null,null,null,"Minha meta"):input.state();
        if(old.title()!=null&&old.title().length()>100 || old.target()!=null&&old.target().length()>24 || old.saved()!=null&&old.saved().length()>24 || old.months()!=null&&(old.months()<1||old.months()>120))throw ApiException.bad("Contexto da meta inválido.");
        var map=new HashMap<String,Object>();map.put("target",old.target());map.put("saved",old.saved());map.put("months",old.months());map.put("title",old.title()==null?"Minha meta":old.title());
        CategoryAdvisor.GoalIntent intent;
        try{intent=advisor.interpretGoal(owner,Map.of("message",input.message(),"state",map));}
        catch(CategoryAdvisor.TemporarilyUnavailable ex){throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"O interpretador de metas está indisponível. Tente novamente; nada foi salvo.");}
        var state=new State(intent.target(),intent.saved(),intent.months(),intent.title());
        var available=reports.summary(owner,YearMonth.now(clock)).competence().result().max(BigDecimal.ZERO);
        if("SUMMARY".equals(intent.action()))return new Reply("A sobra registrada no mês é R$ "+available.toPlainString()+". É uma estimativa: confirme naturezas financeiras e complete registros e limites antes de usá-la para decisões.",state,null,available,false,intent.method());
        if(intent.target()==null)return new Reply("Quanto você quer guardar? Exemplo: quero R$ 3000 para uma viagem.",state,null,available,false,intent.method());
        var target=value(intent.target());if(target.signum()==0)throw ApiException.bad("A meta deve ser maior que zero.");
        if("PURCHASE".equals(intent.action()))return new Reply(target.compareTo(available)<=0?"A compra cabe na sobra registrada deste mês, mas isso não comprova dinheiro livre: confira limites, metas, compromissos e reserva antes de decidir.":"A compra supera a sobra registrada neste mês. Podemos montar uma meta para planejar antes de comprar.",state,null,available,false,intent.method());
        if(intent.months()==null)return new Reply("Em quantos meses quer atingir essa meta? Exemplo: em 6 meses. Você também pode informar quanto já tem reservado.",state,null,available,false,intent.method());
        if(intent.months()<1||intent.months()>120)throw ApiException.bad("Escolha entre 1 e 120 meses.");
        var saved=value(intent.saved()==null?"0":intent.saved());var monthly=monthly(target,saved,intent.months());
        String message="Para "+intent.title()+", reserve R$ "+monthly.toPlainString()+" por mês durante "+intent.months()+" meses. O cálculo não presume juros e não movimenta dinheiro. "+(monthly.compareTo(available)>0?"Esse aporte supera a sobra registrada; aumente o prazo ou reveja seus limites.":"O aporte cabe na sobra registrada; ainda confira compromissos e reserva.")+" Os dados podem estar incompletos. Salve a meta somente se concordar.";
        return new Reply(message,new State(intent.target(),saved.toPlainString(),intent.months(),intent.title()),monthly,available,true,intent.method());
    }
    public List<Goal> list(UUID owner){return jdbc.query("SELECT * FROM savings_goal WHERE owner_id=? ORDER BY created_at DESC,id",(rs,n)->new Goal(rs.getObject("id",UUID.class),rs.getString("title"),rs.getBigDecimal("target"),rs.getBigDecimal("saved"),rs.getInt("months"),monthly(rs.getBigDecimal("target"),rs.getBigDecimal("saved"),rs.getInt("months")),rs.getLong("version")),owner);}
    @Transactional public void save(UUID owner,Save input){
        if(!input.confirmed())throw ApiException.bad("Confirme para salvar a meta.");var target=value(input.target());var saved=value(input.saved());if(target.signum()==0)throw ApiException.bad("A meta deve ser positiva.");
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO savings_goal(id,owner_id,title,target,saved,months) VALUES (?,?,?,?,?,?)",id,owner,input.title().strip(),target,saved,input.months());
        audit.record(owner,id,"SAVINGS_GOAL_CREATED","Meta de planejamento salva; sem reserva ou transferência automática.");
    }
    @Transactional public void progress(UUID owner,UUID id,Progress input){
        var rows=jdbc.queryForList("SELECT version FROM savings_goal WHERE id=? AND owner_id=? FOR UPDATE",id,owner);if(rows.isEmpty())throw ApiException.missing();
        if(((Number)rows.getFirst().get("version")).longValue()!=input.version())throw ApiException.conflict("A meta mudou. Atualize.");
        jdbc.update("UPDATE savings_goal SET saved=?,version=version+1 WHERE id=? AND owner_id=?",value(input.saved()),id,owner);audit.record(owner,id,"SAVINGS_GOAL_PROGRESS","Progresso informado pelo usuário; sem alteração de saldo.");
    }
}
