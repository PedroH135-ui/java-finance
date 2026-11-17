package br.com.javafinance;
import br.com.javafinance.accounts.*;
import br.com.javafinance.banking.*;
import br.com.javafinance.budgets.BudgetService;
import br.com.javafinance.common.ApiException;
import br.com.javafinance.reports.ReportService;
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
class BankProductsIT {
 @DynamicPropertySource static void database(DynamicPropertyRegistry registry){TestDatabase.configure(registry);}
 @Autowired BankProductsService products;@Autowired BankService bank;@Autowired BankProperties config;@Autowired AccountService accounts;
 @Autowired UserService users;@Autowired JdbcTemplate jdbc;@Autowired ReportService reports;@Autowired BudgetService budgets;@Autowired MockMvc mvc;@Autowired Clock clock;
 @MockitoBean BankProvider provider;@MockitoBean BankProductsProvider productProvider;
 UUID owner,item,external,card,account,link,payment;LocalDate date;String externalBill="synthetic-bill";
 BigDecimal amount(String value){return new BigDecimal(value);}
 @BeforeEach void setup(){
  owner=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Produtos fictícios","UmaSenhaSegura123!")).id();date=LocalDate.now(clock);item=UUID.randomUUID();external=UUID.randomUUID();card=UUID.randomUUID();
  config.setOwnerId(owner);config.setItemIds(Set.of(item));config.setClientId("synthetic");config.setClientSecret("synthetic");
  account=accounts.create(owner,new AccountService.Create("Conta fictícia","",Account.Kind.CHECKING,"1000",date.minusDays(10))).id();
  when(provider.accounts(item)).thenReturn(new BankProvider.Snapshot(Instant.now(clock),List.of(new BankProvider.RemoteAccount(external,"Banco fictício","BANK","BRL",amount("1000")))));
  when(provider.transactions(eq(external),any(),any())).thenReturn(List.of());bank.bind(owner,new BankService.Bind(item,external,account,date.minusDays(5),true));link=bank.links(owner).getFirst().id();
  payment=UUID.randomUUID();jdbc.update("INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status) VALUES (?,?,?,?,?,480,?,'DEBIT','POSTED')",payment,owner,link,payment.toString(),"Pagamento de fatura",date);
  fixture("100","POSTED","480");
  when(productProvider.investments(item)).thenReturn(List.of(new BankProductsProvider.Position(UUID.randomUUID(),"RDB fictício","FIXED_INCOME","RDB",amount("2500"),date,null,amount("2400"),"ACTIVE")));
 }
 void fixture(String delivery,String status,String total){
  var charges=List.of(new BankProductsProvider.Charge("market","Mercado fictício",amount("400"),date,"DEBIT","POSTED",externalBill,null,null),
   new BankProductsProvider.Charge("delivery","Delivery fictício",amount(delivery),date,"DEBIT",status,externalBill,1,3),
   new BankProductsProvider.Charge("refund","Estorno fictício",amount("20"),date,"CREDIT","POSTED",externalBill,null,null));
  when(productProvider.cards(eq(item),any(),any())).thenReturn(List.of(new BankProductsProvider.Card(card,"Cartão fictício",List.of(new BankProductsProvider.Bill(externalBill,date,amount(total))),charges,true)));
 }
 void read(){jdbc.update("UPDATE bank_product_sync SET last_attempt_at=null WHERE owner_id=? AND item_id=?",owner,item);products.read(owner,link,new BankProductsService.Read(false));}
 BankProductsService.Bill bill(){return products.summary(owner).cards().getFirst().bills().getFirst();}
 String monetary(){return jdbc.queryForObject("SELECT md5(coalesce(string_agg(to_jsonb(p)::text,'' ORDER BY id),'')) FROM posting p WHERE owner_id=?",String.class,owner)+bank.links(owner).getFirst().reportedBalance().toPlainString();}
 void reconcile(){var b=bill();long version=jdbc.queryForObject("SELECT version FROM bank_entry WHERE id=?",Long.class,payment);products.reconcile(owner,b.id(),new BankProductsService.Reconcile(payment,version,b.revision(),true));}
 @Test void additionalReadAndCachedQueriesDoNotChangeFinancialTotalsOrCallProviderOnGet(){
  String before=monetary();read();var view=products.summary(owner);assertThat(view.cards()).hasSize(1);assertThat(view.positions()).hasSize(1);
  assertThat(view.positions().getFirst().balance()).isEqualByComparingTo("2500");assertThat(view.positions().getFirst().profit()).isNull();assertThat(bill().reconciled()).isFalse();
  assertThat(reports.summary(owner,YearMonth.from(date)).competence().expenses()).isEqualByComparingTo("480");assertThat(monetary()).isEqualTo(before);
  clearInvocations(productProvider);products.summary(owner);verifyNoInteractions(productProvider);
 }
 @Test void exactManualReconciliationCountsDetailsOnceAndCanBeUndoneWithoutChangingMoney(){
  read();String before=monetary();var beforeFlow=reports.summary(owner,YearMonth.from(date)).cashFlow();reconcile();
  assertThat(bill().reconciled()).isTrue();assertThat(jdbc.queryForObject("SELECT count(*) FROM consumption_record WHERE owner_id=? AND source='CARD'",Long.class,owner)).isEqualTo(3);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM consumption_record WHERE owner_id=? AND source='BANK'",Long.class,owner)).isZero();
  var report=reports.summary(owner,YearMonth.from(date));assertThat(report.competence().expenses()).isEqualByComparingTo("480");assertThat(report.cashFlow()).isEqualByComparingTo(beforeFlow);assertThat(monetary()).isEqualTo(before);
  UUID market=jdbc.queryForObject("SELECT id FROM category WHERE owner_id=? AND name='Mercado'",UUID.class,owner);var charge=products.summary(owner).cards().getFirst().charges().stream().filter(c->c.description().equals("Mercado fictício")).findFirst().orElseThrow();
  products.categorize(owner,charge.id(),new BankProductsService.CategoryChange(charge.version(),market));
  assertThat(budgets.month(owner,YearMonth.from(date)).categories().stream().filter(c->c.categoryId().equals(market)).findFirst().orElseThrow().spent()).isEqualByComparingTo("400");assertThat(bill().reconciled()).isTrue();
  products.undo(owner,bill().id());assertThat(bill().reconciled()).isFalse();assertThat(reports.summary(owner,YearMonth.from(date)).competence().expenses()).isEqualByComparingTo("480");assertThat(products.charge(owner,charge.id()).categoryId()).isEqualTo(market);assertThat(monetary()).isEqualTo(before);
 }
 @Test void partialTotalsPendingAndStaleRevisionsAreRejected(){
  fixture("100","PENDING","480");read();assertThatThrownBy(this::reconcile).isInstanceOf(ApiException.class);assertThat(bill().reconciled()).isFalse();
  fixture("100","POSTED","490");read();assertThatThrownBy(this::reconcile).isInstanceOf(ApiException.class);
  fixture("100","POSTED","480");read();var old=bill();fixture("101","POSTED","481");read();
  assertThatThrownBy(()->products.reconcile(owner,old.id(),new BankProductsService.Reconcile(payment,0,old.revision(),true))).isInstanceOf(ApiException.class).hasMessageContaining("mudaram");
 }
 @Test void providerChangesInvalidateReconciliationAndRestorePaymentUntilNewConfirmation(){
  read();String before=monetary();reconcile();fixture("110","POSTED","490");read();
  assertThat(bill().reconciled()).isFalse();assertThat(jdbc.queryForObject("SELECT count(*) FROM consumption_record WHERE owner_id=? AND source='CARD'",Long.class,owner)).isZero();
  assertThat(reports.summary(owner,YearMonth.from(date)).competence().expenses()).isEqualByComparingTo("480");assertThat(monetary()).isEqualTo(before);
 }
 @Test void unavailableProductPreservesPreviousPositionAndDoesNotEraseOtherData(){
  read();when(productProvider.investments(item)).thenThrow(BankReadException.failure(BankReadException.Stage.ACCOUNTS,BankReadException.Code.ACCESS_DENIED,403));read();
  var snapshot=products.summary(owner);assertThat(snapshot.positions()).hasSize(1);assertThat(snapshot.cards()).hasSize(1);assertThat(snapshot.connections().getFirst().investmentsStatus()).isEqualTo("UNAVAILABLE");
 }
 @Test void rereadingKeepsManualCategoriesAndOneCopyOfEachProduct(){
  read();var charge=products.summary(owner).cards().getFirst().charges().getFirst();UUID category=jdbc.queryForObject("SELECT id FROM category WHERE owner_id=? AND name='Mercado'",UUID.class,owner);
  products.categorize(owner,charge.id(),new BankProductsService.CategoryChange(charge.version(),category));read();
  assertThat(products.summary(owner).cards()).hasSize(1);assertThat(products.summary(owner).cards().getFirst().charges()).hasSize(3);assertThat(products.charge(owner,charge.id()).categoryId()).isEqualTo(category);
 }
 @Test void csrfOwnershipAndExplicitConfirmationAreRequired() throws Exception{
  read();mvc.perform(get("/api/banking/products")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/banking/card-bills/"+bill().id()+"/undo").with(user(new UserPrincipal(owner,"",0)))).andExpect(status().isForbidden());
  UUID other=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Outro fictício","UmaSenhaSegura123!")).id();
  mvc.perform(get("/api/banking/products").with(user(new UserPrincipal(other,"",0)))).andExpect(status().isOk()).andExpect(jsonPath("$.cards.length()").value(0));
  var charge=products.summary(owner).cards().getFirst().charges().getFirst();mvc.perform(get("/api/banking/card-charges/"+charge.id()).with(user(new UserPrincipal(other,"",0)))).andExpect(status().isNotFound());
  assertThatThrownBy(()->products.reconcile(owner,bill().id(),new BankProductsService.Reconcile(payment,0,bill().revision(),false))).isInstanceOf(ApiException.class);
 }
}
