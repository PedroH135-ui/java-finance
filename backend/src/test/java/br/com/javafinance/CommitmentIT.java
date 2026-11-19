package br.com.javafinance;

import br.com.javafinance.accounts.Account;
import br.com.javafinance.accounts.AccountService;
import br.com.javafinance.banking.BankProperties;
import br.com.javafinance.banking.BankProvider;
import br.com.javafinance.banking.BankService;
import br.com.javafinance.budgets.BudgetService;
import br.com.javafinance.budgets.CommitmentService;
import br.com.javafinance.common.ApiException;
import br.com.javafinance.users.UserPrincipal;
import br.com.javafinance.users.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.registration-enabled=true", "app.category-ai.enabled=false"})
@AutoConfigureMockMvc
class CommitmentIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        TestDatabase.configure(registry);
    }

    @Autowired CommitmentService commitments;
    @Autowired BudgetService budgets;
    @Autowired UserService users;
    @Autowired AccountService accounts;
    @Autowired BankService bank;
    @Autowired BankProperties config;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean BankProvider provider;

    private UUID createUser() {
        return users.register(new UserService.Registration(UUID.randomUUID() + "@example.test",
            "Pessoa fictícia", "UmaSenhaSegura123!")).id();
    }

    private UUID bind(UUID owner, UUID external, LocalDate today) {
        var item = UUID.randomUUID();
        config.setOwnerId(owner);
        config.setItemIds(java.util.Set.of(item));
        config.setClientId("synthetic");
        config.setClientSecret("synthetic");
        when(provider.accounts(item)).thenReturn(new BankProvider.Snapshot(clock.instant(),
            List.of(new BankProvider.RemoteAccount(external, "Conta fictícia", "BANK", "BRL", new BigDecimal("1000.00")))));
        when(provider.transactions(eq(external), any(), any())).thenReturn(List.of());
        var account = accounts.create(owner, new AccountService.Create("Conta fictícia", "", Account.Kind.CHECKING,
            "1000", today.minusDays(5))).id();
        bank.bind(owner, new BankService.Bind(item, external, account, today.minusDays(3), true));
        return account;
    }

    private void sync(UUID owner, UUID external, LocalDate today, BankProvider.RemoteEntry... entries) {
        var link = bank.links(owner).getFirst().id();
        jdbc.update("UPDATE bank_link SET last_attempt_at=null WHERE owner_id=? AND id=?", owner, link);
        when(provider.transactions(eq(external), any(), any())).thenReturn(List.of(entries));
        bank.sync(owner, link, new BankService.Sync(today.minusDays(3), today));
    }

    @Test void matchesTheSyncedStatementExactlyOnceAndPreservesBudgetAndBalances() throws Exception {
        var owner = createUser();
        var external = UUID.randomUUID();
        var today = LocalDate.now(clock);
        var account = bind(owner, external, today);
        var movementCount = jdbc.queryForObject("SELECT count(*) FROM movement WHERE owner_id=?", Long.class, owner);
        var postingCount = jdbc.queryForObject("SELECT count(*) FROM posting WHERE owner_id=?", Long.class, owner);
        var input = new CommitmentService.Create(account, "Assinatura fictícia", " streaming   exemplo ", today, "39.90");
        var principal = user(new UserPrincipal(owner, "", 0));
        mvc.perform(post("/api/budgets/commitments").with(principal).with(csrf())
            .contentType("application/json").content(mapper.writeValueAsString(input))).andExpect(status().isNoContent());
        assertThat(commitments.upcoming(owner).pendingTotal()).isEqualByComparingTo("39.90");
        assertThatThrownBy(() -> commitments.create(owner, input)).isInstanceOf(ApiException.class);
        var amount = new BigDecimal("39.90");
        sync(owner, external, today, new BankProvider.RemoteEntry("fixture", "STREAMING EXEMPLO", amount, today, "DEBIT", "PENDING"));
        assertThat(commitments.upcoming(owner).pendingTotal()).isEqualByComparingTo(amount);
        var posted = new BankProvider.RemoteEntry("fixture", "STREAMING EXEMPLO", amount, today, "DEBIT", "POSTED");
        sync(owner, external, today, posted);
        sync(owner, external, today, posted);

        var result = commitments.upcoming(owner);
        var row = result.items().getFirst();
        assertThat(result.through()).isEqualTo(today.plusDays(29));
        assertThat(result.pendingTotal()).isZero();
        assertThat(row.status()).isEqualTo(CommitmentService.Status.IDENTIFIED);
        assertThat(row.entryId()).isNotNull();
        mvc.perform(get("/api/banking/entries/{id}/record", row.entryId()).with(principal)).andExpect(status().isOk());
        var budget = budgets.month(owner, YearMonth.from(today)).totals();
        assertThat(budget.unbudgetedSpent()).isEqualByComparingTo(amount);
        assertThat(budget.unbudgetedPlanned()).isZero();
        assertThat(bank.links(owner).getFirst().reportedBalance()).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movement WHERE owner_id=?", Long.class, owner)).isEqualTo(movementCount);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posting WHERE owner_id=?", Long.class, owner)).isEqualTo(postingCount);

        var other = user(new UserPrincipal(createUser(), "", 0));
        mvc.perform(get("/api/budgets/commitments").with(other)).andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(delete("/api/budgets/commitments/{id}", row.id()).with(other).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/budgets/commitments/{id}", row.id()).with(principal).with(csrf())).andExpect(status().isNoContent());
        assertThat(budgets.month(owner, YearMonth.from(today)).totals()).isEqualTo(budget);
    }

    @Test void overlappingPredictionsAndRepeatedChargesStayInReviewAndHiddenEntriesNeverMatch() {
        var owner = createUser();
        var external = UUID.randomUUID();
        var today = LocalDate.now(clock);
        var account = bind(owner, external, today);
        var amount = new BigDecimal("50.00");
        for (int days : List.of(0, 2, 29)) {
            commitments.create(owner, new CommitmentService.Create(account, "Conta " + days, "ASSINATURA", today.plusDays(days), "50"));
        }
        assertThatThrownBy(() -> commitments.create(owner,
            new CommitmentService.Create(account, "Fora da janela", "ASSINATURA", today.plusDays(30), "50")))
            .isInstanceOf(ApiException.class);
        var entry = new BankProvider.RemoteEntry("shared", "ASSINATURA", amount, today, "DEBIT", "POSTED");
        sync(owner, external, today, entry);
        var data = commitments.upcoming(owner);
        assertThat(data.items()).extracting(CommitmentService.Row::status).containsExactly(
            CommitmentService.Status.REVIEW, CommitmentService.Status.REVIEW, CommitmentService.Status.EXPECTED);
        assertThat(data.items()).allSatisfy(row -> assertThat(row.entryId()).isNull());
        assertThat(data.pendingTotal()).isEqualByComparingTo("50.00");
        assertThat(data.reviewTotal()).isEqualByComparingTo("100.00");
        commitments.remove(owner, data.items().get(1).id());
        var second = new BankProvider.RemoteEntry("distinct", "ASSINATURA", amount, today.minusDays(1), "DEBIT", "POSTED");
        sync(owner, external, today, entry, second);
        assertThat(commitments.upcoming(owner).items().getFirst().status()).isEqualTo(CommitmentService.Status.REVIEW);
        jdbc.update("UPDATE bank_entry SET hidden=true WHERE owner_id=?", owner);
        assertThat(commitments.upcoming(owner).items()).allSatisfy(row ->
            assertThat(row.status()).isEqualTo(CommitmentService.Status.EXPECTED));
    }
}
