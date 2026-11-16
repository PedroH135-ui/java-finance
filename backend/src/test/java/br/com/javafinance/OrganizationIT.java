package br.com.javafinance;
import br.com.javafinance.organization.*;
import br.com.javafinance.accounts.*;
import br.com.javafinance.banking.*;
import br.com.javafinance.categories.*;
import br.com.javafinance.users.*;
import br.com.javafinance.reports.*;
import br.com.javafinance.budgets.*;
import br.com.javafinance.common.ApiException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.registration-enabled=true","app.category-ai.automatic-cloud-enabled=true","app.category-ai.cloud-rag=true","app.category-ai.enabled=true","app.category-ai.url=https://example.vercel.app","app.category-ai.job-token=synthetic-cloud-job-token-for-tests-only-0000"})
@AutoConfigureMockMvc
class OrganizationIT {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){TestDatabase.configure(r);}
 @Autowired UserService users;@Autowired BankService bank;@Autowired BankProperties config;@Autowired AccountService accounts;
 @Autowired CategoryService categories;@Autowired FinancialNatureService natures;@Autowired InvestmentService investments;
 @Autowired GoalService goals;@Autowired ReportService reports;@Autowired BudgetService budgets;@Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;
 @MockitoBean BankProvider provider;@MockitoBean CategoryAdvisor advisor;@MockitoSpyBean CloudCategoryJobs jobs;
 UUID owner,account,link,item,external;LocalDate date=LocalDate.now().minusDays(1);
 UUID register(){return users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Organização fictícia","UmaSenhaSegura123!")).id();}
 @BeforeEach void setup(){
  doReturn(true).when(jobs).dispatch(any());owner=register();item=UUID.randomUUID();external=UUID.randomUUID();
  config.setOwnerId(owner);config.setClientId("synthetic-client");config.setClientSecret("synthetic-secret");config.setItemIds(Set.of(item));
  account=accounts.create(owner,new AccountService.Create("Banco fictício","",Account.Kind.CHECKING,"1000",date.minusDays(10))).id();
  when(provider.accounts(item)).thenReturn(new BankProvider.Snapshot(Instant.now(),List.of(new BankProvider.RemoteAccount(external,"Outro banco fictício","BANK","BRL",new BigDecimal("950")))));
  when(provider.transactions(eq(external),any(),any())).thenReturn(List.of());
  bank.bind(owner,new BankService.Bind(item,external,account,date.minusDays(5),true));link=bank.links(owner).getFirst().id();
 }
 UUID entry(String description,String amount,String direction){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status) VALUES (?,?,?,?,?,?,?,?,?)",id,owner,link,id.toString(),description,new BigDecimal(amount),date,direction,"POSTED");return id;}
 Object money(){return List.of(accounts.list(owner),jdbc.queryForObject("SELECT count(*) FROM movement WHERE owner_id=?",Long.class,owner),jdbc.queryForObject("SELECT count(*) FROM posting WHERE owner_id=?",Long.class,owner),jdbc.queryForList("SELECT id,amount,date,direction FROM bank_entry WHERE owner_id=? ORDER BY id",owner));}
 @Test void bindingSeedsCategoriesReadsAndStartsOrganizationWithoutChangingOptOut(){
  assertThat(categories.list(owner)).hasSize(16);assertThat(jobs.preference(owner).enabled()).isTrue();
  verify(provider).transactions(eq(external),any(),any());
  jobs.preference(owner,false,jobs.preference(owner).version());
  var second=accounts.create(owner,new AccountService.Create("Outra fictícia","",Account.Kind.CHECKING,"0",date.minusDays(10))).id();
  UUID ext=UUID.randomUUID();when(provider.accounts(item)).thenReturn(new BankProvider.Snapshot(Instant.now(),List.of(new BankProvider.RemoteAccount(ext,"Outra","BANK","BRL",BigDecimal.ZERO))));
  when(provider.transactions(eq(ext),any(),any())).thenReturn(List.of());
  bank.bind(owner,new BankService.Bind(item,ext,second,date.minusDays(5),true));
  assertThat(jobs.preference(owner).enabled()).isFalse();assertThat(categories.list(owner)).hasSize(16);
 }
 @Test void manualNatureSeparatesIndicatorsAndInvestmentsWithoutChangingMoney(){
  UUID apply=entry("Aplicação automática em RDB","300","DEBIT"),redeem=entry("Resgate RDB","50","CREDIT"),card=entry("Pagamento de fatura","200","DEBIT");
  entry("Mercado fictício","100","DEBIT");entry("Salário fictício","2000","CREDIT");var before=money();var month=YearMonth.from(date);
  assertThat(natures.status(owner,apply).suggestion().instrument()).isEqualTo("RDB");
  assertThat(investments.summary(owner).suggestions()).hasSize(2);
  assertThat(reports.summary(owner,month).competence().expenses()).isEqualByComparingTo("600");
  assertThatThrownBy(()->natures.confirm(owner,apply,new FinancialNatureService.Change(0,FinancialNatureService.Nature.INVESTMENT_IN,"RDB",null,false))).isInstanceOf(ApiException.class);
  natures.confirm(owner,apply,new FinancialNatureService.Change(0,FinancialNatureService.Nature.INVESTMENT_IN,"RDB",null,true));
  natures.confirm(owner,redeem,new FinancialNatureService.Change(0,FinancialNatureService.Nature.INVESTMENT_OUT,"RDB",null,true));
  natures.confirm(owner,card,new FinancialNatureService.Change(0,FinancialNatureService.Nature.CARD_PAYMENT,"NONE",null,true));
  var report=reports.summary(owner,month);assertThat(report.competence().income()).isEqualByComparingTo("2000");assertThat(report.competence().expenses()).isEqualByComparingTo("100");assertThat(report.cashFlow()).isEqualByComparingTo("1450");
  assertThat(budgets.month(owner,month).totals().unbudgetedSpent()).isEqualByComparingTo("100");
  assertThat(investments.summary(owner).netContributions()).isEqualByComparingTo("250");assertThat(money()).isEqualTo(before);
  natures.confirm(owner,apply,new FinancialNatureService.Change(1,FinancialNatureService.Nature.ORDINARY,"NONE",null,true));
  assertThat(reports.summary(owner,month).competence().expenses()).isEqualByComparingTo("400");assertThat(money()).isEqualTo(before);
 }
 @Test void providerChangesInvalidateNatureAndPositionsStayInformative(){
  UUID apply=entry("Aplicação RDB","300","DEBIT");var before=money();
  investments.save(owner,null,new InvestmentService.Input("Caixinha fictícia","Banco fictício","RDB","800",date,0));
  var position=investments.summary(owner).positions().getFirst();
  natures.confirm(owner,apply,new FinancialNatureService.Change(0,FinancialNatureService.Nature.INVESTMENT_IN,"RDB",position.id(),true));assertThat(money()).isEqualTo(before);
  jdbc.update("UPDATE bank_entry SET amount=350,version=version+1 WHERE id=?",apply);
  assertThat(natures.status(owner,apply).changed()).isTrue();assertThat(investments.summary(owner).flows()).isEmpty();assertThat(investments.summary(owner).positions().getFirst().reportedValue()).isEqualByComparingTo("800");
 }
 @Test void ownTransferRequiresConfirmationAndNeverBecomesConsumption(){
  UUID id=entry("PIX para outra conta minha fictícia","80","DEBIT");var before=money();
  natures.confirm(owner,id,new FinancialNatureService.Change(0,FinancialNatureService.Nature.OWN_TRANSFER,"NONE",null,true));
  assertThat(reports.summary(owner,YearMonth.from(date)).competence().expenses()).isZero();assertThat(money()).isEqualTo(before);
 }
 @Test void goalsUseExactMathRequireSaveAndDoNotReserveMoney(){
  var before=money();
  when(advisor.interpretGoal(eq(owner),anyMap())).thenReturn(new CategoryAdvisor.GoalIntent("3000","600",6,"Viagem","GOAL",false,"LANGCHAIN_RULES"));
  var reply=goals.chat(owner,new GoalService.Chat("Quero viajar e guardar 3000 em 6 meses",null));assertThat(reply.monthly()).isEqualByComparingTo("400");assertThat(goals.list(owner)).isEmpty();
  assertThatThrownBy(()->goals.save(owner,new GoalService.Save("Viagem","3000","600",6,false))).isInstanceOf(ApiException.class);
  goals.save(owner,new GoalService.Save("Viagem","3000","600",6,true));var goal=goals.list(owner).getFirst();
  goals.progress(owner,goal.id(),new GoalService.Progress("1000",0));assertThat(goals.list(owner).getFirst().monthly()).isEqualByComparingTo("333.34");assertThat(money()).isEqualTo(before);
 }
 @Test void organizationApisEnforceOwnerSessionCsrfAndVersions() throws Exception {
  UUID id=entry("Aplicação RDB","50","DEBIT"),stranger=register();
  mvc.perform(get("/api/banking/entries/"+id+"/nature").with(user(new UserPrincipal(stranger,"",0)))).andExpect(status().isNotFound());
  mvc.perform(post("/api/goals").with(user(new UserPrincipal(owner,"",0))).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(get("/api/investments")).andExpect(status().isUnauthorized());
  assertThat(investments.summary(stranger).flows()).isEmpty();assertThat(goals.list(stranger)).isEmpty();
  natures.confirm(owner,id,new FinancialNatureService.Change(0,FinancialNatureService.Nature.INVESTMENT_IN,"RDB",null,true));
  assertThatThrownBy(()->natures.confirm(owner,id,new FinancialNatureService.Change(0,FinancialNatureService.Nature.ORDINARY,"NONE",null,true))).isInstanceOf(ApiException.class);
 }
}
