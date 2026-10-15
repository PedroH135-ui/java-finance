package br.com.javafinance.movements;
import br.com.javafinance.accounts.*;
import br.com.javafinance.categories.*;
import br.com.javafinance.common.*;
import static br.com.javafinance.movements.MovementDtos.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class MovementService {
    private final MovementRepository movements; private final AccountRepository accounts; private final CategoryRepository categories;
    private final LedgerService ledger; private final Clock clock; private final AuditService audit;
    private final ObjectMapper mapper;
    public MovementService(MovementRepository movements,AccountRepository accounts,CategoryRepository categories,LedgerService ledger,Clock clock,AuditService audit,ObjectMapper mapper) {
        this.movements=movements; this.accounts=accounts; this.categories=categories; this.ledger=ledger; this.clock=clock; this.audit=audit;
        this.mapper=mapper;
    }
    @Transactional
    public View create(UUID owner,Create input) {
        if(input.kind()==Movement.Kind.OPENING) throw ApiException.bad("Saldo inicial é criado junto com a conta.");
        if(input.status()==Movement.Status.CANCELLED) throw ApiException.bad("Não é possível criar um registro cancelado.");
        if(input.kind()!=Movement.Kind.TRANSFER && input.destinationId()!=null) throw ApiException.bad("Destino só se aplica a transferências.");
        BigDecimal amount=Money.parse(input.amount());
        if(amount.signum()==0 || (input.kind()!=Movement.Kind.ADJUSTMENT && amount.signum()<0)) throw ApiException.bad("Informe um valor maior que zero.");
        if(input.kind()==Movement.Kind.TRANSFER && (input.destinationId()==null || input.destinationId().equals(input.accountId())))
            throw ApiException.bad("Escolha duas contas diferentes.");
        // Ordem determinística de locks evita deadlocks em transferências opostas.
        List<UUID> ids=new ArrayList<>(List.of(input.accountId()));
        if(input.destinationId()!=null) ids.add(input.destinationId());
        ids.sort(Comparator.naturalOrder());
        Map<UUID,Account> locked=new HashMap<>();
        for(UUID id:ids) locked.put(id,active(id,owner));
        // A mesma conta serializa duas confirmações da mesma chave de requisição.
        var existing=movements.findByOwnerIdAndRequestKey(owner,input.requestKey());
        if(existing.isPresent()) {
            Movement old=existing.get();
            if(!sameRequest(old,input,amount)) throw ApiException.conflict("Esta chave já foi usada para outra operação.");
            return View.from(old);
        }
        if(input.competence().getDayOfMonth()!=1) throw ApiException.bad("A competência deve ser o primeiro dia do mês.");
        for(Account account:locked.values()) {
            if(input.operationDate().isBefore(account.openingDate)) throw ApiException.bad("A operação não pode anteceder o saldo inicial.");
        }
        if(input.kind()==Movement.Kind.INCOME || input.kind()==Movement.Kind.EXPENSE) {
            Category category=categories.lock(input.categoryId()==null?new UUID(0,0):input.categoryId(),owner).orElseThrow(ApiException::missing);
            if(category.archived || !category.kind.name().equals(input.kind().name())) throw ApiException.bad("Escolha uma categoria ativa compatível.");
        } else if(input.categoryId()!=null) throw ApiException.bad("Transferências e ajustes não usam categoria de consumo.");
        Movement m=new Movement(); m.id=UUID.randomUUID(); m.ownerId=owner; m.description=input.description().strip(); m.amount=amount;
        m.kind=input.kind(); m.status=input.status(); m.accountId=input.accountId(); m.destinationId=input.destinationId(); m.categoryId=input.categoryId();
        m.operationDate=input.operationDate(); m.competence=input.competence(); m.dueDate=input.dueDate();
        m.notes=input.notes()==null?"":input.notes(); m.requestKey=input.requestKey(); m.createdAt=clock.instant();
        m.requestFingerprint=fingerprint(input,amount);
        if(m.status==Movement.Status.CONFIRMED) { validateSettlement(input.settledDate(),m.operationDate,locked.values()); m.settledDate=input.settledDate(); }
        else if(input.settledDate()!=null) throw ApiException.bad("Uma previsão não possui data de efetivação.");
        movements.saveAndFlush(m);
        if(m.status==Movement.Status.CONFIRMED) ledger.post(m);
        audit.record(owner,m.id,"CREATED","Lançamento "+m.status.name()+".");
        return View.from(m);
    }
    private boolean sameRequest(Movement m,Create i,BigDecimal amount) {
        if(m.requestFingerprint!=null) return m.requestFingerprint.equals(fingerprint(i,amount));
        return m.amount.compareTo(amount)==0 && m.kind==i.kind() && m.accountId.equals(i.accountId()) &&
            m.status==i.status() && Objects.equals(m.settledDate,i.settledDate()) &&
            Objects.equals(m.destinationId,i.destinationId()) && Objects.equals(m.categoryId,i.categoryId()) &&
            m.description.equals(i.description().strip()) && m.operationDate.equals(i.operationDate()) &&
            m.competence.equals(i.competence()) && m.dueDate.equals(i.dueDate()) &&
            Objects.equals(m.notes,i.notes()==null?"":i.notes());
    }
    private String fingerprint(Create i,BigDecimal amount) {
        var canonical=new Create(i.requestKey(),i.description().strip(),amount.setScale(2).toPlainString(),i.kind(),i.status(),
            i.accountId(),i.destinationId(),i.categoryId(),i.operationDate(),i.competence(),i.dueDate(),i.settledDate(),i.notes()==null?"":i.notes());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical))); }
        catch(java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    public View get(UUID owner,UUID id) { return View.from(movements.findByIdAndOwnerId(id,owner).orElseThrow(ApiException::missing)); }
    @Transactional
    public View confirm(UUID owner,UUID id,Change input) {
        Movement m=movements.lock(id,owner).orElseThrow(ApiException::missing);
        version(m,input.version());
        if(m.status!=Movement.Status.PLANNED) throw ApiException.conflict("Somente previsões podem ser confirmadas.");
        List<UUID> ids=new ArrayList<>(List.of(m.accountId)); if(m.destinationId!=null) ids.add(m.destinationId); ids.sort(Comparator.naturalOrder());
        List<Account> locked=ids.stream().map(a -> active(a,owner)).toList();
        validateSettlement(input.settledDate(),m.operationDate,locked);
        m.settledDate=input.settledDate(); m.status=Movement.Status.CONFIRMED;
        movements.flush(); ledger.post(m); audit.record(owner,id,"CONFIRMED",input.reason()); return View.from(m);
    }
    @Transactional
    public View cancel(UUID owner,UUID id,Change input) {
        Movement m=movements.lock(id,owner).orElseThrow(ApiException::missing);
        version(m,input.version());
        if(m.kind==Movement.Kind.OPENING) throw ApiException.bad("Corrija o saldo inicial com um ajuste rastreável.");
        if(m.status==Movement.Status.CANCELLED) throw ApiException.conflict("Este registro já foi cancelado.");
        m.status=Movement.Status.CANCELLED; movements.flush(); audit.record(owner,id,"CANCELLED",input.reason()); return View.from(m);
    }
    @Transactional
    public View notes(UUID owner,UUID id,NoteChange input) {
        Movement m=movements.lock(id,owner).orElseThrow(ApiException::missing); version(m,input.version());
        // Registros confirmados são imutáveis. Correção = cancelar + novo lançamento.
        if(m.status!=Movement.Status.PLANNED) throw ApiException.bad("Somente a descrição de previsões pode ser editada. Cancele e recrie para corrigir valores confirmados.");
        audit.revision(owner,id,input.reason(),m.description,m.notes);
        m.description=input.description().strip(); m.notes=input.notes()==null?"":input.notes(); movements.flush();
        return View.from(m);
    }
    public Page list(UUID owner,LocalDate from,LocalDate to,UUID account,UUID category,Movement.Kind kind,Movement.Status status,String text,int page,int size,String sort,String direction,String basis) {
        if(from==null || to==null || from.isAfter(to) || to.isAfter(from.plusYears(10))) throw ApiException.bad("Informe um período válido de até dez anos.");
        if(page<0 || size<1 || size>100) throw ApiException.bad("Paginação inválida.");
        if(!Set.of("operationDate","competence","settledDate").contains(basis)) throw ApiException.bad("Base de data inválida.");
        if(!Set.of("operationDate","amount","description","dueDate").contains(sort) || !Set.of("asc","desc").contains(direction)) throw ApiException.bad("Ordenação inválida.");
        Specification<Movement> spec=(r,q,b) -> {
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(b.equal(r.get("ownerId"),owner)); predicates.add(b.between(r.get(basis),from,to));
            if(account!=null) predicates.add(b.or(b.equal(r.get("accountId"),account),b.equal(r.get("destinationId"),account)));
            if(category!=null) predicates.add(b.equal(r.get("categoryId"),category));
            if(kind!=null) predicates.add(b.equal(r.get("kind"),kind));
            if(status!=null) predicates.add(b.equal(r.get("status"),status));
            if(text!=null && !text.isBlank()) {
                String escaped=text.strip().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
                predicates.add(b.like(b.lower(r.get("description")),"%"+escaped+"%",'\\'));
            }
            return b.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result=movements.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Direction.fromString(direction),sort).and(Sort.by("id"))));
        return new Page(result.getContent().stream().map(View::from).toList(),result.getTotalElements(),page,size,result.getTotalPages());
    }
    private Account active(UUID id,UUID owner) {
        Account a=accounts.lock(id,owner).orElseThrow(ApiException::missing);
        if(a.archived) throw ApiException.bad("A conta está arquivada."); return a;
    }
    private void validateSettlement(LocalDate date,LocalDate operation,Collection<Account> accounts) {
        if(date==null || date.isAfter(LocalDate.now(clock)) || date.isBefore(operation) || accounts.stream().anyMatch(a -> date.isBefore(a.openingDate)))
            throw ApiException.bad("A efetivação deve ocorrer entre a operação e hoje, após o saldo inicial.");
    }
    private void version(Movement m,long version) { if(m.version!=version) throw ApiException.conflict("O lançamento mudou. Atualize a página."); }
}

