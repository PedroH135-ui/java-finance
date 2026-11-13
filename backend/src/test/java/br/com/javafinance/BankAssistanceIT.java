package br.com.javafinance;

import br.com.javafinance.accounts.*;
import br.com.javafinance.banking.*;
import br.com.javafinance.categories.*;
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
class BankAssistanceIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {TestDatabase.configure(registry);}
    @Autowired BankAssistanceService assistance; @Autowired BankService bank; @Autowired BankProperties config;
    @Autowired UserService users; @Autowired AccountService accounts; @Autowired CategoryService categories;
    @Autowired JdbcTemplate jdbc; @Autowired Clock clock; @Autowired ReportService reports; @Autowired MockMvc mvc;
    @MockitoBean BankProvider provider;
    UUID owner,item,remoteA,remoteB; AccountService.View accountA,accountB; BankService.Link linkA,linkB;
    UUID debit,credit; LocalDate today,date; UserPrincipal principal;
    @BeforeEach void setup() {
        owner=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Perfil fictício","UmaSenhaSegura123!")).id();
        principal=new UserPrincipal(owner,"",0);item=UUID.randomUUID();remoteA=UUID.randomUUID();remoteB=UUID.randomUUID();today=LocalDate.now(clock);date=today.minusDays(2);
        config.setOwnerId(owner);config.setClientId("synthetic-client");config.setClientSecret("synthetic-secret");config.setItemIds(Set.of(item));
        accountA=accounts.create(owner,new AccountService.Create("Banco A fictício","",Account.Kind.CHECKING,"1000",today.minusDays(10)));
        accountB=accounts.create(owner,new AccountService.Create("Banco B fictício","",Account.Kind.CHECKING,"200",today.minusDays(10)));
        when(provider.accounts(item)).thenReturn(new BankProvider.Snapshot(clock.instant(),List.of(
            new BankProvider.RemoteAccount(remoteA,"Banco A","BANK","BRL",new BigDecimal("950")),
            new BankProvider.RemoteAccount(remoteB,"Banco B","BANK","BRL",new BigDecimal("250")))));
        bank.bind(owner,new BankService.Bind(item,remoteA,accountA.id(),today.minusDays(9),true));
        bank.bind(owner,new BankService.Bind(item,remoteB,accountB.id(),today.minusDays(9),true));
        linkA=bank.links(owner).stream().filter(l->l.accountId().equals(accountA.id())).findFirst().orElseThrow();
        linkB=bank.links(owner).stream().filter(l->l.accountId().equals(accountB.id())).findFirst().orElseThrow();
        sync(linkA,List.of(entry("debit","Mercado Alfa","50","DEBIT","POSTED",date)));
        sync(linkB,List.of(entry("credit","Crédito de teste","50","CREDIT","POSTED",date.plusDays(1))));
        debit=bank.entries(owner,linkA.id(),0).getFirst().id();credit=bank.entries(owner,linkB.id(),0).getFirst().id();
    }
    BankProvider.RemoteEntry entry(String id,String text,String amount,String direction,String status,LocalDate day) {
        return new BankProvider.RemoteEntry(id,text,new BigDecimal(amount),day,direction,status);
    }
    void sync(BankService.Link link,List<BankProvider.RemoteEntry> entries) {
        jdbc.update("UPDATE bank_link SET last_attempt_at=null WHERE id=?",link.id());
        when(provider.transactions(link.externalAccountId(),link.importFrom(),today)).thenReturn(entries);
        bank.sync(owner,link.id(),new BankService.Sync(link.importFrom(),today));
    }
    UUID category(String name,Category.Kind kind) {return categories.create(owner,new CategoryService.Create(name,kind,true)).id();}
    long version(UUID id) {return jdbc.queryForObject("SELECT version FROM bank_entry WHERE id=?",Long.class,id);}
    void pair() {assistance.confirmTransfer(owner,debit,new BankAssistanceService.TransferChange(version(debit),credit,version(credit)));}
    void balancesUnchanged() {
        assertThat(accounts.list(owner)).anySatisfy(a->{assertThat(a.id()).isEqualTo(accountA.id());assertThat(a.balance()).isEqualByComparingTo("950");});
        assertThat(accounts.list(owner)).anySatisfy(a->{assertThat(a.id()).isEqualTo(accountB.id());assertThat(a.balance()).isEqualByComparingTo("250");});
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movement WHERE owner_id=? AND kind!='OPENING'",Long.class,owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posting WHERE owner_id=?",Long.class,owner)).isEqualTo(2);
    }
    @Test void suggestionsAreReadOnlyAndOnlyPairOppositeOwnAccountsWithinWindow() {
        clearInvocations(provider);
        var result=assistance.assistance(owner,debit);
        assertThat(result.transferCandidates()).singleElement().satisfies(p->{assertThat(p.id()).isEqualTo(credit);assertThat(p.amount()).isEqualByComparingTo("50");});
        assertThat(result.transfer()).isNull();assertThat(result.categorySuggestion().categoryId()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_transfer_pair WHERE owner_id=?",Long.class,owner)).isZero();
        verifyNoInteractions(provider);balancesUnchanged();
    }
    @Test void confirmingAndUndoingPairChangesReportsOnlyAndRestoresCategory() {
        UUID cat=category("Alimentação",Category.Kind.EXPENSE);
        assistance.category(owner,debit,new BankAssistanceService.CategoryChange(0,cat));
        pair();
        var summary=reports.summary(owner,YearMonth.from(date));
        assertThat(summary.competence().expenses()).isZero();assertThat(summary.competence().income()).isZero();assertThat(summary.records()).isZero();assertThat(summary.unclassifiedBankRecords()).isZero();
        assertThat(assistance.assistance(owner,debit).transfer().valid()).isTrue();
        assertThatThrownBy(()->assistance.category(owner,debit,new BankAssistanceService.CategoryChange(version(debit),cat))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->bank.review(owner,debit,"import",new BankService.Review(version(debit),cat,null,true,true))).isInstanceOf(ApiException.class);
        assistance.undoTransfer(owner,credit,new BankAssistanceService.UndoTransfer(0));
        assertThat(assistance.assistance(owner,debit).currentCategoryId()).isEqualTo(cat);
        assertThat(reports.summary(owner,YearMonth.from(date)).competence().expenses()).isEqualByComparingTo("50");
        assertThatThrownBy(()->assistance.undoTransfer(owner,credit,new BankAssistanceService.UndoTransfer(0))).isInstanceOf(ApiException.class);
        balancesUnchanged();
    }
    @Test void pairSurvivesVisibilityChangesButProviderChangesInvalidateIt() {
        pair();bank.visibility(owner,debit,new BankService.Visibility(version(debit),true,"Ocultado"));
        bank.visibility(owner,debit,new BankService.Visibility(version(debit),false,"Restaurado"));
        assertThat(assistance.assistance(owner,debit).transfer().valid()).isTrue();
        sync(linkA,List.of(entry("debit","Mercado corrigido","51","DEBIT","POSTED",date)));
        assertThat(assistance.assistance(owner,debit).transfer().valid()).isFalse();
        assertThat(reports.summary(owner,YearMonth.from(date)).competence().expenses()).isEqualByComparingTo("51");
        assistance.undoTransfer(owner,debit,new BankAssistanceService.UndoTransfer(0));balancesUnchanged();
    }
    @Test void categoriesAreOptionalCorrectableAndHistoryLearnsOnlyUserChoices() {
        UUID food=category("Alimentação",Category.Kind.EXPENSE),other=category("Outros gastos",Category.Kind.EXPENSE);
        assertThat(assistance.assistance(owner,debit).currentCategoryId()).isNull();
        assistance.category(owner,debit,new BankAssistanceService.CategoryChange(0,food));
        assistance.category(owner,debit,new BankAssistanceService.CategoryChange(1,other));
        sync(linkA,List.of(entry("debit","Mercado Alfa","50","DEBIT","POSTED",date),entry("next","Mercado Alfa","25","DEBIT","POSTED",date)));
        UUID next=jdbc.queryForObject("SELECT id FROM bank_entry WHERE owner_id=? AND external_id='next'",UUID.class,owner);
        var suggestion=assistance.assistance(owner,next).categorySuggestion();
        assertThat(suggestion.categoryId()).isEqualTo(other.toString());assertThat(suggestion.method()).isEqualTo("HISTORY");
        assertThat(assistance.assistance(owner,next).currentCategoryId()).isNull(); // suggestion does not apply itself.
        assistance.category(owner,debit,new BankAssistanceService.CategoryChange(2,null));
        assertThat(assistance.assistance(owner,next).categorySuggestion().categoryId()).isNull();
        balancesUnchanged();
    }
    @Test void categoryValidationStaleVersionsAndProviderDescriptionChangesAreHandled() {
        UUID income=category("Receita",Category.Kind.INCOME),food=category("Alimentação",Category.Kind.EXPENSE);
        assertThatThrownBy(()->assistance.category(owner,debit,new BankAssistanceService.CategoryChange(0,income))).isInstanceOf(ApiException.class);
        assistance.category(owner,debit,new BankAssistanceService.CategoryChange(0,food));
        assertThatThrownBy(()->assistance.category(owner,debit,new BankAssistanceService.CategoryChange(0,null))).isInstanceOf(ApiException.class);
        sync(linkA,List.of(entry("debit","Outra operação","50","DEBIT","POSTED",date)));
        assertThat(assistance.assistance(owner,debit).currentCategoryId()).isNull();
        assertThat(jdbc.queryForObject("SELECT category_id FROM financial_record WHERE id=?",UUID.class,debit)).isNull();
        sync(linkA,List.of(entry("debit","Outra operação","50","DEBIT","PENDING",date)));
        assertThatThrownBy(()->assistance.category(owner,debit,new BankAssistanceService.CategoryChange(version(debit),food))).isInstanceOf(ApiException.class);
        balancesUnchanged();
    }
    @Test void ambiguousCandidatesNeverConfirmAutomaticallyAndInvalidPairsAreRejected() {
        sync(linkB,List.of(entry("credit","Crédito de teste","50","CREDIT","POSTED",date),entry("credit-two","Outro crédito","50","CREDIT","POSTED",date)));
        assertThat(assistance.assistance(owner,debit).transferCandidates()).hasSize(2);
        assertThatThrownBy(()->assistance.confirmTransfer(owner,debit,new BankAssistanceService.TransferChange(0,debit,0))).isInstanceOf(ApiException.class);
        sync(linkB,List.of(entry("credit","Crédito de teste","51","CREDIT","POSTED",date)));
        assertThatThrownBy(()->assistance.confirmTransfer(owner,debit,new BankAssistanceService.TransferChange(version(debit),credit,version(credit)))).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_transfer_pair WHERE owner_id=?",Long.class,owner)).isZero();
    }
    @Test void ownershipCsrfAndUnknownCategoriesCannotBeBypassed() throws Exception {
        UUID stranger=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Outro","UmaSenhaSegura123!")).id();
        UUID foreign=categories.create(stranger,new CategoryService.Create("Privada",Category.Kind.EXPENSE,true)).id();
        assertThatThrownBy(()->assistance.category(owner,debit,new BankAssistanceService.CategoryChange(0,foreign))).isInstanceOf(ApiException.class);
        mvc.perform(get("/api/banking/entries/"+debit+"/assistance")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/banking/entries/"+debit+"/assistance").with(user(new UserPrincipal(stranger,"",0)))).andExpect(status().isNotFound());
        mvc.perform(post("/api/banking/entries/"+debit+"/category").with(user(principal)).contentType("application/json").content("{\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/banking/entries/"+debit+"/assistance").with(user(principal))).andExpect(status().isOk()).andExpect(jsonPath("$.transferCandidates.length()").value(1));
        assertThatThrownBy(()->assistance.confirmTransfer(owner,debit,new BankAssistanceService.TransferChange(0,UUID.randomUUID(),0))).isInstanceOf(ApiException.class);
    }
    @Test void concurrentConfirmationCreatesExactlyOnePair() throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> action=()->{try{assistance.confirmTransfer(owner,debit,new BankAssistanceService.TransferChange(0,credit,0));return true;}catch(ApiException ex){return false;}};
            var results=pool.invokeAll(List.of(action,action));assertThat(results.get(0).get()^results.get(1).get()).isTrue();
        }finally{pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_transfer_pair WHERE owner_id=? AND active",Long.class,owner)).isEqualTo(1);
        balancesUnchanged();
    }
}
