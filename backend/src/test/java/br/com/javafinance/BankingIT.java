package br.com.javafinance;

import br.com.javafinance.accounts.*;
import br.com.javafinance.reports.ReportService;
import br.com.javafinance.budgets.BudgetService;
import br.com.javafinance.banking.*;
import br.com.javafinance.categories.*;
import br.com.javafinance.common.ApiException;
import br.com.javafinance.movements.*;
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
class BankingIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { TestDatabase.configure(registry); }
    @Autowired BankService bank; @Autowired BankProperties config; @Autowired UserService users;
    @Autowired AccountService accounts; @Autowired CategoryService categories; @Autowired MovementService movements;
    @Autowired JdbcTemplate jdbc; @Autowired Clock clock; @Autowired MockMvc mvc;
    @Autowired BankAutoSync automatic; @Autowired ReportService reports; @Autowired BudgetService budgets; @Autowired LedgerService ledger; @Autowired FinancialRecordService feed;
    @MockitoBean BankProvider provider;
    UUID owner,item,external; UserPrincipal principal; AccountService.View account;
    LocalDate today,date; BankProvider.Snapshot snapshot;
    @BeforeEach void setup() {
        owner=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Banco fictício","UmaSenhaSegura123!")).id();
        principal=new UserPrincipal(owner,"",0); item=UUID.randomUUID(); external=UUID.randomUUID(); today=LocalDate.now(clock); date=today.minusDays(2);
        config.setOwnerId(owner); config.setClientId("synthetic-client"); config.setClientSecret("synthetic-secret"); config.setItemIds(Set.of(item));
        config.setWebhookSecret("synthetic-webhook-secret-at-least-32-characters");
        config.setCronSecret("synthetic-cron-secret-at-least-32-characters");
        account=accounts.create(owner,new AccountService.Create("Nubank fictício","",Account.Kind.CHECKING,"1000",today.minusDays(10)));
        snapshot=new BankProvider.Snapshot(clock.instant(),List.of(new BankProvider.RemoteAccount(external,"Conta Nubank fictícia","BANK","BRL",new BigDecimal("950.00"))));
        when(provider.accounts(item)).thenReturn(snapshot);
    }
    BankService.Link bind() {
        bank.bind(owner,new BankService.Bind(item,external,account.id(),today.minusDays(9),true));
        return bank.links(owner).getFirst();
    }
    BankProvider.RemoteEntry remote(String id,String amount,String status) {
        return new BankProvider.RemoteEntry(id,"Compra fictícia",new BigDecimal(amount),date,"DEBIT",status);
    }
    BankService.Entry sync(BankService.Link link,List<BankProvider.RemoteEntry> entries) {
        when(provider.transactions(external,link.importFrom(),today)).thenReturn(entries);
        bank.sync(owner,link.id(),new BankService.Sync(link.importFrom(),today));
        return bank.entries(owner,link.id(),0).getFirst();
    }
    void allowSync(UUID link) { jdbc.update("UPDATE bank_link SET last_attempt_at=null WHERE id=?",link); }
    UUID category() { return categories.create(owner,new CategoryService.Create("Alimentação",Category.Kind.EXPENSE,true)).id(); }
    @Test void pendingEntriesAreFilteredBeforePaginationWithoutPostingOrCallingProvider() throws Exception {
        var link=bind();
        for(int i=0;i<107;i++) {
            String state=i<55?"IGNORED":i==106?"CHANGED":"REVIEW";
            jdbc.update("INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status,state) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),owner,link.id(),"pending-filter-"+i,"Registro fictício "+i,new BigDecimal("1.00"),
                i<55?today:date,"DEBIT","POSTED",state);
        }
        clearInvocations(provider);
        assertThat(bank.entries(owner,link.id(),0)).hasSize(50).allSatisfy(entry->assertThat(entry.state()).isEqualTo("IGNORED"));
        var first=bank.entries(owner,link.id(),0,true);
        var second=bank.entries(owner,link.id(),1,true);
        assertThat(first).hasSize(50).allSatisfy(entry->assertThat(entry.state()).isIn("REVIEW","CHANGED"));
        assertThat(second).hasSize(2).allSatisfy(entry->assertThat(entry.state()).isIn("REVIEW","CHANGED"));
        assertThat(bank.entries(owner,UUID.randomUUID(),0,true)).isEmpty();
        assertThatThrownBy(()->bank.entries(UUID.randomUUID(),link.id(),0,true)).isInstanceOf(ApiException.class);
        mvc.perform(get("/api/banking/links/"+link.id()+"/entries?pending=true&page=1").with(user(principal)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_entry WHERE owner_id=?",Long.class,owner)).isEqualTo(107);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movement WHERE owner_id=? AND kind!='OPENING'",Long.class,owner)).isZero();
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
        verifyNoInteractions(provider);
    }
    @Test void linkingAndReadingDoNotChangeLedgerAndRepeatedSyncIsIdempotent() {
        var link=bind(); var entry=sync(link,List.of(remote("entry-1","50.00","POSTED")));
        assertThat(entry.state()).isEqualTo("REVIEW"); assertThat(bank.links(owner).getFirst().reportedBalance()).isEqualByComparingTo("950");
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
        assertThatThrownBy(()->bank.sync(owner,link.id(),new BankService.Sync(link.importFrom(),today))).isInstanceOf(ApiException.class);
        allowSync(link.id()); bank.sync(owner,link.id(),new BankService.Sync(link.importFrom(),today));
        assertThat(bank.entries(owner,link.id(),0)).hasSize(1); assertThat(bank.entries(owner,link.id(),0).getFirst().version()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movement WHERE owner_id=? AND kind!='OPENING'",Long.class,owner)).isZero();
    }
    @Test void classificationFeedsReportsAndBudgetsWithoutAnotherDebit() {
        var link=bind(); var entry=sync(link,List.of(remote("entry-1","50.25","POSTED"))); UUID category=category();
        assertThatThrownBy(()->bank.review(owner,entry.id(),"import",new BankService.Review(entry.version(),category,null,false,false))).isInstanceOf(ApiException.class);
        bank.review(owner,entry.id(),"import",new BankService.Review(entry.version(),category,null,true,false));
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
        var reviewed=bank.entries(owner,link.id(),0).getFirst();
        assertThat(reviewed.state()).isEqualTo("IMPORTED");
        assertThat(movements.get(owner,reviewed.movementId()).source()).isEqualTo("MEU_PLUGGY");
        assertThat(movements.get(owner,reviewed.movementId()).competence()).isEqualTo(date.withDayOfMonth(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posting WHERE movement_id=?",Long.class,reviewed.movementId())).isZero();
        assertThat(ledger.balance(owner,account.id(),today)).isEqualByComparingTo("1000");
        var summary=reports.summary(owner,YearMonth.from(date));
        assertThat(summary.balance()).isEqualByComparingTo("950");
        assertThat(summary.competence().expenses()).isEqualByComparingTo("50.25");
        assertThat(summary.cashFlow()).isEqualByComparingTo("-50.25");
        assertThat(summary.records()).isEqualTo(1);
        assertThat(summary.categories()).hasSize(1);
        assertThat(budgets.month(owner,YearMonth.from(date)).categories()).anySatisfy(row->{
            assertThat(row.categoryId()).isEqualTo(category); assertThat(row.spent()).isEqualByComparingTo("50.25");
        });
        assertThatThrownBy(()->bank.review(owner,entry.id(),"import",new BankService.Review(entry.version(),category,null,true,false))).isInstanceOf(ApiException.class);
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
    }
    @Test void matchingAnExistingMovementDoesNotDuplicateAndDuplicateImportNeedsAcknowledgment() {
        UUID category=category();
        var manual=movements.create(owner,new MovementDtos.Create(UUID.randomUUID(),"Compra manual","50.00",Movement.Kind.EXPENSE,Movement.Status.CONFIRMED,
            account.id(),null,category,date,date.withDayOfMonth(1),date,date,""));
        var link=bind(); var entry=sync(link,List.of(remote("entry-1","50.00","POSTED")));
        assertThat(entry.candidates()).extracting(BankService.Candidate::id).contains(manual.id());
        assertThatThrownBy(()->bank.review(owner,entry.id(),"import",new BankService.Review(entry.version(),category,null,true,false))).isInstanceOf(ApiException.class);
        bank.review(owner,entry.id(),"match",new BankService.Review(entry.version(),null,manual.id(),false,false));
        assertThat(bank.entries(owner,link.id(),0).getFirst().state()).isEqualTo("MATCHED");
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
    }
    @Test void pendingAndChangedLinkedDataAreNeverAutomaticallyPostedOrReimported() {
        var link=bind(); var pending=sync(link,List.of(remote("entry-1","50.00","PENDING"))); UUID category=category();
        assertThatThrownBy(()->bank.review(owner,pending.id(),"import",new BankService.Review(pending.version(),category,null,true,true))).isInstanceOf(ApiException.class);
        allowSync(link.id()); var posted=sync(link,List.of(remote("entry-1","50.00","POSTED")));
        assertThatThrownBy(()->bank.review(owner,posted.id(),"ignore",new BankService.Review(pending.version(),null,null,false,false))).isInstanceOf(ApiException.class);
        bank.review(owner,posted.id(),"import",new BankService.Review(posted.version(),category,null,true,false));
        allowSync(link.id()); var changed=sync(link,List.of(remote("entry-1","60.00","POSTED")));
        assertThat(changed.state()).isEqualTo("CHANGED");
        assertThatThrownBy(()->bank.review(owner,changed.id(),"import",new BankService.Review(changed.version(),category,null,true,true))).isInstanceOf(ApiException.class);
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
    }
    @Test void disconnectPreservesHistoryAndStopsReadsUntilReconnected() {
        var link=bind(); var entry=sync(link,List.of(remote("entry-1","50.00","POSTED")));
        bank.disconnect(owner,link.id());
        assertThat(bank.entries(owner,link.id(),0)).hasSize(1);
        assertThatThrownBy(()->bank.sync(owner,link.id(),new BankService.Sync(link.importFrom(),today))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->bank.review(owner,entry.id(),"ignore",new BankService.Review(entry.version(),null,null,false,false))).isInstanceOf(ApiException.class);
        bank.reconnect(owner,link.id());
        bank.review(owner,entry.id(),"ignore",new BankService.Review(entry.version(),null,null,false,false));
        assertThat(bank.entries(owner,link.id(),0).getFirst().state()).isEqualTo("IGNORED");
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
    }
    @Test void cannotReadUnlistedItemsOrOtherProfilesAndSecretsAreNeverInConfiguration() throws Exception {
        mvc.perform(get("/api/banking/config")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/banking/config").with(user(principal))).andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true))
            .andExpect(jsonPath("$.clientSecret").doesNotExist()).andExpect(jsonPath("$.clientId").doesNotExist());
        UUID other=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Outro","UmaSenhaSegura123!")).id();
        var stranger=new UserPrincipal(other,"",0);
        mvc.perform(get("/api/banking/config").with(user(stranger))).andExpect(jsonPath("$.ready").value(false)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/banking/links").with(user(stranger))).andExpect(status().isForbidden());
        mvc.perform(get("/api/banking/items/"+UUID.randomUUID()+"/accounts").with(user(principal))).andExpect(status().isNotFound());
        mvc.perform(post("/api/banking/links").with(user(principal)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(provider);
    }
    @Test void openingDateCurrencyAndBaselineGuardAreEnforced() {
        assertThatThrownBy(()->bank.bind(owner,new BankService.Bind(item,external,account.id(),today.minusDays(9),false))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->bank.bind(owner,new BankService.Bind(item,external,account.id(),account.openingDate(),true))).isInstanceOf(ApiException.class);
        UUID other=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Outro","UmaSenhaSegura123!")).id();
        var foreign=accounts.create(other,new AccountService.Create("Outra conta","",Account.Kind.CHECKING,"0",today.minusDays(10)));
        assertThatThrownBy(()->bank.bind(owner,new BankService.Bind(item,external,foreign.id(),today.minusDays(9),true))).isInstanceOf(ApiException.class);
        when(provider.accounts(item)).thenReturn(new BankProvider.Snapshot(clock.instant(),List.of(new BankProvider.RemoteAccount(external,"Cartão","CREDIT","BRL",BigDecimal.ZERO))));
        assertThatThrownBy(()->bank.bind(owner,new BankService.Bind(item,external,account.id(),today.minusDays(9),true))).isInstanceOf(ApiException.class);
    }
    @Test void syncFailureNeverConfirmsPartialDataAndCannotBypassImportPeriod() {
        var link=bind();
        when(provider.transactions(external,link.importFrom(),today)).thenThrow(ApiException.bad("Falha fictícia"));
        assertThatThrownBy(()->bank.sync(owner,link.id(),new BankService.Sync(link.importFrom(),today))).isInstanceOf(ApiException.class);
        assertThat(bank.entries(owner,link.id(),0)).isEmpty();
        assertThatThrownBy(()->bank.sync(owner,link.id(),new BankService.Sync(account.openingDate(),today))).isInstanceOf(ApiException.class);
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
    }
    @Test void diagnosticPreservesStorageExceptionAndRollsBackWholeRead() {
        var link=bind();
        when(provider.transactions(external,link.importFrom(),today)).thenReturn(List.of(
            remote("valid-first","10.00","POSTED"),
            new BankProvider.RemoteEntry("invalid-second","x".repeat(201),new BigDecimal("20.00"),date,"DEBIT","POSTED")));
        assertThatThrownBy(()->bank.sync(owner,link.id(),new BankService.Sync(link.importFrom(),today)))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(bank.entries(owner,link.id(),0)).isEmpty();
        assertThat(bank.links(owner).getFirst().lastCheckedAt()).isNull();
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
    }
    @Test void ownTransferCanMatchBothAccountsWithoutBecomingIncomeOrExpense() {
        var destination=accounts.create(owner,new AccountService.Create("Outra conta fictícia","",Account.Kind.CHECKING,"100",today.minusDays(10)));
        UUID externalDestination=UUID.randomUUID();
        when(provider.accounts(item)).thenReturn(new BankProvider.Snapshot(clock.instant(),List.of(
            new BankProvider.RemoteAccount(external,"A origem","BANK","BRL",new BigDecimal("950")),
            new BankProvider.RemoteAccount(externalDestination,"B destino","BANK","BRL",new BigDecimal("150")))));
        bank.bind(owner,new BankService.Bind(item,external,account.id(),today.minusDays(9),true));
        bank.bind(owner,new BankService.Bind(item,externalDestination,destination.id(),today.minusDays(9),true));
        var transfer=movements.create(owner,new MovementDtos.Create(UUID.randomUUID(),"Transferência própria","50",Movement.Kind.TRANSFER,Movement.Status.CONFIRMED,
            account.id(),destination.id(),null,date,date.withDayOfMonth(1),date,date,""));
        for(var link:bank.links(owner)) {
            boolean debit=link.accountId().equals(account.id());
            when(provider.transactions(link.externalAccountId(),link.importFrom(),today)).thenReturn(List.of(
                new BankProvider.RemoteEntry(debit?"debit-transfer":"credit-transfer","Transferência própria",new BigDecimal("50"),date,debit?"DEBIT":"CREDIT","POSTED")));
            bank.sync(owner,link.id(),new BankService.Sync(link.importFrom(),today));
            var entry=bank.entries(owner,link.id(),0).getFirst();
            assertThat(entry.candidates()).extracting(BankService.Candidate::id).contains(transfer.id());
            bank.review(owner,entry.id(),"match",new BankService.Review(entry.version(),null,transfer.id(),false,false));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movement WHERE owner_id=? AND kind IN ('INCOME','EXPENSE')",Long.class,owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_entry WHERE owner_id=? AND state='MATCHED'",Long.class,owner)).isEqualTo(2);
        assertThat(reports.summary(owner,YearMonth.from(date)).records()).isZero();
        assertThat(reports.summary(owner,YearMonth.from(date)).cashFlow()).isZero();
        assertThat(feed.list(owner,account.openingDate(),today,destination.id(),null,Movement.Kind.TRANSFER,null,"",0,20,"operationDate","desc","operationDate",false).total()).isEqualTo(1);
    }
    @Test void simultaneousConfirmationsCannotCreateTwoMovements() throws Exception {
        var link=bind(); var entry=sync(link,List.of(remote("concurrent","50.25","POSTED"))); UUID category=category();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> action=()->{
                try { bank.review(owner,entry.id(),"import",new BankService.Review(entry.version(),category,null,true,true)); return true; }
                catch(ApiException ex) { return false; }
            };
            var results=pool.invokeAll(List.of(action,action));
            assertThat(results.get(0).get() ^ results.get(1).get()).isTrue();
        } finally { pool.shutdownNow(); }
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movement WHERE owner_id=? AND source='MEU_PLUGGY'",Long.class,owner)).isEqualTo(1);
    }
    @Test void bankAndManualBalancesAreCombinedOnceAndCancellationOnlyChangesReports() {
        var manual=accounts.create(owner,new AccountService.Create("Carteira manual","",Account.Kind.WALLET,"100",today.minusDays(10)));
        UUID category=category();
        movements.create(owner,new MovementDtos.Create(UUID.randomUUID(),"Despesa manual","20",Movement.Kind.EXPENSE,Movement.Status.CONFIRMED,
            manual.id(),null,category,date,date.withDayOfMonth(1),date,date,""));
        var link=bind(); var entry=sync(link,List.of(remote("classified","50.25","POSTED")));
        bank.review(owner,entry.id(),"import",new BankService.Review(entry.version(),category,null,true,true));
        var summary=reports.summary(owner,YearMonth.from(date));
        assertThat(summary.balance()).isEqualByComparingTo("1030"); // 950 bank + 80 calculated, no second debit.
        assertThat(summary.cashFlow()).isEqualByComparingTo("-70.25");
        assertThat(summary.competence().expenses()).isEqualByComparingTo("70.25");
        var classified=movements.get(owner,bank.entries(owner,link.id(),0).getFirst().movementId());
        movements.cancel(owner,classified.id(),new MovementDtos.Change(classified.version(),"Correção de classificação",null));
        summary=reports.summary(owner,YearMonth.from(date));
        assertThat(summary.balance()).isEqualByComparingTo("1030");
        assertThat(summary.cashFlow()).isEqualByComparingTo("-20");
        assertThat(summary.competence().expenses()).isEqualByComparingTo("20");
        assertThat(accounts.list(owner)).anySatisfy(a->{assertThat(a.id()).isEqualTo(manual.id());
            assertThat(a.balance()).isEqualByComparingTo("80"); assertThat(a.balanceSource()).isEqualTo(AccountBalanceService.Source.CALCULATED);});
    }
    @Test void zeroMissingAndDisconnectedBankSnapshotsNeverFallBackToCalculatedBalance() throws Exception {
        var link=bind();
        jdbc.update("UPDATE bank_link SET reported_balance=0 WHERE id=?",link.id());
        assertThat(accounts.list(owner).getFirst().balance()).isZero();
        assertThat(reports.summary(owner,YearMonth.from(date)).balance()).isZero();
        bank.disconnect(owner,link.id());
        var position=accounts.list(owner).getFirst();
        assertThat(position.balanceSource()).isEqualTo(AccountBalanceService.Source.BANK);
        assertThat(position.bankReadActive()).isFalse();
        assertThat(position.bankUpdatedAt()).isCloseTo(snapshot.updatedAt(),within(1,java.time.temporal.ChronoUnit.MILLIS));
        assertThat(position.balance()).isZero();
        jdbc.update("UPDATE bank_link SET reported_balance=null WHERE id=?",link.id());
        assertThat(accounts.list(owner).getFirst().balance()).isNull();
        assertThat(reports.summary(owner,YearMonth.from(date)).balance()).isNull();
        assertThat(reports.summary(owner,YearMonth.from(date)).balanceComplete()).isFalse();
        mvc.perform(get("/api/accounts").with(user(principal))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].balance").isEmpty()).andExpect(jsonPath("$[0].balanceSource").value("BANK"));
        assertThat(ledger.balance(owner,account.id(),today)).isEqualByComparingTo("1000");
    }
    @Test void newBankReadUpdatesPrimaryBalanceWhileLegacyPostingsArePreservedAndOwnersIsolated() {
        var manual=movements.create(owner,new MovementDtos.Create(UUID.randomUUID(),"Legado","50",Movement.Kind.EXPENSE,Movement.Status.CONFIRMED,
            account.id(),null,category(),date,date.withDayOfMonth(1),date,date,""));
        jdbc.update("UPDATE movement SET source='MEU_PLUGGY',external_reference='legacy' WHERE id=?",manual.id());
        var link=bind();
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
        when(provider.accounts(item)).thenReturn(new BankProvider.Snapshot(clock.instant(),List.of(
            new BankProvider.RemoteAccount(external,"Nubank fictício","BANK","BRL",new BigDecimal("900")))));
        sync(link,List.of(remote("new-pending","50","POSTED")));
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("900");
        assertThat(reports.summary(owner,YearMonth.from(date)).cashFlow()).isEqualByComparingTo("-100");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posting WHERE movement_id=?",Long.class,manual.id())).isEqualTo(1);
        UUID other=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Outro","UmaSenhaSegura123!")).id();
        accounts.create(other,new AccountService.Create("Manual","",Account.Kind.WALLET,"17",today.minusDays(1)));
        assertThat(accounts.list(other)).hasSize(1);
        assertThat(reports.summary(other,YearMonth.from(today)).balance()).isEqualByComparingTo("17");
    }
    FinancialRecordService.Page feed(boolean hidden,int page,int size) {
        return feed.list(owner,account.openingDate(),today,null,null,null,null,"",page,size,"operationDate","desc","operationDate",hidden);
    }
    @Test void bankRecordsAppearAndFeedReportsAutomaticallyWithoutCreatingMovementsOrPostings() {
        var link=bind();
        sync(link,List.of(remote("expense","50","POSTED"),remote("pending","900","PENDING"),
            new BankProvider.RemoteEntry("income","Entrada fictícia",new BigDecimal("100"),date,"CREDIT","POSTED")));
        clearInvocations(provider);
        assertThat(feed(false,0,20).items()).hasSize(4); // Opening + three bank records, including pending.
        var summary=reports.summary(owner,YearMonth.from(date));
        assertThat(summary.competence().income()).isEqualByComparingTo("100");
        assertThat(summary.competence().expenses()).isEqualByComparingTo("50");
        assertThat(summary.cashFlow()).isEqualByComparingTo("50");
        assertThat(summary.records()).isEqualTo(2);
        assertThat(summary.unclassifiedBankRecords()).isEqualTo(2);
        assertThat(summary.categories()).singleElement().satisfies(c->{assertThat(c.id()).isNull();assertThat(c.name()).isEqualTo("Sem categoria");assertThat(c.total()).isEqualByComparingTo("50");});
        assertThat(budgets.month(owner,YearMonth.from(date)).totals().unbudgetedSpent()).isEqualByComparingTo("50");
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movement WHERE owner_id=? AND kind!='OPENING'",Long.class,owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posting WHERE owner_id=?",Long.class,owner)).isEqualTo(1);
        assertThat(bank.entries(owner,link.id(),0)).allSatisfy(e->assertThat(e.state()).isEqualTo("REVIEW"));
        verifyNoInteractions(provider);
    }
    @Test void hidingIsReversibleAuditedAndSurvivesSyncWhileBalancesStayUnchanged() {
        var link=bind(); var entry=sync(link,List.of(remote("hidden","50","POSTED")));
        bank.visibility(owner,entry.id(),new BankService.Visibility(entry.version(),true,"Não representa gasto"));
        assertThat(feed(true,0,20).items()).singleElement().satisfies(e->assertThat(e.bankEntryId()).isEqualTo(entry.id()));
        assertThat(reports.summary(owner,YearMonth.from(date)).competence().expenses()).isZero();
        assertThat(budgets.month(owner,YearMonth.from(date)).totals().unbudgetedSpent()).isZero();
        assertThatThrownBy(()->bank.visibility(owner,entry.id(),new BankService.Visibility(entry.version(),false,""))).isInstanceOf(ApiException.class);
        allowSync(link.id()); var updated=sync(link,List.of(remote("hidden","60","POSTED")));
        assertThat(feed(true,0,20).items()).singleElement().satisfies(e->assertThat(e.amount()).isEqualByComparingTo("60"));
        bank.disconnect(owner,link.id()); // Visibility edits still work with bank reads paused.
        bank.visibility(owner,entry.id(),new BankService.Visibility(updated.version(),false,"Registro corrigido"));
        assertThat(feed(true,0,20).items()).isEmpty();
        assertThat(reports.summary(owner,YearMonth.from(date)).competence().expenses()).isEqualByComparingTo("60");
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE owner_id=? AND resource_id=? AND action IN ('BANK_HIDDEN','BANK_RESTORED')",Long.class,owner,entry.id())).isEqualTo(2);
    }
    @Test void classifyingMovesUncategorizedValueIntoCategoryWithoutDuplicatingReportsAndCanBeHidden() {
        var link=bind(); var entry=sync(link,List.of(remote("categorize","50","POSTED"))); UUID category=category();
        bank.review(owner,entry.id(),"import",new BankService.Review(entry.version(),category,null,true,false));
        var summary=reports.summary(owner,YearMonth.from(date));
        assertThat(summary.records()).isEqualTo(1); assertThat(summary.unclassifiedBankRecords()).isZero();
        assertThat(summary.competence().expenses()).isEqualByComparingTo("50");
        assertThat(summary.categories()).singleElement().satisfies(c->assertThat(c.id()).isEqualTo(category));
        assertThat(feed(false,0,20).items()).hasSize(2);
        var classified=bank.entries(owner,link.id(),0).getFirst();
        bank.visibility(owner,entry.id(),new BankService.Visibility(classified.version(),true,"Duplicado"));
        assertThat(reports.summary(owner,YearMonth.from(date)).records()).isZero();
        assertThat(budgets.month(owner,YearMonth.from(date)).categories()).anySatisfy(c->{assertThat(c.categoryId()).isEqualTo(category);assertThat(c.spent()).isZero();});
        bank.visibility(owner,entry.id(),new BankService.Visibility(classified.version()+1,false,"Restaurado"));
        assertThat(reports.summary(owner,YearMonth.from(date)).records()).isEqualTo(1);
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
    }
    @Test void feedAndVisibilityEnforceOwnerCsrfVersionsFiltersAndPagination() throws Exception {
        var link=bind(); var entry=sync(link,List.of(remote("secure","50","POSTED")));
        assertThat(feed(false,0,1).total()).isEqualTo(2);
        assertThat(feed(false,0,1).items()).hasSize(1);
        assertThat(feed(false,1,1).items()).hasSize(1);
        assertThat(feed.list(owner,date,date,null,"uncategorized",Movement.Kind.EXPENSE,Movement.Status.CONFIRMED,"Compra",0,20,"amount","asc","operationDate",false).total()).isEqualTo(1);
        assertThatThrownBy(()->feed.list(owner,date,date,null,null,null,null,"",0,20,"amount;delete","asc","operationDate",false)).isInstanceOf(ApiException.class);
        UUID stranger=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Outro","UmaSenhaSegura123!")).id();
        assertThat(feed.list(stranger,date,date,null,null,null,null,"",0,20,"amount","asc","operationDate",false).total()).isZero();
        mvc.perform(get("/api/movements/feed").param("from",date.toString()).param("to",date.toString())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/banking/entries/"+entry.id()+"/visibility").with(user(principal)).contentType("application/json").content("{\"version\":0,\"hidden\":true}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/banking/entries/"+entry.id()+"/visibility").with(user(new UserPrincipal(stranger,"",0))).with(csrf()).contentType("application/json").content("{\"version\":0,\"hidden\":true}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/banking/entries/"+UUID.randomUUID()+"/visibility").with(user(principal)).with(csrf()).contentType("application/json").content("{\"version\":0,\"hidden\":true}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/banking/entries/"+entry.id()+"/visibility").with(user(principal)).with(csrf()).contentType("application/json").content("{\"version\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/banking/entries/"+entry.id()+"/visibility").with(user(principal)).with(csrf()).contentType("application/json").content("{\"version\":0,\"hidden\":true}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/movements/feed").with(user(principal)).param("from",date.toString()).param("to",date.toString()).param("hidden","true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].bankEntryVersion").value(1));
    }
    String event(UUID eventId, UUID itemId) {
        return "{\"event\":\"item/updated\",\"eventId\":\""+eventId+"\",\"itemId\":\""+itemId+"\",\"clientUserId\":\"untrusted-ignored\"}";
    }
    @Test void webhookAuthenticatesWithoutSessionAndDuplicateDeliveryNeverPostsLedger() throws Exception {
        var link=bind(); UUID eventId=UUID.randomUUID();
        when(provider.transactions(external,link.importFrom(),today)).thenReturn(List.of(remote("automatic-1","25","POSTED")));
        for(int i=0;i<2;i++) mvc.perform(post("/api/banking/hooks/pluggy").header("X-JavaFinance-Webhook",config.getWebhookSecret())
            .contentType("application/json").content(event(eventId,item))).andExpect(status().isNoContent());
        assertThat(bank.entries(owner,link.id(),0)).hasSize(1).allMatch(e->e.state().equals("REVIEW"));
        assertThat(bank.links(owner).getFirst().autoCheckedThrough()).isEqualTo(today);
        verify(provider,times(1)).transactions(external,link.importFrom(),today);
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("950");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movement WHERE owner_id=? AND kind!='OPENING'",Long.class,owner)).isZero();
    }
    @Test void failedWebhookIsDurableAndSameDeliveryRetriesSuccessfully() throws Exception {
        var link=bind(); UUID eventId=UUID.randomUUID();
        when(provider.transactions(external,link.importFrom(),today)).thenThrow(ApiException.bad("Provider failed"));
        mvc.perform(post("/api/banking/hooks/pluggy").header("X-JavaFinance-Webhook",config.getWebhookSecret()).content(event(eventId,item))).andExpect(status().isServiceUnavailable());
        assertThat(bank.links(owner).getFirst().autoErrorAt()).isNotNull();
        assertThat(bank.links(owner).getFirst().autoCheckedThrough()).isNull();
        allowSync(link.id());
        doReturn(List.of(remote("retry-1","25","POSTED"))).when(provider).transactions(external,link.importFrom(),today);
        mvc.perform(post("/api/banking/hooks/pluggy").header("X-JavaFinance-Webhook",config.getWebhookSecret()).content(event(eventId,item))).andExpect(status().isNoContent());
        assertThat(bank.links(owner).getFirst().autoErrorAt()).isNull();
        assertThat(bank.entries(owner,link.id(),0)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_sync_event WHERE owner_id=?",Long.class,owner)).isEqualTo(1);
    }
    @Test void anEventDuringProcessingRemainsPendingAndDailyPollingRecoversWithoutWebhook() {
        var link=bind(); automatic.enqueue(item,"first");
        when(provider.transactions(external,link.importFrom(),today)).thenAnswer(invocation->{
            automatic.enqueue(item,"arrived-during-processing"); return List.of();
        });
        assertThat(automatic.process(item)).isTrue();
        assertThat(jdbc.queryForObject("SELECT requested_version-completed_version FROM bank_sync_job WHERE owner_id=? AND item_id=?",Long.class,owner,item)).isEqualTo(1);
        allowSync(link.id());
        when(provider.transactions(external,link.importFrom(),today)).thenReturn(List.of());
        assertThat(automatic.daily()).isTrue();
        assertThat(automatic.daily()).isTrue(); // Same-day cron retry does not create another job/read.
        assertThat(jdbc.queryForObject("SELECT requested_version-completed_version FROM bank_sync_job WHERE owner_id=? AND item_id=?",Long.class,owner,item)).isZero();
        verify(provider,times(2)).transactions(external,link.importFrom(),today);
    }
    @Test void activeLeasePreventsConcurrentProcessingAndExpiredLeaseCanRecover() {
        var link=bind(); automatic.enqueue(item,"lease-test");
        jdbc.update("UPDATE bank_sync_job SET lease_token=?,lease_until=? WHERE owner_id=? AND item_id=?",UUID.randomUUID(),java.sql.Timestamp.from(clock.instant().plusSeconds(600)),owner,item);
        clearInvocations(provider);
        assertThat(automatic.process(item)).isFalse(); verifyNoInteractions(provider);
        jdbc.update("UPDATE bank_sync_job SET lease_until=? WHERE owner_id=? AND item_id=?",java.sql.Timestamp.from(clock.instant().minusSeconds(1)),owner,item);
        when(provider.transactions(external,link.importFrom(),today)).thenReturn(List.of());
        assertThat(automatic.process(item)).isTrue();
        assertThat(bank.links(owner).getFirst().autoCheckedThrough()).isEqualTo(today);
    }
    @Test void automationSkipsFutureStartAndDisconnectedLinksAndCannotReadForeignItems() {
        bank.bind(owner,new BankService.Bind(item,external,account.id(),today.plusDays(1),true));
        var link=bank.links(owner).getFirst(); clearInvocations(provider);
        assertThat(automatic.daily()).isTrue(); verifyNoInteractions(provider);
        assertThat(automatic.enqueue(UUID.randomUUID(),"foreign-item")).isFalse();
        bank.disconnect(owner,link.id());
        automatic.enqueue(item,"disabled-link");
        assertThat(automatic.process(item)).isTrue(); verifyNoInteractions(provider);
        assertThat(bank.links(owner).getFirst().autoCheckedThrough()).isNull();
    }
    @Test void longHistoryUsesBoundedResumableWindowsAndKeepsOpeningBoundary() {
        var historical=accounts.create(owner,new AccountService.Create("Historical","",Account.Kind.CHECKING,"1000",today.minusDays(181)));
        bank.bind(owner,new BankService.Bind(item,external,historical.id(),today.minusDays(180),true));
        var link=bank.links(owner).getFirst();
        when(provider.transactions(eq(external),any(LocalDate.class),any(LocalDate.class))).thenReturn(List.of());
        automatic.enqueue(item,"history"); assertThat(automatic.process(item)).isFalse();
        LocalDate firstTo=link.importFrom().plusDays(92);
        assertThat(bank.links(owner).getFirst().autoCheckedThrough()).isEqualTo(firstTo);
        verify(provider).transactions(external,link.importFrom(),firstTo);
        allowSync(link.id()); assertThat(automatic.process(item)).isFalse();
        LocalDate secondTo=firstTo.minusDays(6).plusDays(92);
        verify(provider).transactions(external,firstTo.minusDays(6),secondTo);
        allowSync(link.id()); assertThat(automatic.process(item)).isTrue();
        assertThat(bank.links(owner).getFirst().autoCheckedThrough()).isEqualTo(today);
    }
    @Test void machineEndpointsRejectSessionOnlyInvalidSecretsMalformedAndOversizedPayloads() throws Exception {
        mvc.perform(post("/api/banking/hooks/pluggy").with(user(principal)).with(csrf()).content(event(UUID.randomUUID(),item))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/banking/jobs/daily").with(user(principal))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/banking/jobs/daily").header("Authorization","Bearer wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/banking/hooks/pluggy").header("X-JavaFinance-Webhook",config.getWebhookSecret()).content("{")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/banking/hooks/pluggy").header("X-JavaFinance-Webhook",config.getWebhookSecret()).content("x".repeat(8193))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/banking/hooks/pluggy").header("X-JavaFinance-Webhook",config.getWebhookSecret()).content(event(UUID.randomUUID(),UUID.randomUUID()))).andExpect(status().isNoContent());
        mvc.perform(post("/api/banking/links").with(user(principal)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(provider);
    }
    @Test void dailyEndpointUsesSeparateSecretAndMissingConfigurationFailsClosed() throws Exception {
        bind(); when(provider.transactions(eq(external),any(LocalDate.class),any(LocalDate.class))).thenReturn(List.of());
        mvc.perform(get("/api/banking/jobs/daily").header("Authorization","Bearer "+config.getCronSecret())).andExpect(status().isNoContent());
        clearInvocations(provider); config.setWebhookSecret("");
        mvc.perform(get("/api/banking/jobs/daily").header("Authorization","Bearer "+config.getCronSecret())).andExpect(status().isUnauthorized());
        assertThat(automatic.daily()).isFalse(); verifyNoInteractions(provider);
        assertThat(bank.config(owner).automaticRead()).isFalse();
    }
    @Test void dailyRolloverRechecksNinetyThreeDaysRatherThanOnlyNewDay() {
        var historical=accounts.create(owner,new AccountService.Create("Historical","",Account.Kind.CHECKING,"1000",today.minusDays(181)));
        bank.bind(owner,new BankService.Bind(item,external,historical.id(),today.minusDays(180),true));
        var link=bank.links(owner).getFirst();
        jdbc.update("UPDATE bank_link SET auto_checked_through=? WHERE id=?",today.minusDays(1),link.id());
        when(provider.transactions(external,today.minusDays(92),today)).thenReturn(List.of());
        automatic.enqueue(item,"new-day"); assertThat(automatic.process(item)).isTrue();
        verify(provider).transactions(external,today.minusDays(92),today);
        assertThat(bank.links(owner).getFirst().autoCheckedThrough()).isEqualTo(today);
    }
}
