package br.com.javafinance.banking;

import br.com.javafinance.common.*;
import br.com.javafinance.movements.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.dao.DataAccessException;
import org.slf4j.LoggerFactory;

@Service
public class BankService {
    public record Config(boolean ready, UUID userId, List<UUID> items, String provider, boolean automaticRead) {}
    public record Bind(@NotNull UUID itemId, @NotNull UUID externalAccountId, @NotNull UUID accountId, @NotNull LocalDate importFrom, boolean confirmedBaseline) {}
    public record Sync(@NotNull LocalDate from, @NotNull LocalDate to) {}
    public record Review(long version, UUID categoryId, UUID movementId, boolean confirmedNature, boolean reviewedDuplicates) {}
    public record Link(UUID id, UUID itemId, UUID externalAccountId, UUID accountId, String name, LocalDate importFrom,
                       boolean active, Instant lastCheckedAt, Instant providerUpdatedAt, BigDecimal reportedBalance,
                       LocalDate autoCheckedThrough, Instant autoErrorAt) {}
    public record Candidate(UUID id, String description, BigDecimal amount, LocalDate date, String kind) {}
    public record Entry(UUID id, UUID linkId, String accountName, String description, BigDecimal amount, LocalDate date,
                        String direction, String providerStatus, String state, UUID movementId, long version, List<Candidate> candidates) {}
    private final BankProperties config; private final BankProvider provider; private final JdbcTemplate jdbc;
    private final MovementService movements; private final AuditService audit; private final Clock clock; private final TransactionTemplate tx;
    public BankService(BankProperties config, BankProvider provider, JdbcTemplate jdbc, MovementService movements, AuditService audit, Clock clock, PlatformTransactionManager manager) {
        this.config=config; this.provider=provider; this.jdbc=jdbc; this.movements=movements; this.audit=audit; this.clock=clock; tx=new TransactionTemplate(manager);
    }
    public Config config(UUID owner) {
        boolean allowed=owner.equals(config.getOwnerId());
        return new Config(allowed && config.ready(),owner,allowed ? config.getItemIds().stream().sorted().toList() : List.of(),"Meu Pluggy · uso pessoal · somente leitura",allowed && config.automationReady());
    }
    private void owner(UUID owner) {
        if (!owner.equals(config.getOwnerId())) throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,"Conexão pessoal não habilitada para este perfil.");
    }
    private void remote(UUID owner, UUID item) {
        owner(owner);
        if (!config.ready()) throw ApiException.bad("Configure as credenciais pessoais no servidor.");
        if (!config.getItemIds().contains(item)) throw ApiException.missing();
    }
    public BankProvider.Snapshot discover(UUID owner, UUID item) { remote(owner,item); return provider.accounts(item); }
    public List<Link> links(UUID owner) {
        owner(owner);
        return jdbc.query("SELECT * FROM bank_link WHERE owner_id=? ORDER BY name,id",(rs,n)->new Link(rs.getObject("id",UUID.class),rs.getObject("item_id",UUID.class),
            rs.getObject("external_account_id",UUID.class),rs.getObject("account_id",UUID.class),rs.getString("name"),rs.getObject("import_from",LocalDate.class),rs.getBoolean("active"),
            instant(rs.getTimestamp("last_checked_at")),instant(rs.getTimestamp("provider_updated_at")),rs.getBigDecimal("reported_balance"),
            rs.getObject("auto_checked_through",LocalDate.class),instant(rs.getTimestamp("auto_error_at"))),owner);
    }
    public void bind(UUID owner, Bind input) {
        if(!input.confirmedBaseline()) throw ApiException.bad("Confirme a data inicial e revise os registros existentes antes de vincular a conta.");
        var snapshot=discover(owner,input.itemId());
        var external=snapshot.accounts().stream().filter(a->a.id().equals(input.externalAccountId()) && a.type().equals("BANK") && a.currency().equals("BRL")).findFirst().orElseThrow(ApiException::missing);
        tx.executeWithoutResult(status->{
            // Lock order: personal owner -> link -> account. Reconnection never resets review history.
            jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",UUID.class,owner);
            var account=jdbc.queryForList("SELECT * FROM account WHERE owner_id=? AND id=? FOR UPDATE",owner,input.accountId());
            if(account.isEmpty()) throw ApiException.missing();
            var local=account.getFirst();
            if(Boolean.TRUE.equals(local.get("archived")) || !"BRL".equals(local.get("currency")) || !Set.of("CHECKING","SAVINGS","WALLET","OTHER").contains(local.get("kind")))
                throw ApiException.bad("Escolha uma conta bancária ativa em BRL.");
            LocalDate opening=((java.sql.Date)local.get("opening_date")).toLocalDate();
            if(!input.importFrom().isAfter(opening) || input.importFrom().isAfter(LocalDate.now(clock).plusDays(1)))
                throw ApiException.bad("A data inicial deve ser posterior ao saldo inicial e no máximo amanhã. Confira a data e o saldo de abertura da conta.");
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO bank_link(id,owner_id,item_id,external_account_id,account_id,name,import_from,provider_updated_at,reported_balance) VALUES (?,?,?,?,?,?,?,?,?)",
                id,owner,input.itemId(),external.id(),input.accountId(),external.name(),input.importFrom(),timestamp(snapshot.updatedAt()),external.balance());
            audit.record(owner,id,"BANK_LINKED","Conta vinculada para leitura; saldo principal informado pelo banco. Nenhum registro classificado automaticamente.");
        });
    }
    public int sync(UUID owner, UUID id, Sync input) {
        long started = System.nanoTime();
        boolean completed = false;
        String reference = UUID.randomUUID().toString();
        try {
            int read = read(owner, id, input);
            completed = true;
            return read;
        } catch (BankReadException ex) {
            reference = ex.reference;
            throw ex;
        } catch (DataAccessException ex) {
            // Database exception messages may contain SQL, identifiers and financial values.
            LoggerFactory.getLogger(BankService.class).warn("bank_read_failed stage=STORAGE code=STORAGE_ERROR reference={}", reference);
            throw ex;
        } finally {
            LoggerFactory.getLogger(BankService.class).info("bank_read_finished reference={} completed={} duration_ms={}",
                reference, completed, (System.nanoTime() - started) / 1_000_000);
        }
    }
    private int read(UUID owner, UUID id, Sync input) {
        Link link=links(owner).stream().filter(l->l.id().equals(id)).findFirst().orElseThrow(ApiException::missing);
        remote(owner,link.itemId());
        if(!link.active()) throw ApiException.bad("A conexão está desconectada neste sistema.");
        if(input.from()==null || input.to()==null || input.from().isBefore(link.importFrom()) || input.to().isBefore(input.from()) || input.to().isAfter(LocalDate.now(clock)) || input.to().isAfter(input.from().plusDays(92)))
            throw ApiException.bad("Consulte até 93 dias, desde a data inicial do vínculo e sem datas futuras.");
        int reserved=jdbc.update("UPDATE bank_link SET last_attempt_at=? WHERE id=? AND owner_id=? AND active=true AND (last_attempt_at IS NULL OR last_attempt_at<=?)",
            timestamp(clock.instant()),id,owner,timestamp(clock.instant().minusSeconds(60)));
        if(reserved==0) throw ApiException.bad("Aguarde um minuto antes de consultar novamente. O Meu Pluggy atualiza o banco diariamente.");
        var snapshot=provider.accounts(link.itemId());
        var external=snapshot.accounts().stream().filter(a->a.id().equals(link.externalAccountId())).findFirst().orElseThrow(ApiException::missing);
        List<BankProvider.RemoteEntry> entries=provider.transactions(link.externalAccountId(),input.from(),input.to());
        if(entries.size()>2000) throw ApiException.bad("Reduza o período da consulta.");
        return tx.execute(status->{
            lockLink(owner,id);
            for(var entry:entries) {
                if(entry.date().isBefore(input.from()) || entry.date().isAfter(input.to())) throw ApiException.bad("O provedor retornou dados fora do período solicitado.");
                int inserted=jdbc.update("INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(link_id,external_id) DO NOTHING",
                    UUID.randomUUID(),owner,id,entry.id(),entry.description(),entry.amount(),entry.date(),entry.direction(),entry.status());
                if(inserted==0) jdbc.update("""
                    UPDATE bank_entry SET description=?,amount=?,date=?,direction=?,provider_status=?,
                      state=CASE WHEN state='REVIEW' THEN 'REVIEW' ELSE 'CHANGED' END,version=version+1
                    WHERE owner_id=? AND link_id=? AND external_id=?
                      AND (description,amount,date,direction,provider_status) IS DISTINCT FROM (?,?,?,?,?)
                    """,entry.description(),entry.amount(),entry.date(),entry.direction(),entry.status(),owner,id,entry.id(),entry.description(),entry.amount(),entry.date(),entry.direction(),entry.status());
            }
            jdbc.update("UPDATE bank_link SET last_checked_at=?,provider_updated_at=?,reported_balance=? WHERE id=? AND owner_id=?",timestamp(clock.instant()),timestamp(snapshot.updatedAt()),external.balance(),id,owner);
            audit.record(owner,id,"BANK_READ","Dados consultados para revisão, sem confirmação automática."); return entries.size();
        });
    }
    public List<Entry> entries(UUID owner, UUID linkId, int page) {
        return entries(owner,linkId,page,false);
    }
    public List<Entry> entries(UUID owner, UUID linkId, int page, boolean pending) {
        owner(owner);
        if(page<0 || page>10000) throw ApiException.bad("Página inválida.");
        return jdbc.query("""
            SELECT e.*,a.name account_name,l.account_id FROM bank_entry e
            JOIN bank_link l ON l.id=e.link_id AND l.owner_id=e.owner_id
            JOIN account a ON a.id=l.account_id AND a.owner_id=e.owner_id
            WHERE e.owner_id=? AND e.link_id=? AND (NOT ? OR e.state IN ('REVIEW','CHANGED'))
            ORDER BY e.date DESC,e.id LIMIT 50 OFFSET ?
            """,(rs,n)->{
                var amount=rs.getBigDecimal("amount"); var date=rs.getObject("date",LocalDate.class); var direction=rs.getString("direction");
                var state=rs.getString("state");
                return new Entry(rs.getObject("id",UUID.class),linkId,rs.getString("account_name"),rs.getString("description"),amount,date,direction,rs.getString("provider_status"),state,
                    rs.getObject("movement_id",UUID.class),rs.getLong("version"),state.equals("REVIEW")?candidates(owner,rs.getObject("account_id",UUID.class),amount,date,direction):List.of());
            },owner,linkId,pending,page*50);
    }
    private List<Candidate> candidates(UUID owner, UUID account, BigDecimal amount, LocalDate date, String direction) {
        return jdbc.query("""
            SELECT id,description,amount,settled_date,kind FROM movement
            WHERE owner_id=? AND status='CONFIRMED' AND amount=? AND settled_date BETWEEN ? AND ?
              AND ((kind=? AND account_id=?) OR (kind='TRANSFER' AND ((?='DEBIT' AND account_id=?) OR (?='CREDIT' AND destination_id=?))))
            ORDER BY settled_date,id LIMIT 20
            """,(rs,n)->new Candidate(rs.getObject("id",UUID.class),rs.getString("description"),rs.getBigDecimal("amount"),rs.getObject("settled_date",LocalDate.class),rs.getString("kind")),
            owner,amount,date.minusDays(3),date.plusDays(3),direction.equals("DEBIT")?"EXPENSE":"INCOME",account,direction,account,direction,account);
    }
    @Transactional public void review(UUID owner, UUID id, String action, Review input) {
        owner(owner);
        var ids=jdbc.queryForList("SELECT link_id FROM bank_entry WHERE id=? AND owner_id=?",id,owner);
        if(ids.isEmpty()) throw ApiException.missing();
        var link=lockLink(owner,(UUID)ids.getFirst().get("link_id"));
        var entry=jdbc.queryForMap("SELECT * FROM bank_entry WHERE id=? AND owner_id=? FOR UPDATE",id,owner);
        if(((Number)entry.get("version")).longValue()!=input.version()) throw ApiException.conflict("O lançamento mudou. Atualize a lista antes de revisar.");
        String state=(String)entry.get("state");
        if(!Set.of("REVIEW","CHANGED").contains(state)) throw ApiException.conflict("Este lançamento já foi revisado.");
        if(action.equals("ignore")) {
            jdbc.update("UPDATE bank_entry SET state='IGNORED',version=version+1 WHERE id=? AND owner_id=?",id,owner);
            audit.record(owner,id,"BANK_IGNORED","Ignorado na revisão, sem movimentação financeira."); return;
        }
        if(entry.get("movement_id")!=null || !"POSTED".equals(entry.get("provider_status")))
            throw ApiException.bad("Revise alterações no provedor ou aguarde a efetivação. Não será criado outro lançamento.");
        UUID account=(UUID)link.get("account_id"); BigDecimal amount=(BigDecimal)entry.get("amount");
        LocalDate date=((java.sql.Date)entry.get("date")).toLocalDate(); String direction=(String)entry.get("direction");
        List<Candidate> matches=candidates(owner,account,amount,date,direction);
        UUID movement;
        if(action.equals("match")) {
            if(input.movementId()!=null) {
                var locked=jdbc.queryForList("SELECT id FROM movement WHERE id=? AND owner_id=? FOR UPDATE",input.movementId(),owner);
                if(locked.isEmpty()) throw ApiException.missing();
                matches=candidates(owner,account,amount,date,direction);
            }
            if(input.movementId()==null || matches.stream().noneMatch(c->c.id().equals(input.movementId()))) throw ApiException.bad("Escolha um lançamento efetivado compatível, da mesma conta e valor (até três dias de diferença).");
            var existing=movements.get(owner,input.movementId());
            // A matching action never changes the original movement or its ledger.
            movement=existing.id();
        } else if(action.equals("import")) {
            if(!input.confirmedNature()) throw ApiException.bad("Confirme que é uma receita/despesa real, não transferência própria, investimento, empréstimo ou pagamento de fatura.");
            if(!matches.isEmpty() && !input.reviewedDuplicates()) throw ApiException.conflict("Há possíveis duplicidades. Vincule o lançamento existente ou confirme que são operações distintas.");
            if(input.categoryId()==null) throw ApiException.bad("Selecione a categoria.");
            UUID request=UUID.nameUUIDFromBytes(("MEU_PLUGGY:"+owner+":"+id).getBytes(StandardCharsets.UTF_8));
            var created=movements.recordBankReview(owner,new MovementDtos.Create(request,(String)entry.get("description"),amount.toPlainString(),
                direction.equals("DEBIT")?Movement.Kind.EXPENSE:Movement.Kind.INCOME,Movement.Status.CONFIRMED,account,null,input.categoryId(),date,date.withDayOfMonth(1),date,date,"Classificado após revisão do Meu Pluggy; sem alterar saldo bancário."),(String)entry.get("external_id"));
            movement=created.id();
        } else throw ApiException.bad("Ação inválida.");
        jdbc.update("UPDATE bank_entry SET state=?,movement_id=?,version=version+1 WHERE id=? AND owner_id=?",action.equals("match")?"MATCHED":"IMPORTED",movement,id,owner);
        audit.record(owner,id,"BANK_REVIEWED",action.equals("match")?"Vinculado a lançamento existente, sem duplicar saldo.":"Classificação confirmada para relatórios, sem alterar saldo bancário.");
    }
    @Transactional public void disconnect(UUID owner, UUID id) {
        owner(owner);
        var links=jdbc.queryForList("SELECT id FROM bank_link WHERE id=? AND owner_id=? FOR UPDATE",id,owner);
        if(links.isEmpty()) throw ApiException.missing();
        jdbc.update("UPDATE bank_link SET active=false WHERE id=? AND owner_id=?",id,owner);
        audit.record(owner,id,"BANK_DISCONNECTED","Consultas desativadas neste sistema. Revogue também a autorização no Meu Pluggy/banco.");
    }
    public void reconnect(UUID owner, UUID id) {
        Link link=links(owner).stream().filter(l->l.id().equals(id)).findFirst().orElseThrow(ApiException::missing);
        var snapshot=discover(owner,link.itemId());
        if(snapshot.accounts().stream().noneMatch(a->a.id().equals(link.externalAccountId()))) throw ApiException.missing();
        tx.executeWithoutResult(status->{
            jdbc.update("UPDATE bank_link SET active=true WHERE id=? AND owner_id=?",id,owner);
            audit.record(owner,id,"BANK_RECONNECTED","Leitura reativada. Histórico e data inicial preservados.");
        });
    }
    private Map<String,Object> lockLink(UUID owner, UUID id) {
        var links=jdbc.queryForList("SELECT * FROM bank_link WHERE id=? AND owner_id=? FOR UPDATE",id,owner);
        if(links.isEmpty()) throw ApiException.missing();
        if(!Boolean.TRUE.equals(links.getFirst().get("active"))) throw ApiException.bad("Vínculo desconectado. Os registros anteriores foram preservados.");
        return links.getFirst();
    }
    private static Instant instant(java.sql.Timestamp value) { return value==null?null:value.toInstant(); }
    private static java.sql.Timestamp timestamp(Instant value) { return value==null?null:java.sql.Timestamp.from(value); }
}
