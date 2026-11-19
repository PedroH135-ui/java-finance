package br.com.javafinance;

import br.com.javafinance.accounts.*;
import br.com.javafinance.banking.*;
import br.com.javafinance.common.ApiException;
import br.com.javafinance.organization.PlanningFinanceService;
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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.registration-enabled=true") @AutoConfigureMockMvc
class SalaryIT {
 @DynamicPropertySource static void database(DynamicPropertyRegistry registry){TestDatabase.configure(registry);}
 @Autowired SalaryService salary;@Autowired BankService bank;@Autowired BankProperties config;@Autowired UserService users;
 @Autowired AccountService accounts;@Autowired JdbcTemplate jdbc;@Autowired PlanningFinanceService planning;@Autowired MockMvc mvc;@Autowired Clock clock;
 @MockitoBean BankProvider provider;
 UUID owner,item,external,account,link,receipt;LocalDate today;
 static final String DESCRIPTION="Transferência Recebida|EMPRESA FICTÍCIA LTDA";
 @BeforeEach void setup(){
  owner=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Salário fictício","UmaSenhaSegura123!")).id();today=LocalDate.now(clock);item=UUID.randomUUID();external=UUID.randomUUID();
  jdbc.update("INSERT INTO category_automation_preference(owner_id,enabled) VALUES (?,false) ON CONFLICT(owner_id) DO UPDATE SET enabled=false",owner);
  config.setOwnerId(owner);config.setItemIds(Set.of(item));config.setClientId("synthetic");config.setClientSecret("synthetic");
  account=accounts.create(owner,new AccountService.Create("Conta fictícia","",Account.Kind.CHECKING,"1000",today.minusDays(10))).id();
  when(provider.accounts(item)).thenReturn(new BankProvider.Snapshot(Instant.now(clock),List.of(new BankProvider.RemoteAccount(external,"Banco fictício","BANK","BRL",new BigDecimal("1000")))));
  when(provider.transactions(eq(external),any(),any())).thenReturn(List.of());bank.bind(owner,new BankService.Bind(item,external,account,today.minusDays(5),true));link=bank.links(owner).getFirst().id();
  receipt=entry(DESCRIPTION,"CREDIT");
 }
 UUID entry(String text,String direction){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status) VALUES (?,?,?,?,?,3000,?,?,'POSTED')",id,owner,link,id.toString(),text,today,direction);return id;}
 String monetary(){return jdbc.queryForObject("SELECT md5(coalesce(string_agg(to_jsonb(p)::text,'' ORDER BY id),'')) FROM posting p WHERE owner_id=?",String.class,owner)+jdbc.queryForObject("SELECT balance_source||coalesce(reported_balance::text,'') FROM (SELECT 'BANK' balance_source,reported_balance FROM bank_link WHERE id=?) x",String.class,link);}
 void read(String externalId,LocalDate date){
  jdbc.update("UPDATE bank_link SET last_attempt_at=null WHERE id=?",link);
  when(provider.transactions(eq(external),any(),any())).thenReturn(List.of(new BankProvider.RemoteEntry(externalId,DESCRIPTION,new BigDecimal("3100"),date,"CREDIT","POSTED")));
  bank.sync(owner,link,new BankService.Sync(today.minusDays(5),today));
 }
 @Test void explicitSalaryDoesNotChangePredictionMoneyOrCreateFutureRule(){
  var before=monetary();salary.mark(owner,receipt,new SalaryService.Mark(0,false));
  assertThat(jdbc.queryForObject("SELECT c.name FROM bank_entry e JOIN category c ON c.id=e.category_id WHERE e.id=?",String.class,receipt)).isEqualTo("Salário");
  assertThat(salary.list(owner)).isEmpty();assertThat(planning.context(owner).monthlySalary()).isNull();assertThat(monetary()).isEqualTo(before);
  read("new-without-rule",today);assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE external_id=? AND link_id=?",UUID.class,"new-without-rule",link)).isNull();
 }
 @Test void futureRuleWorksWithAiDisabledOnlyForNewMatchingReceiptsAndIsIdempotent(){
  UUID historical=entry(DESCRIPTION,"CREDIT");String before=monetary();
  salary.mark(owner,receipt,new SalaryService.Mark(0,true));read("future-rule",today);
  assertThat(jdbc.queryForObject("SELECT category_source FROM bank_entry WHERE external_id='future-rule' AND link_id=?",String.class,link)).isEqualTo("RULE");
  assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,historical)).isNull();
  long version=jdbc.queryForObject("SELECT version FROM bank_entry WHERE external_id='future-rule' AND link_id=?",Long.class,link);
  read("future-rule",today);assertThat(jdbc.queryForObject("SELECT version FROM bank_entry WHERE external_id='future-rule' AND link_id=?",Long.class,link)).isEqualTo(version);
  assertThat(salary.list(owner)).hasSize(1);assertThat(monetary()).isEqualTo(before);assertThat(planning.profile(owner).saved()).isFalse();
 }
 @Test void pauseAndArchiveStopNewRulesWithoutUndoingCorrections(){
  salary.mark(owner,receipt,new SalaryService.Mark(0,true));var rule=salary.list(owner).getFirst();salary.toggle(owner,rule.id(),new SalaryService.Toggle(rule.version(),false));read("paused",today);
  assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE external_id='paused' AND link_id=?",UUID.class,link)).isNull();
  assertThat(jdbc.queryForObject("SELECT category_source FROM bank_entry WHERE id=?",String.class,receipt)).isEqualTo("MANUAL");
  rule=salary.list(owner).getFirst();salary.toggle(owner,rule.id(),new SalaryService.Toggle(rule.version(),true));
  jdbc.update("UPDATE category SET archived=true WHERE id=(SELECT category_id FROM bank_entry WHERE id=?)",receipt);read("archived",today);
  assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE external_id='archived' AND link_id=?",UUID.class,link)).isNull();
 }
 @Test void oldDatesDebitsAndManualChoicesAreNotReclassified(){
  salary.mark(owner,receipt,new SalaryService.Mark(0,true));read("old-date",today.minusDays(1));
  assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE external_id='old-date' AND link_id=?",UUID.class,link)).isNull();
  assertThatThrownBy(()->salary.mark(owner,entry(DESCRIPTION,"DEBIT"),new SalaryService.Mark(0,true))).isInstanceOf(ApiException.class);
  UUID corrected=entry(DESCRIPTION,"CREDIT");jdbc.update("UPDATE bank_entry SET category_source='MANUAL',category_auto_blocked=true WHERE id=?",corrected);
  salary.applyNew(owner,corrected,new BankProvider.RemoteEntry("x",DESCRIPTION,new BigDecimal("3000"),today,"CREDIT","POSTED"),salary.rulesForRead(owner,link));
  assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,corrected)).isNull();
 }
 @Test void authenticationCsrfOwnershipAndVersionAreRequired() throws Exception{
  mvc.perform(post("/api/banking/entries/"+receipt+"/salary").contentType("application/json").content("{\"version\":0}")).andExpect(status().isForbidden());
  var principal=new UserPrincipal(owner,"",0);
  mvc.perform(post("/api/banking/entries/"+receipt+"/salary").with(user(principal)).with(csrf()).contentType("application/json").content("{\"version\":0,\"futureReceipts\":true}")).andExpect(status().isNoContent());
  mvc.perform(post("/api/banking/entries/"+receipt+"/salary").with(user(principal)).with(csrf()).contentType("application/json").content("{\"version\":0}")).andExpect(status().isConflict());
  UUID other=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Outro fictício","UmaSenhaSegura123!")).id();
  mvc.perform(get("/api/banking/salary-rules").with(user(new UserPrincipal(other,"",0)))).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(post("/api/banking/entries/"+receipt+"/salary").with(user(new UserPrincipal(other,"",0))).with(csrf()).contentType("application/json").content("{\"version\":1}")).andExpect(status().isNotFound());
 }
 @Test void aNewPendingReceiptBecomesSalaryOnlyAfterPostingAndManualCorrectionWins(){
  salary.mark(owner,receipt,new SalaryService.Mark(0,true));
  jdbc.update("UPDATE bank_link SET last_attempt_at=null WHERE id=?",link);
  when(provider.transactions(eq(external),any(),any())).thenReturn(List.of(new BankProvider.RemoteEntry("pending-future",DESCRIPTION,new BigDecimal("3100"),today,"CREDIT","PENDING")));
  bank.sync(owner,link,new BankService.Sync(today.minusDays(5),today));
  assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE external_id='pending-future' AND link_id=?",UUID.class,link)).isNull();
  read("pending-future",today);assertThat(jdbc.queryForObject("SELECT category_source FROM bank_entry WHERE external_id='pending-future' AND link_id=?",String.class,link)).isEqualTo("RULE");
  UUID other=jdbc.queryForObject("SELECT id FROM category WHERE owner_id=? AND name='Outras receitas'",UUID.class,owner);
  jdbc.update("INSERT INTO category_correction(owner_id,direction,description_key,category_id) VALUES (?,'CREDIT',?,?)",owner,CategoryAdvisor.memoryKey(DESCRIPTION),other);
  read("manual-priority",today);assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE external_id='manual-priority' AND link_id=?",UUID.class,link)).isNull();assertThat(salary.list(owner).getFirst().blockedByCorrection()).isTrue();
 }
}
