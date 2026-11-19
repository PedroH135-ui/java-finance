package br.com.javafinance;

import br.com.javafinance.categories.*;
import br.com.javafinance.accounts.*;
import br.com.javafinance.users.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.registration-enabled=true")
@AutoConfigureMockMvc
class DefaultCategoriesIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){TestDatabase.configure(r);}
    @Autowired UserService users; @Autowired CategoryService categories; @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accounts; @Autowired MockMvc mvc;
    UUID register(){return users.register(new UserService.Registration(UUID.randomUUID()+"@example.test","Categorias fictícias","UmaSenhaSegura123!")).id();}
    UUID legacy(){UUID owner=register();jdbc.update("DELETE FROM category WHERE owner_id=?",owner);return owner;}

    @Test void registrationSeedsPrivateCategoriesAndEnabledPreferenceWithoutMoneyOrJobs(){
        UUID owner=register(),other=register();
        var list=categories.list(owner);
        assertThat(list).hasSize(16);
        assertThat(list.stream().filter(c->c.kind()==Category.Kind.INCOME)).hasSize(5);
        assertThat(list).allMatch(c->!c.essential()&&!c.archived());
        assertThat(list.stream().map(CategoryService.View::id)).doesNotContainAnyElementsOf(categories.list(other).stream().map(CategoryService.View::id).toList());
        assertThat(categories.addDefaults(owner).added()).isZero();
        assertThat(jdbc.queryForObject("SELECT enabled FROM category_automation_preference WHERE owner_id=?",Boolean.class,owner)).isTrue();
        for(String table:List.of("account","movement","posting","category_automation_job"))
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE owner_id=?",Long.class,owner)).isZero();
    }
    @Test void existingNamesAndArchivedCategoriesArePreservedAndMoneyIsUnchanged(){
        UUID owner=legacy();
        var salary=categories.create(owner,new CategoryService.Create("  SALARIO  ",Category.Kind.INCOME,false));
        var market=categories.create(owner,new CategoryService.Create("mercado",Category.Kind.EXPENSE,true));
        categories.edit(owner,market.id(),new CategoryService.Edit(market.name(),true,true,market.version()));
        var delivery=categories.create(owner,new CategoryService.Create("Restaurantes   e DELIVERY",Category.Kind.EXPENSE,false));
        var custom=categories.create(owner,new CategoryService.Create("Minha categoria",Category.Kind.EXPENSE,true));
        var before=categories.list(owner);
        accounts.create(owner,new AccountService.Create("Conta fictícia","",Account.Kind.CHECKING,"123.45",LocalDate.now()));
        var money=accounts.list(owner);
        var tables=List.of("movement","posting","category_automation_preference","category_automation_job");
        var counts=tables.stream().map(table->jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE owner_id=?",Long.class,owner)).toList();
        assertThat(categories.addDefaults(owner)).isEqualTo(new CategoryService.Defaults(13,3));
        assertThat(categories.list(owner)).containsAll(before).hasSize(17);
        assertThat(categories.addDefaults(owner).added()).isZero();
        assertThat(accounts.list(owner)).isEqualTo(money);
        assertThat(tables.stream().map(table->jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE owner_id=?",Long.class,owner)).toList()).isEqualTo(counts);
    }
    @Test void repeatedConcurrentRequestsCreateOneSet() throws Exception {
        UUID owner=legacy();
        try(var pool=Executors.newFixedThreadPool(2)){
            var gate=new CountDownLatch(1);
            Callable<Integer> task=()->{gate.await();return categories.addDefaults(owner).added();};
            var first=pool.submit(task);var second=pool.submit(task);gate.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS)+second.get(20,TimeUnit.SECONDS)).isEqualTo(16);
        }
        assertThat(categories.list(owner)).hasSize(16);
    }
    @Test void endpointRequiresSessionAndCsrfAndUsesOnlyCurrentOwner() throws Exception {
        UUID owner=legacy(),other=legacy();var principal=new UserPrincipal(owner,"",0);
        mvc.perform(post("/api/categories/defaults")).andExpect(status().isForbidden());
        mvc.perform(post("/api/categories/defaults").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/categories/defaults").with(user(principal))).andExpect(status().isForbidden());
        mvc.perform(post("/api/categories/defaults").with(user(principal)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.added").value(16));
        mvc.perform(post("/api/categories/defaults").with(user(principal)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.added").value(0));
        assertThat(categories.list(other)).isEmpty();
    }
}
