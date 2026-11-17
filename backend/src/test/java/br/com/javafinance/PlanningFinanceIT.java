package br.com.javafinance;

import br.com.javafinance.accounts.*;
import br.com.javafinance.banking.CategoryAdvisor;
import br.com.javafinance.budgets.BudgetService;
import br.com.javafinance.common.ApiException;
import br.com.javafinance.movements.*;
import br.com.javafinance.organization.*;
import br.com.javafinance.users.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.registration-enabled=true") @AutoConfigureMockMvc
class PlanningFinanceIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){TestDatabase.configure(r);}
    @Autowired PlanningFinanceService finances;@Autowired GoalService goals;@Autowired UserService users;
    @Autowired AccountService accounts;@Autowired JdbcTemplate jdbc;@Autowired Clock clock;@Autowired MockMvc mvc;
    @Autowired BudgetService budgets;@Autowired MovementService movements;@Autowired FinancialNatureService natures;
    @MockitoBean CategoryAdvisor advisor;
    UUID owner,account,link,salary,market;YearMonth month;
    UUID register(){return users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Planejamento fictício","UmaSenhaSegura123!")).id();}
    @BeforeEach void setup(){
        owner=register();month=YearMonth.now(clock);
        account=accounts.create(owner,new AccountService.Create("Banco fictício","",Account.Kind.CHECKING,"0",month.minusMonths(6).atDay(1))).id();
        link=UUID.randomUUID();jdbc.update("INSERT INTO bank_link(id,owner_id,item_id,external_account_id,account_id,name,import_from,reported_balance) VALUES (?,?,?,?,?,?,?,1000)",link,owner,UUID.randomUUID(),UUID.randomUUID(),account,"Fictício",month.minusMonths(5).atDay(1));
        salary=jdbc.queryForObject("SELECT id FROM category WHERE owner_id=? AND name='Salário'",UUID.class,owner);
        market=jdbc.queryForObject("SELECT id FROM category WHERE owner_id=? AND name='Mercado'",UUID.class,owner);
        when(advisor.interpretGoal(eq(owner),anyMap())).thenReturn(new CategoryAdvisor.GoalIntent("3000","600",6,"Viagem","GOAL",false,"LANGCHAIN_RULES"));
    }
    UUID entry(YearMonth period,String description,String amount,String direction,UUID category){
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status,category_id,category_description,category_direction,category_source) VALUES (?,?,?,?,?,?,?,?, 'POSTED',?,?,?,?)",id,owner,link,id.toString(),description,new BigDecimal(amount),period.atDay(2),direction,category,category==null?null:description,category==null?null:direction,category==null?null:"MANUAL");return id;
    }
    Object money(){return List.of(accounts.list(owner),jdbc.queryForList("SELECT * FROM movement WHERE owner_id=? ORDER BY id",owner),jdbc.queryForList("SELECT * FROM posting WHERE owner_id=? ORDER BY id",owner),jdbc.queryForList("SELECT * FROM bank_entry WHERE owner_id=? ORDER BY id",owner));}
    @Test void salaryIsSeparateFromOtherIncomeAndCurrentPartialMonthIsNotAveraged(){
        var a=month.minusMonths(2);var b=month.minusMonths(1);
        entry(a,"Salário fictício","3000","CREDIT",salary);entry(a,"PIX recebido fictício","500","CREDIT",null);entry(a,"Mercado fictício","2700","DEBIT",market);
        entry(b,"Salário fictício","3200","CREDIT",salary);entry(b,"Outra receita fictícia","200","CREDIT",null);entry(b,"Mercado fictício","2500","DEBIT",market);
        entry(month,"Recebimento eventual fictício","10000","CREDIT",null);
        var c=finances.context(owner);
        assertThat(c.observedMonths()).isEqualTo(2);assertThat(c.averageIncome()).isEqualByComparingTo("3450");
        assertThat(c.averageSalary()).isEqualByComparingTo("3100");assertThat(c.averageConsumption()).isEqualByComparingTo("2600");
        assertThat(c.monthlySalary()).isNull();assertThat(c.available()).isNull();
        assertThat(c.currentIncome()).isEqualByComparingTo("10000");assertThat(c.salarySource()).isEqualTo("UNKNOWN");assertThat(c.warnings()).anyMatch(w->w.contains("2 de 3"));
    }
    @Test void receivedPixNeverBecomesSalaryAndProfileMakesGoalGapExplicitWithoutChangingMoney(){
        entry(month.minusMonths(1),"PIX recebido fictício","5000","CREDIT",null);entry(month.minusMonths(1),"Consumo fictício","2700","DEBIT",market);
        assertThat(finances.context(owner).monthlySalary()).isNull();assertThat(finances.context(owner).available()).isNull();var before=money();
        finances.save(owner,new PlanningFinanceService.Input("3000","2700",0));
        var reply=goals.chat(owner,new GoalService.Chat("Planejar viagem",null));
        assertThat(reply.monthly()).isEqualByComparingTo("400");assertThat(reply.available()).isEqualByComparingTo("300");
        assertThat(reply.assessment().additionalSavingNeeded()).isEqualByComparingTo("100");
        assertThat(reply.assessment().maximumMonthlyConsumption()).isEqualByComparingTo("2600");assertThat(reply.assessment().minimumMonths()).isEqualTo("8");
        assertThat(reply.assessment().fitsEstimate()).isFalse();assertThat(reply.message()).contains("Faltam R$ 100.00");assertThat(money()).isEqualTo(before);assertThat(goals.list(owner)).isEmpty();
        verify(advisor).interpretGoal(eq(owner),argThat(payload->!payload.containsKey("salary")&&!payload.containsKey("finances")&&!payload.containsKey("expenses")));
    }
    @Test void budgetsUnbudgetedSpendingAndOtherGoalsReduceCapacityOnce(){
        finances.save(owner,new PlanningFinanceService.Input("3000","2000",0));
        entry(month,"Despesa sem limite fictícia","100","DEBIT",null);
        budgets.save(owner,month,market,new BudgetService.Save("2400",null));
        goals.save(owner,new GoalService.Save("Outra meta fictícia","600","0",6,true));var before=money();
        var c=finances.context(owner);assertThat(c.monthlyExpenses()).isEqualByComparingTo("2500");
        assertThat(c.existingGoalContributions()).isEqualByComparingTo("100");assertThat(c.available()).isEqualByComparingTo("400");
        var assessment=finances.assess(c,new BigDecimal("3000"),new BigDecimal("600"),6);
        assertThat(assessment.fitsEstimate()).isTrue();assertThat(assessment.additionalSavingNeeded()).isZero();assertThat(money()).isEqualTo(before);
    }
    @Test void futurePlannedBillsAndZeroSurplusCannotBeIgnoredByExtendingDeadline(){
        finances.save(owner,new PlanningFinanceService.Input("3000","2000",0));
        movements.create(owner,new MovementDtos.Create(UUID.randomUUID(),"Conta futura fictícia","3200",Movement.Kind.EXPENSE,Movement.Status.PLANNED,account,null,market,month.atDay(2),month.atDay(1),month.atEndOfMonth(),null,""));var before=money();
        var c=finances.context(owner);assertThat(c.monthlyExpenses()).isEqualByComparingTo("3200");assertThat(c.monthlyResult()).isEqualByComparingTo("-200");assertThat(c.available()).isZero();
        var assessment=finances.assess(c,new BigDecimal("100"),BigDecimal.ZERO,120);assertThat(assessment.minimumMonths()).isNull();assertThat(assessment.fitsEstimate()).isFalse();assertThat(money()).isEqualTo(before);
    }
    @Test void onlyConfirmedInvestmentNatureChangesTheSpendingReference(){
        finances.save(owner,new PlanningFinanceService.Input("3000",null,0));
        entry(month.minusMonths(1),"Salário fictício","3000","CREDIT",salary);entry(month.minusMonths(1),"Consumo fictício","1000","DEBIT",market);
        var investment=entry(month.minusMonths(1),"Aplicação RDB","800","DEBIT",null);
        assertThat(finances.context(owner).monthlyExpenses()).isEqualByComparingTo("1800");
        natures.confirm(owner,investment,new FinancialNatureService.Change(0,FinancialNatureService.Nature.INVESTMENT_IN,"RDB",null,true));
        assertThat(finances.context(owner).monthlyExpenses()).isEqualByComparingTo("1000");assertThat(finances.context(owner).available()).isEqualByComparingTo("2000");
    }
    @Test void uncategorizedPendingBankExpensesRemainInBudgetAndPlanning(){
        finances.save(owner,new PlanningFinanceService.Input("3000","1000",0));
        UUID pending=entry(month,"Despesa agendada fictícia","1500","DEBIT",null);jdbc.update("UPDATE bank_entry SET provider_status='PENDING' WHERE id=?",pending);
        assertThat(budgets.month(owner,month).totals().unbudgetedPlanned()).isEqualByComparingTo("1500");
        assertThat(finances.context(owner).monthlyExpenses()).isEqualByComparingTo("1500");assertThat(finances.context(owner).currentConsumption()).isEqualByComparingTo("0");
    }
    @Test void unknownExpensesAreDifferentFromExplicitZeroAndRoundingDoesNotUnderfundGoal(){
        entry(month.minusMonths(1),"Salário fictício","3000","CREDIT",salary);
        assertThat(finances.context(owner).available()).isNull();
        finances.save(owner,new PlanningFinanceService.Input("100","66.67",0));
        var c=finances.context(owner);var a=finances.assess(c,new BigDecimal("100.01"),BigDecimal.ZERO,3);
        assertThat(a.requiredMonthly()).isEqualByComparingTo("33.34");assertThat(a.additionalSavingNeeded()).isEqualByComparingTo("0.01");assertThat(a.minimumMonths()).isEqualTo("4");
        finances.save(owner,new PlanningFinanceService.Input("100","0",1));assertThat(finances.context(owner).available()).isEqualByComparingTo("100");
    }
    @Test void profileEnforcesOwnerSessionCsrfVersionAndDoesNotSaveAmountsInAuditReason()throws Exception{
        var stranger=register();finances.save(owner,new PlanningFinanceService.Input("3456.78","2345.67",0));
        assertThat(finances.context(stranger).profile().netSalary()).isNull();assertThat(finances.context(stranger).available()).isNull();
        assertThatThrownBy(()->finances.save(owner,new PlanningFinanceService.Input("1","1",0))).isInstanceOf(ApiException.class);
        mvc.perform(get("/api/goals/finances")).andExpect(status().isUnauthorized());
        var principal=new UserPrincipal(owner,"",0);
        mvc.perform(put("/api/goals/finances").with(user(principal)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/goals/finances").with(user(principal)).with(csrf()).contentType("application/json").content("{\"netSalary\":\"1\",\"version\":0}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForList("SELECT reason FROM audit_event WHERE owner_id=?",owner).toString()).doesNotContain("3456.78","2345.67");
    }
}
