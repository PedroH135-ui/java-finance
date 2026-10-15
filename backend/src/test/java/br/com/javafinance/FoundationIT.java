package br.com.javafinance;
import br.com.javafinance.accounts.*;
import br.com.javafinance.categories.*;
import br.com.javafinance.movements.*;
import br.com.javafinance.reports.*;
import br.com.javafinance.users.*;
import br.com.javafinance.common.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.registration-enabled=true")
@AutoConfigureMockMvc
class FoundationIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        TestDatabase.configure(r);
    }
    @Autowired UserService users; @Autowired AccountService accounts; @Autowired CategoryService categories;
    @Autowired MovementService movements; @Autowired ReportService reports; @Autowired UserRepository userRepository;
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired Flyway flyway;
    @Autowired AuditService audit;
    UUID owner; UserPrincipal principal; LocalDate date=LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    @BeforeEach void setup() {
        var user=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Teste","UmaSenhaSegura123!"));
        owner=user.id(); var entity=userRepository.findById(owner).orElseThrow(); principal=new UserPrincipal(owner,entity.passwordHash,0);
    }
    AccountService.View account(String name,String opening) { return accounts.create(owner,new AccountService.Create(name,"",Account.Kind.CHECKING,opening,date.minusDays(10))); }
    CategoryService.View category(Category.Kind kind) { return categories.create(owner,new CategoryService.Create(kind.name(),kind,kind==Category.Kind.EXPENSE)); }
    MovementDtos.Create input(AccountService.View a,UUID dest,UUID cat,Movement.Kind kind,String amount,Movement.Status status) {
        return new MovementDtos.Create(UUID.randomUUID(),"Teste financeiro",amount,kind,status,a.id(),dest,cat,date,date.withDayOfMonth(1),date,
            status==Movement.Status.CONFIRMED?date:null,"");
    }
    @Test void initialBalanceIsNotIncomeAndSavingsUsesCompetence() {
        var a=account("Corrente","5000");
        movements.create(owner,input(a,null,category(Category.Kind.INCOME).id(),Movement.Kind.INCOME,"6000",Movement.Status.CONFIRMED));
        movements.create(owner,input(a,null,category(Category.Kind.EXPENSE).id(),Movement.Kind.EXPENSE,"3500",Movement.Status.CONFIRMED));
        var report=reports.summary(owner,YearMonth.from(date));
        assertThat(report.balance()).isEqualByComparingTo("7500");
        assertThat(report.competence().result()).isEqualByComparingTo("2500");
        assertThat(report.competence().savingsRate()).isEqualByComparingTo("41.67");
        assertThat(report.cashFlow()).isEqualByComparingTo("2500");
    }
    @Test void plannedTransferAndCancellationAreAtomic() {
        var a=account("Origem","1000"); var b=account("Destino","100");
        var planned=movements.create(owner,input(a,b.id(),null,Movement.Kind.TRANSFER,"300",Movement.Status.PLANNED));
        assertThat(accounts.list(owner).stream().filter(x->x.id().equals(a.id())).findFirst().orElseThrow().balance()).isEqualByComparingTo("1000");
        var confirmed=movements.confirm(owner,planned.id(),new MovementDtos.Change(planned.version(),"Transferência realizada.",date));
        var list=accounts.list(owner);
        assertThat(list.stream().filter(x->x.id().equals(a.id())).findFirst().orElseThrow().balance()).isEqualByComparingTo("700");
        assertThat(list.stream().filter(x->x.id().equals(b.id())).findFirst().orElseThrow().balance()).isEqualByComparingTo("400");
        assertThat(reports.summary(owner,YearMonth.from(date)).balance()).isEqualByComparingTo("1100");
        assertThat(reports.summary(owner,YearMonth.from(date)).competence().income()).isEqualByComparingTo("0");
        assertThat(reports.summary(owner,YearMonth.from(date)).competence().savingsRate()).isNull();
        movements.cancel(owner,confirmed.id(),new MovementDtos.Change(confirmed.version(),"Transferência cancelada.",null));
        assertThat(accounts.list(owner).stream().filter(x->x.id().equals(a.id())).findFirst().orElseThrow().balance()).isEqualByComparingTo("1000");
        assertThat(accounts.list(owner).stream().filter(x->x.id().equals(b.id())).findFirst().orElseThrow().balance()).isEqualByComparingTo("100");
    }
    @Test void userCannotReadModifyOrTransferAnotherUsersData() throws Exception {
        var a=account("Privada","50"); var cat=category(Category.Kind.INCOME);
        var m=movements.create(owner,input(a,null,cat.id(),Movement.Kind.INCOME,"10",Movement.Status.CONFIRMED));
        var other=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Outro","OutraSenhaSegura123!"));
        var stranger=new UserPrincipal(other.id(),"",0);
        mvc.perform(get("/api/movements/"+m.id()).with(user(stranger))).andExpect(status().isNotFound());
        mvc.perform(get("/api/movements/"+m.id()+"/audit").with(user(stranger))).andExpect(status().isNotFound());
        mvc.perform(post("/api/movements/"+m.id()+"/cancel").with(user(stranger)).with(csrf()).contentType("application/json")
            .content(mapper.writeValueAsString(new MovementDtos.Change(m.version(),"Não permitido.",null)))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/accounts/"+a.id()).with(user(stranger)).with(csrf()).contentType("application/json")
            .content(mapper.writeValueAsString(new AccountService.Edit("Tentativa","",true,a.version())))).andExpect(status().isNotFound());
        var foreignInput=input(a,null,cat.id(),Movement.Kind.INCOME,"20",Movement.Status.CONFIRMED);
        mvc.perform(post("/api/movements").with(user(stranger)).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(foreignInput))).andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts").with(user(stranger))).andExpect(content().json("[]"));
        mvc.perform(get("/api/reports/summary").param("month",YearMonth.from(date).toString()).with(user(stranger))).andExpect(jsonPath("$.balance").value("0.00"));
    }
    @Test void csrfLoginLogoutAndPasswordInvalidation() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(status().isOk()).andExpect(jsonPath("$.registrationEnabled").value(true));
        var email=users.me(owner).email();
        mvc.perform(post("/api/auth/login").param("username",email).param("password","UmaSenhaSegura123!")).andExpect(status().isForbidden());
        var login=mvc.perform(post("/api/auth/login").with(csrf()).param("username",email).param("password","UmaSenhaSegura123!")).andExpect(status().isNoContent()).andReturn();
        var session=(MockHttpSession)login.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        users.password(owner,new UserService.PasswordChange("UmaSenhaSegura123!","NovaSenhaSegura456!"));
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        var newLogin=mvc.perform(post("/api/auth/login").with(csrf()).param("username",email).param("password","NovaSenhaSegura456!")).andExpect(status().isNoContent()).andReturn();
        var newSession=(MockHttpSession)newLogin.getRequest().getSession(false);
        mvc.perform(post("/api/auth/logout").session(newSession).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void concurrentResubmissionsCreateOneMovement() throws Exception {
        var a=account("Reenvio","0"); var cat=category(Category.Kind.INCOME);
        var input=input(a,null,cat.id(),Movement.Kind.INCOME,"120.33",Movement.Status.CONFIRMED);
        try(var pool=Executors.newFixedThreadPool(4)) {
            var futures=new ArrayList<Future<MovementDtos.View>>();
            for(int i=0;i<8;i++) futures.add(pool.submit(()->movements.create(owner,input)));
            Set<UUID> ids=new HashSet<>(); for(var f:futures)ids.add(f.get(20,TimeUnit.SECONDS).id());
            assertThat(ids).hasSize(1);
        }
        assertThat(reports.summary(owner,YearMonth.from(date)).balance()).isEqualByComparingTo("120.33");
    }
    @Test void ownershipFailureRollsBackTransferAndArchivedAccountIsProtected() {
        var a=account("Origem","100");
        assertThatThrownBy(()->movements.create(owner,input(a,UUID.randomUUID(),null,Movement.Kind.TRANSFER,"30",Movement.Status.CONFIRMED))).isInstanceOf(ApiException.class);
        assertThat(reports.summary(owner,YearMonth.from(date)).balance()).isEqualByComparingTo("100");
        accounts.edit(owner,a.id(),new AccountService.Edit(a.name(),"",true,a.version()));
        assertThatThrownBy(()->movements.create(owner,input(a,null,category(Category.Kind.INCOME).id(),Movement.Kind.INCOME,"30",Movement.Status.CONFIRMED))).isInstanceOf(ApiException.class);
    }
    @Test void cancelledAndPlannedExpensesDoNotChangeResultsAndRevisionConflictsAreRejected() {
        var a=account("Conta","100"); var cat=category(Category.Kind.EXPENSE);
        var planned=movements.create(owner,input(a,null,cat.id(),Movement.Kind.EXPENSE,"20",Movement.Status.PLANNED));
        var done=movements.confirm(owner,planned.id(),new MovementDtos.Change(planned.version(),"Pago.",date));
        assertThatThrownBy(()->movements.cancel(owner,done.id(),new MovementDtos.Change(planned.version(),"Versão antiga.",null))).isInstanceOf(ApiException.class);
        movements.cancel(owner,done.id(),new MovementDtos.Change(done.version(),"Correção.",null));
        assertThat(reports.summary(owner,YearMonth.from(date)).balance()).isEqualByComparingTo("100");
        assertThat(reports.summary(owner,YearMonth.from(date)).competence().expenses()).isEqualByComparingTo("0");
    }
    @Test void migrationsCanRunAgainWithoutDestroyingData() {
        account("Persistente","42.37");
        flyway.validate(); flyway.migrate();
        assertThat(reports.summary(owner,YearMonth.from(date)).balance()).isEqualByComparingTo("42.37");
    }
    @Test void dashboardDrilldownUsesCompetenceInsteadOfOperationMonth() throws Exception {
        var a=account("Competência","0"); var cat=category(Category.Kind.INCOME);
        var month=YearMonth.from(date).minusMonths(1);
        var request=new MovementDtos.Create(UUID.randomUUID(),"Receita de competência anterior","50",Movement.Kind.INCOME,Movement.Status.CONFIRMED,
            a.id(),null,cat.id(),date,month.atDay(1),date,date,"");
        var created=movements.create(owner,request);
        mvc.perform(get("/api/movements").with(user(principal)).param("from",month.atDay(1).toString()).param("to",month.atEndOfMonth().toString())
            .param("basis","competence").param("status","CONFIRMED").param("kind","INCOME"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(created.id().toString()));
        mvc.perform(get("/api/movements").with(user(principal)).param("from",month.atDay(1).toString()).param("to",month.atEndOfMonth().toString()).param("kind","INCOME"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        assertThat(reports.summary(owner,month).competence().income()).isEqualByComparingTo("50");
    }
    @Test void descriptionRevisionRetainsOriginalTextAndRejectsOldVersion() {
        var a=account("Auditoria","0"); var cat=category(Category.Kind.EXPENSE);
        var original=movements.create(owner,input(a,null,cat.id(),Movement.Kind.EXPENSE,"15",Movement.Status.PLANNED));
        var revised=movements.notes(owner,original.id(),new MovementDtos.NoteChange(original.version(),"Descrição corrigida","Nota corrigida","Correção de digitação."));
        assertThat(revised.description()).isEqualTo("Descrição corrigida");
        var event=audit.forResource(owner,original.id()).stream().filter(e->e.get("action").equals("DESCRIPTION_UPDATED")).findFirst().orElseThrow();
        assertThat(event.get("previous_description")).isEqualTo(original.description());
        assertThat(event.get("previous_notes")).isEqualTo(original.notes());
        assertThatThrownBy(()->movements.notes(owner,original.id(),new MovementDtos.NoteChange(original.version(),"Antiga","","Versão antiga."))).isInstanceOf(ApiException.class);
    }
    @Test void idempotencyRetainsOriginalRequestAfterLifecycleChanges() {
        var a=account("Idempotência","100"); var cat=category(Category.Kind.EXPENSE);
        var request=input(a,null,cat.id(),Movement.Kind.EXPENSE,"20",Movement.Status.PLANNED);
        var initial=movements.create(owner,request);
        var different=new MovementDtos.Create(request.requestKey(),request.description(),request.amount(),request.kind(),Movement.Status.CONFIRMED,
            request.accountId(),null,request.categoryId(),date,date.withDayOfMonth(1),date,date,request.notes());
        assertThatThrownBy(()->movements.create(owner,different)).isInstanceOf(ApiException.class);
        var confirmed=movements.confirm(owner,initial.id(),new MovementDtos.Change(initial.version(),"Pagamento.",date));
        movements.cancel(owner,confirmed.id(),new MovementDtos.Change(confirmed.version(),"Correção.",null));
        assertThat(movements.create(owner,request).status()).isEqualTo(Movement.Status.CANCELLED);
        assertThat(reports.summary(owner,YearMonth.from(date)).balance()).isEqualByComparingTo("100");
    }
}

