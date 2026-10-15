package br.com.javafinance;

import br.com.javafinance.accounts.*;
import br.com.javafinance.budgets.*;
import br.com.javafinance.categories.*;
import br.com.javafinance.common.ApiException;
import br.com.javafinance.movements.*;
import br.com.javafinance.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
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
class BudgetIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        TestDatabase.configure(r);
    }
    @Autowired BudgetService budgets; @Autowired UserService users; @Autowired CategoryService categories;
    @Autowired AccountService accounts; @Autowired MovementService movements; @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
    UUID owner; UserPrincipal principal; YearMonth month = YearMonth.of(2026, 10);
    @BeforeEach void setup() {
        owner = users.register(new UserService.Registration(UUID.randomUUID()+"@example.test", "Orçamento", "UmaSenhaSegura123!")).id();
        principal = new UserPrincipal(owner, "", 0);
    }
    CategoryService.View category(String name) { return categories.create(owner, new CategoryService.Create(name, Category.Kind.EXPENSE, false)); }
    BudgetService.Row row(UUID category) { return budgets.month(owner, month).categories().stream().filter(r -> r.categoryId().equals(category)).findFirst().orElseThrow(); }
    MovementDtos.View expense(AccountService.View account, UUID category, String amount, Movement.Status status, YearMonth competence) {
        LocalDate date = LocalDate.of(2026, 10, 12);
        return movements.create(owner, new MovementDtos.Create(UUID.randomUUID(), "Despesa", amount, Movement.Kind.EXPENSE, status,
            account.id(), null, category, date, competence.atDay(1), date, status == Movement.Status.CONFIRMED ? date : null, ""));
    }
    @Test void budgetUsesCompetenceAndSeparatesPlannedCancelledAndUnbudgeted() {
        var cat = category("Alimentação"); var extra = category("Sem limite");
        var account = accounts.create(owner, new AccountService.Create("Conta", "", Account.Kind.CHECKING, "1000", LocalDate.of(2026, 1, 1)));
        budgets.save(owner, month, cat.id(), new BudgetService.Save("100.00", null));
        expense(account, cat.id(), "40.25", Movement.Status.CONFIRMED, month);
        var planned = expense(account, cat.id(), "20.10", Movement.Status.PLANNED, month);
        var cancelled = expense(account, cat.id(), "300", Movement.Status.CONFIRMED, month);
        movements.cancel(owner, cancelled.id(), new MovementDtos.Change(cancelled.version(), "Cancelado", null));
        expense(account, cat.id(), "900", Movement.Status.CONFIRMED, month.minusMonths(1));
        expense(account, extra.id(), "5.05", Movement.Status.CONFIRMED, month);
        var row = row(cat.id());
        assertThat(row.spent()).isEqualByComparingTo("40.25"); assertThat(row.planned()).isEqualByComparingTo("20.10");
        assertThat(row.remaining()).isEqualByComparingTo("59.75"); assertThat(row.percent()).isEqualByComparingTo("40.25");
        assertThat(budgets.month(owner, month).totals().unbudgetedSpent()).isEqualByComparingTo("5.05");
        movements.confirm(owner, planned.id(), new MovementDtos.Change(planned.version(), "Pago", LocalDate.of(2026,10,12)));
        assertThat(row(cat.id()).spent()).isEqualByComparingTo("60.35"); assertThat(row(cat.id()).planned()).isZero();
        assertThat(row(extra.id()).limit()).isNull();
    }
    @Test void zeroIsDifferentFromMissingAndBudgetNeverChangesBalance() {
        var cat = category("Zero"); var untouched = category("Livre");
        var account = accounts.create(owner, new AccountService.Create("Conta", "", Account.Kind.CASH, "123.45", LocalDate.of(2026,1,1)));
        budgets.save(owner, month, cat.id(), new BudgetService.Save("0", null));
        assertThat(row(cat.id()).limit()).isZero(); assertThat(row(cat.id()).percent()).isNull();
        assertThat(row(untouched.id()).limit()).isNull();
        assertThat(accounts.list(owner).getFirst().balance()).isEqualByComparingTo("123.45");
        expense(account, cat.id(), "1.01", Movement.Status.CONFIRMED, month);
        assertThat(row(cat.id()).remaining()).isEqualByComparingTo("-1.01");
    }
    @Test void copyIsIdempotentPreservesExistingLimitsAndSkipsArchived() {
        var first = category("Primeira"); var second = category("Segunda"); var archived = category("Arquivada");
        for (var c : List.of(first, second, archived)) budgets.save(owner, month.minusMonths(1), c.id(), new BudgetService.Save("80", null));
        categories.edit(owner, archived.id(), new CategoryService.Edit(archived.name(), false, true, archived.version()));
        budgets.save(owner, month, first.id(), new BudgetService.Save("15", null));
        var result = budgets.copy(owner, month, new BudgetService.Copy(month.minusMonths(1)));
        assertThat(result.copied()).isEqualTo(1); assertThat(result.skipped()).isEqualTo(2);
        assertThat(row(first.id()).limit()).isEqualByComparingTo("15"); assertThat(row(second.id()).limit()).isEqualByComparingTo("80");
        assertThat(budgets.copy(owner, month, new BudgetService.Copy(month.minusMonths(1))).copied()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE owner_id=? AND action='BUDGET_COPIED'", Long.class, owner)).isEqualTo(1);
    }
    @Test void staleWritesAndSimultaneousCreatesCannotOverwrite() throws Exception {
        var cat = category("Concorrência");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var tasks = List.<Callable<Boolean>>of(() -> create(cat.id(), "10"), () -> create(cat.id(), "20"));
            var results = pool.invokeAll(tasks);
            assertThat(results.get(0).get() ^ results.get(1).get()).isTrue();
        } finally { pool.shutdownNow(); }
        budgets.save(owner, month, cat.id(), new BudgetService.Save("30", 0L));
        assertThatThrownBy(() -> budgets.save(owner, month, cat.id(), new BudgetService.Save("40", 0L))).isInstanceOf(ApiException.class);
        assertThat(row(cat.id()).limit()).isEqualByComparingTo("30"); assertThat(row(cat.id()).version()).isEqualTo(1);
    }
    boolean create(UUID category, String amount) {
        try { budgets.save(owner, month, category, new BudgetService.Save(amount, null)); return true; }
        catch (ApiException ex) { return false; }
    }
    @Test void apiEnforcesOwnershipAuthenticationCsrfAndMonthFormat() throws Exception {
        var cat = category("Privada"); budgets.save(owner, month, cat.id(), new BudgetService.Save("25", null));
        var other = users.register(new UserService.Registration(UUID.randomUUID()+"@example.test", "Outro", "UmaSenhaSegura123!")).id();
        var stranger = new UserPrincipal(other, "", 0);
        mvc.perform(get("/api/budgets").param("month", "2026-10")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/budgets").param("month", "2026-10").with(user(principal))).andExpect(status().isOk()).andExpect(jsonPath("$.categories[0].limit").value("25.00"));
        mvc.perform(get("/api/budgets").param("month", "2026-10").with(user(stranger))).andExpect(jsonPath("$.categories").isEmpty());
        var path = "/api/budgets/2026-10/categories/"+cat.id();
        var body = mapper.writeValueAsString(new BudgetService.Save("99", 0L));
        mvc.perform(put(path).with(user(principal)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user(stranger)).with(csrf()).contentType("application/json").content(body)).andExpect(status().isNotFound());
        mvc.perform(put(path).with(user(principal)).with(csrf()).contentType("application/json").content(body)).andExpect(status().isNoContent());
        mvc.perform(put(path).with(user(principal)).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/budgets/2026-11/copy").with(user(principal)).with(csrf()).contentType("application/json").content("{\"sourceMonth\":\"2026-10\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.copied").value(1));
        for (String invalid : List.of("2026-13", "2026-10-01", "1899-12", "2201-01"))
            mvc.perform(get("/api/budgets").param("month", invalid).with(user(principal))).andExpect(status().isBadRequest());
    }
    @Test void rejectsInvalidValuesAndIncomeAndRetainsArchivedHistory() {
        var cat = category("Histórico");
        for (String invalid : List.of("-1", "1.001", "1,20", "1000000000000000", ""))
            assertThatThrownBy(() -> budgets.save(owner, month, cat.id(), new BudgetService.Save(invalid, null))).isInstanceOf(ApiException.class);
        var income = categories.create(owner, new CategoryService.Create("Receita", Category.Kind.INCOME, false));
        assertThatThrownBy(() -> budgets.save(owner, month, income.id(), new BudgetService.Save("1", null))).isInstanceOf(ApiException.class);
        budgets.save(owner, month, cat.id(), new BudgetService.Save("35", null));
        categories.edit(owner, cat.id(), new CategoryService.Edit(cat.name(), false, true, cat.version()));
        assertThat(row(cat.id()).archived()).isTrue(); assertThat(row(cat.id()).limit()).isEqualByComparingTo("35");
        assertThatThrownBy(() -> budgets.save(owner, month.plusMonths(1), cat.id(), new BudgetService.Save("10", null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> budgets.copy(owner, month, new BudgetService.Copy(month))).isInstanceOf(ApiException.class);
    }
    @Test void migrationUpgradesV4WithoutLosingExistingRecords() {
        String schema = "budget_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var config = org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema);
        config.target("4").load().migrate();
        UUID existing = UUID.randomUUID(), category = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".app_user(id,email,display_name,password_hash,created_at) VALUES (?,?,?,?,now())",
            existing, "migration@example.test", "Preservado", "synthetic-test-hash");
        jdbc.update("INSERT INTO " + schema + ".category(id,owner_id,name,kind) VALUES (?,?,?,'EXPENSE')", category, existing, "Preservada");
        org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema).load().migrate();
        assertThat(jdbc.queryForObject("SELECT display_name FROM " + schema + ".app_user WHERE id=?", String.class, existing)).isEqualTo("Preservado");
        assertThat(jdbc.queryForObject("SELECT name FROM " + schema + ".category WHERE id=?", String.class, category)).isEqualTo("Preservada");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".monthly_budget", Long.class)).isZero();
    }
}
