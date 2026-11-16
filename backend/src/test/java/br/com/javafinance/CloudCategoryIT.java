package br.com.javafinance;

import br.com.javafinance.accounts.*;
import br.com.javafinance.banking.*;
import br.com.javafinance.categories.*;
import br.com.javafinance.users.*;
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

@SpringBootTest(properties={"app.registration-enabled=true","app.category-ai.automatic-cloud-enabled=true",
    "app.category-ai.cloud-rag=true","app.category-ai.enabled=true","app.category-ai.url=https://example.vercel.app",
    "app.category-ai.job-token=synthetic-cloud-job-token-for-tests-only-0000"})
@AutoConfigureMockMvc
class CloudCategoryIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){TestDatabase.configure(registry);}
    @Autowired JdbcTemplate jdbc; @Autowired UserService users; @Autowired AccountService accounts;
    @Autowired CategoryService categories; @Autowired BankAssistanceService assistance;
    @Autowired AutomaticCategoryService automatic; @Autowired CloudCategoryPolicy policy; @Autowired MockMvc mvc;
    @MockitoSpyBean CloudCategoryJobs jobs; @MockitoBean CategoryAdvisor advisor;
    UUID owner,peer,entry,food,other; LocalDate date=LocalDate.now().minusDays(2);
    @BeforeEach void setup(){
        doReturn(true).when(jobs).dispatch(any());
        owner=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Cloud fictício","UmaSenhaSegura123!")).id();
        peer=users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Outro fictício","UmaSenhaSegura123!")).id();
        var account=accounts.create(owner,new AccountService.Create("Banco fictício","",Account.Kind.CHECKING,"1000",date.minusDays(10))).id();
        food=categories.create(owner,new CategoryService.Create("Delivery",Category.Kind.EXPENSE,false)).id();
        other=categories.create(owner,new CategoryService.Create("Outros gastos",Category.Kind.EXPENSE,false)).id();
        UUID link=UUID.randomUUID();entry=UUID.randomUUID();
        jdbc.update("INSERT INTO bank_link(id,owner_id,item_id,external_account_id,account_id,name,import_from,reported_balance) VALUES (?,?,?,?,?,?,?,?)",link,owner,UUID.randomUUID(),UUID.randomUUID(),account,"Fictício",date.minusDays(9),new java.math.BigDecimal("950"));
        jdbc.update("INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status) VALUES (?,?,?,?,?,?,?,?,?)",entry,owner,link,entry.toString(),"Pizza delivery fictícia",new java.math.BigDecimal("35.25"),date,"DEBIT","POSTED");
        when(advisor.suggest(eq(owner),eq("EXPENSE"),any(),anyList())).thenReturn(new CategoryAdvisor.Suggestion(food.toString(),"LOW","LLM","Fictício",0,true,"groq"));
    }
    long version(){return jdbc.queryForObject("SELECT version FROM bank_entry WHERE id=?",Long.class,entry);}
    Object money(){return List.of(accounts.list(owner).stream().map(a->List.of(a.id(),a.balance())).toList(),
        jdbc.queryForObject("SELECT count(*) FROM movement WHERE owner_id=?",Long.class,owner),
        jdbc.queryForObject("SELECT count(*) FROM posting WHERE owner_id=?",Long.class,owner),
        jdbc.queryForObject("SELECT jsonb_agg(jsonb_build_array(id,amount,direction,date,movement_id,transfer_id) ORDER BY id)::text FROM bank_entry WHERE owner_id=?",String.class,owner));}
    @Test void optInIsPerUserAndDuplicateDeliveriesNeverChangeMoney(){
        var before=money();assertThat(automatic.status(owner).available()).isTrue();assertThat(policy.activeFor(owner)).isFalse();
        jobs.process(owner);assistance.assistance(owner,entry);verifyNoInteractions(advisor);
        jobs.preference(owner,true,0);assertThat(policy.activeFor(owner)).isTrue();assertThat(policy.activeFor(peer)).isFalse();
        assertThat(jobs.process(peer)).isFalse();assertThat(jobs.process(owner)).isFalse();assertThat(jobs.process(owner)).isFalse();
        assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,entry)).isEqualTo(food);
        verify(advisor,times(1)).suggest(eq(owner),eq("EXPENSE"),any(),anyList());assertThat(money()).isEqualTo(before);
    }
    @Test void disablingDuringModelCallWinsAndCanResume(){
        jobs.preference(owner,true,0);
        when(advisor.suggest(eq(owner),eq("EXPENSE"),any(),anyList())).thenAnswer(c->{jobs.preference(owner,false,1);return new CategoryAdvisor.Suggestion(food.toString(),"LOW","LLM","",0,true,"groq");});
        jobs.process(owner);assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,entry)).isNull();
        assertThat(policy.activeFor(owner)).isFalse();
        when(advisor.suggest(eq(owner),eq("EXPENSE"),any(),anyList())).thenReturn(new CategoryAdvisor.Suggestion(food.toString(),"MEDIUM","RULE","",0,false));
        jobs.preference(owner,true,2);jobs.process(owner);
        assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,entry)).isEqualTo(food);
    }
    @Test void manualChoiceWinsAgainstInflightCloudResponse(){
        var before=money();jobs.preference(owner,true,0);
        when(advisor.suggest(eq(owner),eq("EXPENSE"),any(),anyList())).thenAnswer(c->{assistance.category(owner,entry,new BankAssistanceService.CategoryChange(version(),other));return new CategoryAdvisor.Suggestion(food.toString(),"LOW","LLM","",0,true,"groq");});
        jobs.process(owner);assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,entry)).isEqualTo(other);assertThat(money()).isEqualTo(before);
    }
    @Test void failureReleasesLeaseWithoutMarkingAbstentionAndRetrySurvivesNewDelivery(){
        jobs.preference(owner,true,0);when(advisor.suggest(eq(owner),eq("EXPENSE"),any(),anyList())).thenThrow(new CategoryAdvisor.TemporarilyUnavailable());
        assertThatThrownBy(()->jobs.process(owner)).isInstanceOf(CategoryAdvisor.TemporarilyUnavailable.class);
        assertThat(jdbc.queryForObject("SELECT lease_token FROM category_automation_job WHERE owner_id=?",UUID.class,owner)).isNull();
        assertThat(jdbc.queryForObject("SELECT category_auto_attempted_at FROM bank_entry WHERE id=?",java.sql.Timestamp.class,entry)).isNull();
        when(advisor.suggest(eq(owner),eq("EXPENSE"),any(),anyList())).thenReturn(new CategoryAdvisor.Suggestion(food.toString(),"LOW","LLM","",0,true,"groq"));
        jobs.process(owner);assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,entry)).isEqualTo(food);
    }
    @Test void activeLeaseIsRetriedAndExpiredLeaseCanBeRecovered(){
        jobs.preference(owner,true,0);jdbc.update("UPDATE category_automation_job SET lease_token=?,lease_until=now()+interval '1 minute' WHERE owner_id=?",UUID.randomUUID(),owner);
        assertThatThrownBy(()->jobs.process(owner)).isInstanceOf(CategoryAdvisor.TemporarilyUnavailable.class);verifyNoInteractions(advisor);
        jdbc.update("UPDATE category_automation_job SET lease_until=now()-interval '1 second' WHERE owner_id=?",owner);jobs.process(owner);
        assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,entry)).isEqualTo(food);
    }
    @Test void latestHumanCorrectionOutlivesBoundedRagAndOverridesModelMemory(){
        jobs.preference(owner,true,0);jobs.process(owner);
        assistance.category(owner,entry,new BankAssistanceService.CategoryChange(version(),other));
        UUID link=jdbc.queryForObject("SELECT link_id FROM bank_entry WHERE id=?",UUID.class,entry);
        UUID next=UUID.randomUUID();
        jdbc.update("INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status) VALUES (?,?,?,?,?,?,?,?,?)",next,owner,link,next.toString(),"Pizza delivery fictícia",new java.math.BigDecimal("20"),date,"DEBIT","POSTED");
        clearInvocations(advisor);jobs.request(owner);jobs.process(owner);
        assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,next)).isEqualTo(other);
        assertThat(jdbc.queryForObject("SELECT category_auto_method FROM bank_entry WHERE id=?",String.class,next)).isEqualTo("HISTORY");verifyNoInteractions(advisor);
    }
    @Test void correctionOnAnotherRecordDuringModelResponseAlsoWins(){
        UUID link=jdbc.queryForObject("SELECT link_id FROM bank_entry WHERE id=?",UUID.class,entry),source=UUID.randomUUID();
        jdbc.update("INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status) VALUES (?,?,?,?,?,?,?,?,?)",source,owner,link,source.toString(),"Pizza delivery fictícia",new java.math.BigDecimal("20"),date,"DEBIT","POSTED");
        assistance.category(owner,source,new BankAssistanceService.CategoryChange(0,null));
        when(advisor.suggest(eq(owner),eq("EXPENSE"),any(),anyList())).thenAnswer(c->{
            assistance.category(owner,source,new BankAssistanceService.CategoryChange(1,other));
            return new CategoryAdvisor.Suggestion(food.toString(),"LOW","LLM","",0,true,"groq");
        });
        var before=money();jobs.preference(owner,true,0);jobs.process(owner);
        assertThat(jdbc.queryForObject("SELECT category_id FROM bank_entry WHERE id=?",UUID.class,entry)).isEqualTo(other);
        assertThat(jdbc.queryForObject("SELECT category_auto_method FROM bank_entry WHERE id=?",String.class,entry)).isEqualTo("HISTORY");
        assertThat(money()).isEqualTo(before);
    }
    @Test void preferencesRequireSessionCsrfAndCurrentVersionAndJobRequiresSeparateSecret() throws Exception {
        mvc.perform(post("/api/banking/category-automation").contentType("application/json").content("{\"enabled\":true,\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/banking/category-automation").with(user(new UserPrincipal(owner,"",0))).contentType("application/json").content("{\"enabled\":true,\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/banking/category-automation").with(user(new UserPrincipal(owner,"",0))).with(csrf()).contentType("application/json").content("{\"enabled\":true,\"version\":0}")).andExpect(status().isOk());
        assertThatThrownBy(()->jobs.preference(owner,false,0)).isInstanceOf(br.com.javafinance.common.ApiException.class);
        mvc.perform(post("/api/banking/jobs/categories").contentType("application/json").content("{\"owner\":\""+owner+"\"}")).andExpect(status().isUnauthorized());
    }
}
