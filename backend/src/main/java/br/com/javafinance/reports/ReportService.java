package br.com.javafinance.reports;
import br.com.javafinance.common.*;
import br.com.javafinance.accounts.AccountBalanceService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class ReportService {
    public record Totals(BigDecimal income,BigDecimal expenses,BigDecimal result,BigDecimal savingsRate) {}
    public record CategoryTotal(UUID id,String name,BigDecimal total) {}
    public record MonthTotal(String month,BigDecimal income,BigDecimal expenses,long records) {}
    public record Upcoming(UUID id,String description,LocalDate dueDate,BigDecimal amount,String kind) {}
    public record Summary(String month,LocalDate today,BigDecimal balance,Totals competence,BigDecimal cashFlow,
        List<CategoryTotal> categories,List<MonthTotal> evolution,List<Upcoming> upcoming,long accountCount,long records,boolean balanceComplete,long unclassifiedBankRecords) {}
    private final JdbcTemplate jdbc; private final Clock clock;
    private final AccountBalanceService balances;
    public ReportService(JdbcTemplate jdbc,Clock clock,AccountBalanceService balances) { this.jdbc=jdbc; this.clock=clock; this.balances=balances; }
    public Summary summary(UUID owner,YearMonth month) {
        if(month.getYear()<1900 || month.getYear()>2200) throw ApiException.bad("Período fora do intervalo suportado.");
        LocalDate today=LocalDate.now(clock);
        Totals totals=totals(owner,month);
        var positions=balances.current(owner).values();
        boolean complete=positions.stream().noneMatch(position->position.amount()==null);
        BigDecimal balance=complete?positions.stream().map(AccountBalanceService.Balance::amount).reduce(new BigDecimal("0.00"),BigDecimal::add):null;
        BigDecimal cash=jdbc.queryForObject("""
            SELECT COALESCE(SUM(CASE WHEN m.kind='EXPENSE' THEN -m.amount ELSE m.amount END),0)::numeric(19,2)
            FROM financial_record m
            WHERE m.owner_id=? AND NOT m.hidden AND m.status='CONFIRMED' AND m.kind NOT IN ('OPENING','TRANSFER')
            AND m.settled_date BETWEEN ? AND ?
            """,BigDecimal.class,owner,month.atDay(1),month.atEndOfMonth());
        var categories=jdbc.query("""
            SELECT c.id,COALESCE(c.name,'Sem categoria') name,SUM(m.amount)::numeric(19,2) total FROM financial_record m
            LEFT JOIN category c ON c.id=m.category_id AND c.owner_id=m.owner_id
            WHERE m.owner_id=? AND NOT m.hidden AND m.status='CONFIRMED' AND m.kind='EXPENSE' AND m.competence=?
            GROUP BY c.id,c.name ORDER BY total DESC,COALESCE(c.name,'Sem categoria')
            """,(rs,n)->new CategoryTotal(rs.getObject("id",UUID.class),rs.getString("name"),rs.getBigDecimal("total")),owner,month.atDay(1));
        var evolution=new ArrayList<MonthTotal>();
        for(int i=5;i>=0;i--) {
            YearMonth period=month.minusMonths(i); Totals t=totals(owner,period);
            long count=jdbc.queryForObject("SELECT count(*) FROM financial_record WHERE owner_id=? AND NOT hidden AND competence=? AND status='CONFIRMED' AND kind IN ('INCOME','EXPENSE')",Long.class,owner,period.atDay(1));
            evolution.add(new MonthTotal(period.toString(),t.income(),t.expenses(),count));
        }
        var upcoming=jdbc.query("""
            SELECT id,description,due_date,amount,kind FROM movement WHERE owner_id=? AND status='PLANNED'
            ORDER BY due_date,id LIMIT 8
            """,(rs,n)->new Upcoming(rs.getObject("id",UUID.class),rs.getString("description"),rs.getObject("due_date",LocalDate.class),rs.getBigDecimal("amount"),rs.getString("kind")),owner);
        long accounts=jdbc.queryForObject("SELECT count(*) FROM account WHERE owner_id=?",Long.class,owner);
        long records=jdbc.queryForObject("SELECT count(*) FROM financial_record WHERE owner_id=? AND NOT hidden AND kind IN ('INCOME','EXPENSE') AND status='CONFIRMED' AND competence=?",Long.class,owner,month.atDay(1));
        long unclassified=jdbc.queryForObject("SELECT count(*) FROM financial_record WHERE owner_id=? AND NOT hidden AND source='BANK' AND category_id IS NULL AND kind IN ('INCOME','EXPENSE') AND status='CONFIRMED' AND competence=?",Long.class,owner,month.atDay(1));
        return new Summary(month.toString(),today,balance,totals,cash,categories,evolution,upcoming,accounts,records,complete,unclassified);
    }
    private Totals totals(UUID owner,YearMonth month) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(amount) FILTER (WHERE kind='INCOME'),0)::numeric(19,2) income,
            COALESCE(SUM(amount) FILTER (WHERE kind='EXPENSE'),0)::numeric(19,2) expenses
            FROM financial_record WHERE owner_id=? AND NOT hidden AND status='CONFIRMED' AND competence=? AND kind IN ('INCOME','EXPENSE')
            """,(rs,n)-> {
                BigDecimal income=rs.getBigDecimal("income"),expenses=rs.getBigDecimal("expenses"),result=income.subtract(expenses);
                return new Totals(income,expenses,result,Money.percent(result,income));
            },owner,month.atDay(1));
    }
}

