package br.com.javafinance.organization;

import br.com.javafinance.budgets.BudgetService;
import br.com.javafinance.common.*;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.*;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PlanningFinanceService {
    public record Input(String netSalary,String monthlyExpenses,@PositiveOrZero long version) {}
    public record Profile(BigDecimal netSalary,BigDecimal monthlyExpenses,long version,boolean saved) {}
    public record Month(String month,BigDecimal income,BigDecimal salary,BigDecimal consumption,long records,long uncategorized) {}
    public record Context(Profile profile,String currentMonth,BigDecimal currentIncome,BigDecimal currentSalary,
        BigDecimal currentConsumption,List<Month> history,int observedMonths,BigDecimal averageIncome,
        BigDecimal averageSalary,BigDecimal averageConsumption,BigDecimal monthlySalary,String salarySource,
        BigDecimal monthlyExpenses,String expenseSource,BigDecimal existingGoalContributions,
        BigDecimal monthlyResult,BigDecimal available,List<String> warnings) {}
    public record Assessment(BigDecimal requiredMonthly,BigDecimal additionalSavingNeeded,
        BigDecimal maximumMonthlyConsumption,String minimumMonths,boolean canAssess,boolean fitsEstimate) {}
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    private final JdbcTemplate jdbc;
    private final BudgetService budgets;
    private final AuditService audit;
    private final Clock clock;
    public PlanningFinanceService(JdbcTemplate jdbc,BudgetService budgets,AuditService audit,Clock clock){
        this.jdbc=jdbc;this.budgets=budgets;this.audit=audit;this.clock=clock;
    }
    private static BigDecimal optional(String raw){
        if(raw==null||raw.isBlank())return null;
        var value=Money.parse(raw);if(value.signum()<0)throw ApiException.bad("Informe valores não negativos.");return value;
    }
    public Profile profile(UUID owner){
        return jdbc.query("SELECT net_salary,monthly_expenses,version FROM planning_income_profile WHERE owner_id=?",
            (rs,n)->new Profile(rs.getBigDecimal(1),rs.getBigDecimal(2),rs.getLong(3),true),owner)
            .stream().findFirst().orElse(new Profile(null,null,0,false));
    }
    @Transactional public void save(UUID owner,Input input){
        var salary=optional(input.netSalary());var expenses=optional(input.monthlyExpenses());
        jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",UUID.class,owner);
        var old=profile(owner);
        if(old.version()!=input.version())throw ApiException.conflict("O perfil de planejamento mudou. Atualize a página.");
        if(!old.saved())jdbc.update("INSERT INTO planning_income_profile(owner_id,net_salary,monthly_expenses,version) VALUES (?,?,?,1)",owner,salary,expenses);
        else jdbc.update("UPDATE planning_income_profile SET net_salary=?,monthly_expenses=?,version=version+1,updated_at=now() WHERE owner_id=?",salary,expenses,owner);
        audit.record(owner,owner,"PLANNING_PROFILE_UPDATED","Previsões pessoais atualizadas; sem criar renda, despesa ou alterar saldos.");
    }
    private static String key(String text){return Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Context context(UUID owner){
        var month=YearMonth.now(clock);var start=month.minusMonths(3);var p=profile(owner);
        // Category names are an indication, never a claim that every incoming PIX is salary.
        var salaryIds=jdbc.query("SELECT id,name FROM category WHERE owner_id=? AND kind='INCOME'",(rs,n)->Map.entry(rs.getObject(1,UUID.class),rs.getString(2)),owner)
            .stream().filter(c->Set.of("salario","salarios","salario liquido").contains(key(c.getValue()))).map(Map.Entry::getKey).toList();
        String salaryFilter=salaryIds.isEmpty()?"false":"category_id IN ("+String.join(",",Collections.nCopies(salaryIds.size(),"?"))+")";
        String query="""
            SELECT competence,coalesce(sum(amount) FILTER(WHERE kind='INCOME'),0) income,
              coalesce(sum(amount) FILTER(WHERE kind='INCOME' AND %s),0) salary,
              coalesce(sum(amount) FILTER(WHERE kind='EXPENSE'),0) consumption,count(*) records,
              count(*) FILTER(WHERE category_id IS NULL) uncategorized
            FROM consumption_record WHERE owner_id=? AND NOT hidden AND status='CONFIRMED'
              AND kind IN ('INCOME','EXPENSE') AND competence BETWEEN ? AND ?
            GROUP BY competence ORDER BY competence
            """.formatted(salaryFilter);
        var args=new ArrayList<Object>(salaryIds);args.add(owner);args.add(start.atDay(1));args.add(month.atDay(1));
        var rows=jdbc.query(query,(rs,n)->new Month(YearMonth.from(rs.getObject(1,LocalDate.class)).toString(),rs.getBigDecimal(2),rs.getBigDecimal(3),rs.getBigDecimal(4),rs.getLong(5),rs.getLong(6)),args.toArray());
        var history=rows.stream().filter(r->!r.month().equals(month.toString())).toList();
        var current=rows.stream().filter(r->r.month().equals(month.toString())).findFirst().orElse(new Month(month.toString(),ZERO,ZERO,ZERO,0,0));
        int observed=history.size();
        var income=average(history.stream().map(Month::income).toList());
        var salary=average(history.stream().map(Month::salary).toList());
        var consumption=average(history.stream().map(Month::consumption).toList());
        boolean salaryObserved=history.stream().anyMatch(r->r.salary().signum()>0);
        boolean expenseObserved=history.stream().anyMatch(r->r.consumption().signum()>0);
        BigDecimal monthlySalary=p.netSalary()!=null?p.netSalary():salaryObserved?salary:null;
        String salarySource=p.netSalary()!=null?"USER":salaryObserved?"HISTORY":"UNKNOWN";
        BigDecimal expenseReference=p.monthlyExpenses()!=null?p.monthlyExpenses():expenseObserved?consumption:null;
        String expenseSource=p.monthlyExpenses()!=null?"USER":expenseObserved?"HISTORY":"UNKNOWN";
        var budget=budgets.month(owner,month).totals();
        var currentObligations=current.consumption().add(budget.planned()).add(budget.unbudgetedPlanned());
        var budgetEnvelope=budget.limit().add(budget.unbudgetedSpent()).add(budget.unbudgetedPlanned());
        BigDecimal monthlyExpenses=expenseReference==null?null:expenseReference.max(currentObligations).max(budgetEnvelope);
        BigDecimal existingGoals=jdbc.queryForObject("SELECT coalesce(sum(ceil(greatest(target-saved,0)*100/months)/100),0) FROM savings_goal WHERE owner_id=? AND target>saved",BigDecimal.class,owner);
        BigDecimal result=monthlySalary==null||monthlyExpenses==null?null:monthlySalary.subtract(monthlyExpenses).subtract(existingGoals);
        var warnings=new ArrayList<String>();
        if(observed<3)warnings.add("Há "+observed+" de 3 meses anteriores com registros. Mês sem dados não foi tratado como gasto zero.");
        if(monthlySalary==null)warnings.add("Salário líquido mensal não identificado. Informe a previsão; outras entradas não são consideradas salário automaticamente.");
        else if(salarySource.equals("HISTORY"))warnings.add("Salário estimado pelos registros na categoria Salário; confirme o valor líquido previsto.");
        if(expenseReference==null)warnings.add("Gastos mensais ainda desconhecidos. Informe uma previsão ou complete o histórico para avaliar a capacidade de poupar.");
        if(history.stream().anyMatch(r->r.uncategorized()>0)||current.uncategorized()>0)warnings.add("Há registros sem categoria. Aplicações, faturas e transferências ainda não confirmadas podem distorcer os indicadores.");
        if(expenseReference!=null&&monthlyExpenses.compareTo(expenseReference)>0)warnings.add("Gastos e compromissos do mês ou limites de orçamento superam a referência mensal; usamos o maior valor.");
        warnings.add("Estimativas usam registros disponíveis, não comprovam dinheiro livre ou histórico bancário completo. Sem juros presumidos ou movimentação de dinheiro.");
        return new Context(p,month.toString(),current.income(),current.salary(),current.consumption(),history,observed,income,salary,consumption,monthlySalary,salarySource,monthlyExpenses,expenseSource,existingGoals,result,result==null?null:result.max(ZERO),warnings);
    }
    private static BigDecimal average(List<BigDecimal> values){return values.isEmpty()?null:values.stream().reduce(ZERO,BigDecimal::add).divide(BigDecimal.valueOf(values.size()),2,RoundingMode.HALF_UP);}
    public Assessment assess(Context context,BigDecimal target,BigDecimal saved,int months){
        var remaining=target.subtract(saved).max(ZERO);
        var required=remaining.divide(BigDecimal.valueOf(months),2,RoundingMode.CEILING);
        if(context.available()==null)return new Assessment(required,null,null,null,false,false);
        var additional=required.subtract(context.available()).max(ZERO);
        var ceiling=context.monthlySalary().subtract(context.existingGoalContributions()).subtract(required).max(ZERO);
        String minimum=remaining.signum()==0?"0":context.available().signum()>0?remaining.divide(context.available(),0,RoundingMode.CEILING).toPlainString():null;
        return new Assessment(required,additional,ceiling,minimum,true,required.compareTo(context.available())<=0);
    }
}
