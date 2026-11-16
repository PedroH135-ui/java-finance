package br.com.javafinance;

import br.com.javafinance.accounts.*;
import br.com.javafinance.banking.*;
import br.com.javafinance.categories.*;
import br.com.javafinance.common.ApiException;
import br.com.javafinance.reports.ReportService;
import br.com.javafinance.users.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
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

@SpringBootTest(properties={"app.registration-enabled=true","app.category-ai.automatic-initial-delay-ms=3600000"})
@AutoConfigureMockMvc
class AutomaticCategoryIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){TestDatabase.configure(registry);}
    @Autowired JdbcTemplate jdbc; @Autowired UserService users; @Autowired AccountService accounts;
    @Autowired CategoryService categories; @Autowired BankAssistanceService assistance;
    @Autowired AutomaticCategoryService automatic; @Autowired ReportService reports; @Autowired MockMvc mvc;
    @MockitoBean AutomaticCategoryScope scope; @MockitoBean CategoryAdvisor advisor;
    UUID owner,account,link,entry,food,other; LocalDate date=LocalDate.now().minusDays(2);
    @BeforeEach void setup(){
        owner=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Teste automático","UmaSenhaSegura123!")).id();
        account=accounts.create(owner,new AccountService.Create("Banco fictício","",Account.Kind.CHECKING,"1000",date.minusDays(10))).id();
        food=categories.create(owner,new CategoryService.Create("Delivery",Category.Kind.EXPENSE,false)).id();
        other=categories.create(owner,new CategoryService.Create("Outros gastos",Category.Kind.EXPENSE,false)).id();
        link=UUID.randomUUID();
        jdbc.update("INSERT INTO bank_link(id,owner_id,item_id,external_account_id,account_id,name,import_from,reported_balance) VALUES (?,?,?,?,?,?,?,?)",
            link,owner,UUID.randomUUID(),UUID.randomUUID(),account,"Fictício",date.minusDays(9),new java.math.BigDecimal("950"));
        entry=entry(owner,link,"Pizza em domicílio","POSTED");
        when(scope.enabled()).thenReturn(true);when(scope.owner()).thenReturn(owner);when(scope.activeFor(owner)).thenReturn(true);
        when(advisor.suggest(eq(owner),eq("EXPENSE"),any(),anyList())).thenReturn(suggestion(food));
    }
    UUID entry(UUID user,UUID bankLink,String description,String status){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status) VALUES (?,?,?,?,?,?,?,?,?)",
            id,user,bankLink,id.toString(),description,new java.math.BigDecimal("35.25"),date,"DEBIT",status);
        return id;
    }
    CategoryAdvisor.Suggestion suggestion(UUID category){return new CategoryAdvisor.Suggestion(category.toString(),"LOW","LLM","Fictício",1,true,"groq");}
    long version(){return jdbc.queryForObject("SELECT version FROM bank_entry WHERE id=?",Long.class,entry);}
    Object invariants(){
        var report=reports.summary(owner,YearMonth.from(date));
        return List.of(accounts.list(owner).stream().map(a->List.of(a.id(),a.balance())).toList(),report.balance(),report.cashFlow(),report.competence(),
            jdbc.queryForObject("SELECT count(*) FROM movement WHERE owner_id=?",Long.class,owner),
            jdbc.queryForObject("SELECT count(*) FROM posting WHERE owner_id=?",Long.class,owner),
            jdbc.queryForObject("SELECT jsonb_agg(jsonb_build_array(id,amount,direction,description,date,provider_status,state,movement_id,hidden,transfer_id) ORDER BY id)::text FROM bank_entry WHERE owner_id=?",String.class,owner));
    }
    @Test void backgroundClassifiesOnceWithoutOpeningDetailsOrChangingMoney() throws Exception {
        var before=invariants();automatic.runPending();
        assertThat(assistance.assistance(owner,entry).currentCategoryId()).isEqualTo(food);
        assertThat(assistance.assistance(owner,entry).categoryState().source()).isEqualTo("AUTO");
        assertThat(invariants()).isEqualTo(before);
        automatic.runPending();verify(advisor,times(1)).suggest(eq(owner),eq("EXPENSE"),any(),anyList());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE resource_id=? AND action='BANK_CATEGORY_AUTO'",Long.class,entry)).isEqualTo(1);
        mvc.perform(get("/api/movements/feed").param("from",date.toString()).param("to",date.toString()).with(user(new UserPrincipal(owner,"",0))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].categorySource").value("AUTO"));
    }
    @Test void manualCorrectionAndRemovalSurviveRunsUntilExplicitlyAllowedAgain(){
        var before=invariants();automatic.runPending();
        assistance.category(owner,entry,new BankAssistanceService.CategoryChange(version(),other));automatic.runPending();
        assertThat(assistance.assistance(owner,entry).currentCategoryId()).isEqualTo(other);
        assertThat(assistance.assistance(owner,entry).categoryState().source()).isEqualTo("MANUAL");
        assistance.category(owner,entry,new BankAssistanceService.CategoryChange(version(),null));automatic.runPending();
        assertThat(assistance.assistance(owner,entry).currentCategoryId()).isNull();
        verify(advisor,times(1)).suggest(eq(owner),eq("EXPENSE"),any(),anyList());
        assistance.allowAutomatic(owner,entry,version());automatic.runPending();
        assertThat(assistance.assistance(owner,entry).currentCategoryId()).isEqualTo(food);
        assertThat(invariants()).isEqualTo(before);
    }
    @Test void userCorrectionDuringModelCallWinsTheRace(){
        var before=invariants();
        when(advisor.suggest(eq(owner),eq("EXPENSE"),any(),anyList())).thenAnswer(call->{
            assistance.category(owner,entry,new BankAssistanceService.CategoryChange(version(),other));return suggestion(food);
        });
        automatic.runPending();
        assertThat(assistance.assistance(owner,entry).currentCategoryId()).isEqualTo(other);
        assertThat(assistance.assistance(owner,entry).categoryState().source()).isEqualTo("MANUAL");
        assertThat(invariants()).isEqualTo(before);
    }
    @Test void autoLabelsNeverBecomeRagGroundTruthButUserCorrectionsDo(){
        automatic.runPending();entry(owner,link,"Outra compra","POSTED");automatic.runPending();
        var requests=ArgumentCaptor.forClass(CategoryAdvisor.Request.class);
        verify(advisor,times(2)).suggest(eq(owner),eq("EXPENSE"),requests.capture(),anyList());
        assertThat(requests.getValue().history()).isEmpty();
        assistance.category(owner,entry,new BankAssistanceService.CategoryChange(version(),other));
        entry(owner,link,"Nova compra","POSTED");automatic.runPending();
        verify(advisor,times(3)).suggest(eq(owner),eq("EXPENSE"),requests.capture(),anyList());
        assertThat(requests.getValue().history()).anySatisfy(h->{assertThat(h.description()).isEqualTo("Pizza em domicílio");assertThat(h.categoryId()).isEqualTo(other.toString());});
    }
    @Test void abstentionIsNotAppliedAndIsNotRetriedEveryPoll(){
        var before=invariants();when(advisor.suggest(eq(owner),eq("EXPENSE"),any(),anyList())).thenReturn(new CategoryAdvisor.Suggestion(null,"NONE","NONE","Sem contexto",0,false));
        automatic.runPending();automatic.runPending();
        assertThat(assistance.assistance(owner,entry).currentCategoryId()).isNull();
        verify(advisor,times(1)).suggest(eq(owner),eq("EXPENSE"),any(),anyList());
        assertThat(invariants()).isEqualTo(before);
    }
    @Test void pendingHiddenAndManualRecordsAreSkippedAndScopeIsEnforced() throws Exception {
        jdbc.update("UPDATE bank_entry SET hidden=true WHERE id=?",entry);
        UUID pending=entry(owner,link,"Pendente","PENDING");
        UUID manual=entry(owner,link,"Escolha anterior","POSTED");
        assistance.category(owner,manual,new BankAssistanceService.CategoryChange(0,other));automatic.runPending();
        verifyNoInteractions(advisor);
        assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,pending)).isNull();
        UUID stranger=UUID.randomUUID();
        assertThatThrownBy(()->assistance.applyAutomatic(stranger,entry,0,suggestion(food))).isInstanceOf(ApiException.class);
        mvc.perform(get("/api/banking/category-automation")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/banking/entries/"+manual+"/category/automatic/allow").with(user(new UserPrincipal(owner,"",0))).contentType("application/json").content("{\"version\":1}"))
            .andExpect(status().isForbidden());
    }
    @Test void foreignAndArchivedCategoriesAreNeverApplied(){
        assertThat(assistance.applyAutomatic(owner,entry,version(),suggestion(UUID.randomUUID()))).isFalse();
        jdbc.update("UPDATE category SET archived=true WHERE id=?",food);
        assertThat(assistance.applyAutomatic(owner,entry,version(),suggestion(food))).isFalse();
        assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,entry)).isNull();
    }
    @Test void changedBankDescriptionStillProtectsManualChoiceUntilExplicitPermission(){
        assistance.category(owner,entry,new BankAssistanceService.CategoryChange(version(),other));
        jdbc.update("UPDATE bank_entry SET description='Descrição corrigida pelo banco',version=version+1 WHERE id=?",entry);
        automatic.runPending();verifyNoInteractions(advisor);
        assertThat(assistance.assistance(owner,entry).currentCategoryId()).isNull();
        assertThat(assistance.assistance(owner,entry).categoryState().automaticBlocked()).isTrue();
        assistance.allowAutomatic(owner,entry,version());automatic.runPending();
        assertThat(assistance.assistance(owner,entry).currentCategoryId()).isEqualTo(food);
        assistance.category(owner,entry,new BankAssistanceService.CategoryChange(version(),other));
        assertThatThrownBy(()->assistance.allowAutomatic(owner,entry,version())).isInstanceOf(ApiException.class);
    }
    @Test void onlyOriginalMatchingLlmDecisionsAreSuppliedAsMemo(){
        automatic.runPending();
        UUID repeated=entry(owner,link,"Pizza em domicílio","POSTED");
        when(advisor.suggest(eq(owner),eq("EXPENSE"),any(),anyList())).thenAnswer(call->{
            List<CategoryAdvisor.Example> prior=call.getArgument(3);
            assertThat(prior).singleElement().satisfies(p->{assertThat(p.description()).isEqualTo("Pizza em domicílio");assertThat(p.categoryId()).isEqualTo(food.toString());});
            return new CategoryAdvisor.Suggestion(food.toString(),"LOW","MEMORY","Decisão anterior",1,false);
        });
        var before=invariants();automatic.runPending();assertThat(invariants()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT category_auto_method FROM bank_entry WHERE id=?",String.class,repeated)).isEqualTo("MEMORY");
        jdbc.update("UPDATE bank_entry SET hidden=true WHERE id=?",entry);
        UUID third=entry(owner,link,"Pizza em domicílio","POSTED");
        doAnswer(call->{assertThat((List<?>)call.getArgument(3)).isEmpty();return new CategoryAdvisor.Suggestion(null,"NONE","NONE","Sem decisão original",0,false);})
            .when(advisor).suggest(eq(owner),eq("EXPENSE"),any(),anyList());
        automatic.runPending();assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,third)).isNull();
    }
}
