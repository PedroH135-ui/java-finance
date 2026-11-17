package br.com.javafinance.banking;

import br.com.javafinance.common.*;
import br.com.javafinance.organization.FinancialNatureService;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class BankProductsService {
 public record ReadCompleted(UUID owner) {}
 private org.springframework.context.ApplicationEventPublisher events;
 @org.springframework.beans.factory.annotation.Autowired
 public void events(org.springframework.context.ApplicationEventPublisher events){this.events=events;}
 public record Read(boolean automatic) {}
 public record SyncState(UUID itemId,boolean automatic,String cardsStatus,String investmentsStatus,Instant cardsCheckedAt,Instant investmentsCheckedAt) {}
 public record Charge(UUID id,String description,BigDecimal amount,LocalDate date,String direction,String status,UUID categoryId,long version,String billId,Integer installment,Integer installments,String categorySource,String categoryMethod,String categoryResolution) {}
 public record Bill(UUID id,String externalId,LocalDate dueDate,BigDecimal total,BigDecimal detailedTotal,long detailCount,long pendingCount,String revision,boolean reconciled) {}
 public record Card(UUID id,String name,boolean billsAvailable,Instant checkedAt,List<Bill> bills,List<Charge> charges) {}
 public record Position(UUID id,String name,String type,String subtype,BigDecimal balance,LocalDate date,BigDecimal profit,BigDecimal available,String status,Instant checkedAt) {}
 public record Payment(UUID id,String description,BigDecimal amount,LocalDate date,long version) {}
 public record Summary(List<SyncState> connections,List<Card> cards,List<Position> positions,List<Payment> payments) {}
 public record Reconcile(@NotNull UUID entryId,@PositiveOrZero long entryVersion,@NotBlank @Pattern(regexp="[a-f0-9]{32}") String revision,boolean confirmed) {}
 public record CategoryChange(@PositiveOrZero long version,UUID categoryId) {}
 private final JdbcTemplate jdbc;private final BankProperties config;private final BankProductsProvider provider;
 private final Clock clock;private final AuditService audit;private final FinancialNatureService natures;private final TransactionTemplate tx;
 public BankProductsService(JdbcTemplate jdbc,BankProperties config,BankProductsProvider provider,Clock clock,AuditService audit,FinancialNatureService natures,PlatformTransactionManager manager){
  this.jdbc=jdbc;this.config=config;this.provider=provider;this.clock=clock;this.audit=audit;this.natures=natures;this.tx=new TransactionTemplate(manager);
 }
 private UUID item(UUID owner,UUID link){
  var ids=jdbc.query("SELECT item_id FROM bank_link WHERE id=? AND owner_id=? AND active",(rs,n)->rs.getObject(1,UUID.class),link,owner);
  if(ids.isEmpty())throw ApiException.missing();UUID item=ids.getFirst();
  if(!owner.equals(config.getOwnerId())||!config.ready()||!config.getItemIds().contains(item))throw ApiException.bad("Leitura adicional não habilitada para esta conexão.");return item;
 }
 public void readIfEnabled(UUID owner,UUID link){
  if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM bank_product_sync s JOIN bank_link l ON l.owner_id=s.owner_id AND l.item_id=s.item_id WHERE l.id=? AND l.owner_id=? AND s.automatic)",Boolean.class,link,owner)))read(owner,link,new Read(true));
 }
 public void pause(UUID owner,UUID link){
  var items=jdbc.query("SELECT item_id FROM bank_link WHERE id=? AND owner_id=?",(rs,n)->rs.getObject(1,UUID.class),link,owner);if(items.isEmpty())throw ApiException.missing();
  jdbc.update("UPDATE bank_product_sync SET automatic=false WHERE owner_id=? AND item_id=?",owner,items.getFirst());
 }
 public void read(UUID owner,UUID link,Read input){
  UUID item=item(owner,link);Instant now=clock.instant();
  int reserved=jdbc.update("""
   INSERT INTO bank_product_sync(owner_id,item_id,automatic,last_attempt_at,lease_until) VALUES (?,?,?,?,?)
   ON CONFLICT(owner_id,item_id) DO UPDATE SET automatic=EXCLUDED.automatic,last_attempt_at=EXCLUDED.last_attempt_at,lease_until=EXCLUDED.lease_until
   WHERE (bank_product_sync.last_attempt_at IS NULL OR bank_product_sync.last_attempt_at<=?) AND (bank_product_sync.lease_until IS NULL OR bank_product_sync.lease_until<=?)
   """,owner,item,input.automatic(),java.sql.Timestamp.from(now),java.sql.Timestamp.from(now.plusSeconds(300)),java.sql.Timestamp.from(now.minusSeconds(60)),java.sql.Timestamp.from(now));
  if(reserved==0)throw ApiException.conflict("Aguarde um minuto antes de repetir a leitura adicional.");
  LocalDate today=LocalDate.now(clock);
  try{
   var cards=Objects.requireNonNull(provider.cards(item,today.minusDays(92),today.plusDays(62)));
   if(cards.size()>10)throw ApiException.bad("Limite de cartões excedido.");
   tx.executeWithoutResult(status->{
    lockRead(owner,item,now);
    jdbc.update("UPDATE bank_card SET present=false WHERE owner_id=? AND item_id=?",owner,item);
    for(var card:cards)storeCard(owner,item,card,now,today.minusDays(92),today.plusDays(62));
    String state=cards.isEmpty()?"NO_DATA":cards.stream().anyMatch(c->!c.billsAvailable())?"PARTIAL":"READY";
    jdbc.update("UPDATE bank_product_sync SET cards_status=?,cards_checked_at=? WHERE owner_id=? AND item_id=?",state,java.sql.Timestamp.from(now),owner,item);
   });
  }catch(RuntimeException error){recordFailure(owner,item,"cards_status",error,now);}
  try{
   var positions=Objects.requireNonNull(provider.investments(item));if(positions.size()>2000)throw ApiException.bad("Limite de posições excedido.");
   tx.executeWithoutResult(status->{
    lockRead(owner,item,now);
    jdbc.update("UPDATE bank_investment SET present=false WHERE owner_id=? AND item_id=?",owner,item);
    for(var p:positions)jdbc.update("""
     INSERT INTO bank_investment(id,owner_id,item_id,external_id,name,type,subtype,balance,value_date,profit,available,status,checked_at)
     VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(owner_id,item_id,external_id) DO UPDATE
     SET name=EXCLUDED.name,type=EXCLUDED.type,subtype=EXCLUDED.subtype,balance=EXCLUDED.balance,value_date=EXCLUDED.value_date,profit=EXCLUDED.profit,
       available=EXCLUDED.available,status=EXCLUDED.status,checked_at=EXCLUDED.checked_at,present=true
     """,UUID.randomUUID(),owner,item,p.id(),p.name(),p.type(),p.subtype(),p.balance(),p.date(),p.profit(),p.available(),p.status(),java.sql.Timestamp.from(now));
    jdbc.update("UPDATE bank_product_sync SET investments_status=?,investments_checked_at=? WHERE owner_id=? AND item_id=?",positions.isEmpty()?"NO_DATA":"READY",java.sql.Timestamp.from(now),owner,item);
   });
  }catch(RuntimeException error){recordFailure(owner,item,"investments_status",error,now);}
  jdbc.update("UPDATE bank_product_sync SET lease_until=null WHERE owner_id=? AND item_id=? AND last_attempt_at=?",owner,item,java.sql.Timestamp.from(now));
  audit.record(owner,link,"BANK_PRODUCTS_READ","Leitura adicional concluída; confira a cobertura de cada produto. Sem conciliação, lançamento ou alteração de saldo.");
  if(events!=null)events.publishEvent(new ReadCompleted(owner));
 }
 private void lockRead(UUID owner,UUID item,Instant started){
  if(jdbc.queryForList("SELECT item_id FROM bank_product_sync WHERE owner_id=? AND item_id=? AND last_attempt_at=? AND lease_until>? FOR UPDATE",owner,item,java.sql.Timestamp.from(started),java.sql.Timestamp.from(clock.instant())).isEmpty())throw ApiException.conflict("Outra leitura já substituiu esta consulta.");
 }
 private void recordFailure(UUID owner,UUID item,String column,RuntimeException error,Instant started){
  String state=error instanceof BankReadException e&&e.code==BankReadException.Code.ACCESS_DENIED?"UNAVAILABLE":"ERROR";
  jdbc.update("UPDATE bank_product_sync SET "+column+"=? WHERE owner_id=? AND item_id=? AND last_attempt_at=?",state,owner,item,java.sql.Timestamp.from(started));
  org.slf4j.LoggerFactory.getLogger(getClass()).warn("bank_product_read_failed product={} code={}",column,state);
 }
 private void storeCard(UUID owner,UUID item,BankProductsProvider.Card source,Instant checked,LocalDate from,LocalDate to){
  UUID card=jdbc.queryForObject("""
   INSERT INTO bank_card(id,owner_id,item_id,external_id,name,bills_available,checked_at) VALUES (?,?,?,?,?,?,?)
   ON CONFLICT(owner_id,item_id,external_id) DO UPDATE SET name=EXCLUDED.name,bills_available=EXCLUDED.bills_available,checked_at=EXCLUDED.checked_at,present=true RETURNING id
   """,UUID.class,UUID.randomUUID(),owner,item,source.id(),source.name(),source.billsAvailable(),java.sql.Timestamp.from(checked));
  if(source.bills().size()>2000||source.charges().size()>2000)throw ApiException.bad("Reduza o período de leitura do cartão.");
  if(source.billsAvailable())jdbc.update("UPDATE bank_card_bill SET present=false WHERE owner_id=? AND card_id=?",owner,card);
  jdbc.update("UPDATE bank_card_charge SET present=false WHERE owner_id=? AND card_id=? AND date BETWEEN ? AND ?",owner,card,from,to);
  for(var bill:source.bills())jdbc.update("""
   INSERT INTO bank_card_bill(id,owner_id,card_id,external_id,due_date,total) VALUES (?,?,?,?,?,?)
   ON CONFLICT(card_id,external_id) DO UPDATE SET due_date=EXCLUDED.due_date,total=EXCLUDED.total,present=true
   """,UUID.randomUUID(),owner,card,bill.id(),bill.dueDate(),bill.total());
  for(var charge:source.charges())jdbc.update("""
   INSERT INTO bank_card_charge(id,owner_id,card_id,external_id,description,amount,date,direction,provider_status,external_bill_id,installment,total_installments)
   VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(card_id,external_id) DO UPDATE SET
    version=bank_card_charge.version+CASE WHEN (bank_card_charge.description,bank_card_charge.amount,bank_card_charge.date,bank_card_charge.direction,bank_card_charge.provider_status,bank_card_charge.external_bill_id)
      IS DISTINCT FROM (EXCLUDED.description,EXCLUDED.amount,EXCLUDED.date,EXCLUDED.direction,EXCLUDED.provider_status,EXCLUDED.external_bill_id) THEN 1 ELSE 0 END,
    description=EXCLUDED.description,amount=EXCLUDED.amount,date=EXCLUDED.date,direction=EXCLUDED.direction,provider_status=EXCLUDED.provider_status,
    category_auto_resolution=CASE WHEN (bank_card_charge.description,bank_card_charge.amount,bank_card_charge.date,bank_card_charge.direction,bank_card_charge.provider_status,bank_card_charge.external_bill_id)
      IS DISTINCT FROM (EXCLUDED.description,EXCLUDED.amount,EXCLUDED.date,EXCLUDED.direction,EXCLUDED.provider_status,EXCLUDED.external_bill_id) THEN NULL ELSE bank_card_charge.category_auto_resolution END,
    external_bill_id=EXCLUDED.external_bill_id,installment=EXCLUDED.installment,total_installments=EXCLUDED.total_installments,present=true
   """,UUID.randomUUID(),owner,card,charge.id(),charge.description(),charge.amount(),charge.date(),charge.direction(),charge.status(),charge.billId(),charge.installment(),charge.installments());
 }
 private static Instant instant(java.sql.Timestamp timestamp){return timestamp==null?null:timestamp.toInstant();}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Summary summary(UUID owner){
  var states=jdbc.query("SELECT * FROM bank_product_sync WHERE owner_id=? ORDER BY item_id",(rs,n)->new SyncState(rs.getObject("item_id",UUID.class),rs.getBoolean("automatic"),rs.getString("cards_status"),rs.getString("investments_status"),instant(rs.getTimestamp("cards_checked_at")),instant(rs.getTimestamp("investments_checked_at"))),owner);
  var bills=jdbc.query("SELECT s.*,EXISTS(SELECT 1 FROM valid_card_reconciliation v WHERE v.bill_id=s.id AND v.owner_id=s.owner_id) reconciled FROM card_bill_snapshot s JOIN bank_card_bill b ON b.id=s.id AND b.owner_id=s.owner_id WHERE s.owner_id=? AND b.present ORDER BY s.due_date DESC,s.id",(rs,n)->Map.entry(rs.getObject("card_id",UUID.class),new Bill(rs.getObject("id",UUID.class),rs.getString("external_id"),rs.getObject("due_date",LocalDate.class),rs.getBigDecimal("total"),rs.getBigDecimal("detailed_total"),rs.getLong("detail_count"),rs.getLong("pending_count"),rs.getString("signature"),rs.getBoolean("reconciled"))),owner);
  var charges=jdbc.query("SELECT * FROM bank_card_charge WHERE owner_id=? AND present ORDER BY date DESC,id LIMIT 2000",(rs,n)->Map.entry(rs.getObject("card_id",UUID.class),new Charge(rs.getObject("id",UUID.class),rs.getString("description"),rs.getBigDecimal("amount"),rs.getObject("date",LocalDate.class),rs.getString("direction"),rs.getString("provider_status"),Objects.equals(rs.getString("category_description"),rs.getString("description"))&&Objects.equals(rs.getString("category_direction"),rs.getString("direction"))?rs.getObject("category_id",UUID.class):null,rs.getLong("version"),rs.getString("external_bill_id"),rs.getObject("installment",Integer.class),rs.getObject("total_installments",Integer.class),rs.getString("category_source"),rs.getString("category_auto_method"),rs.getString("category_auto_resolution"))),owner);
  var cards=jdbc.query("SELECT * FROM bank_card WHERE owner_id=? AND present ORDER BY name,id",(rs,n)->{
   UUID id=rs.getObject("id",UUID.class);return new Card(id,rs.getString("name"),rs.getBoolean("bills_available"),instant(rs.getTimestamp("checked_at")),bills.stream().filter(b->b.getKey().equals(id)).map(Map.Entry::getValue).toList(),charges.stream().filter(c->c.getKey().equals(id)).map(Map.Entry::getValue).toList());},owner);
  var positions=jdbc.query("SELECT * FROM bank_investment WHERE owner_id=? AND present ORDER BY item_id,name,id",(rs,n)->new Position(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("type"),rs.getString("subtype"),rs.getBigDecimal("balance"),rs.getObject("value_date",LocalDate.class),rs.getBigDecimal("profit"),rs.getBigDecimal("available"),rs.getString("status"),instant(rs.getTimestamp("checked_at"))),owner);
  var payments=jdbc.query("SELECT b.* FROM bank_entry b WHERE b.owner_id=? AND b.provider_status='POSTED' AND b.direction='DEBIT' AND NOT b.hidden AND b.state<>'IGNORED' AND b.movement_id IS NULL AND b.transfer_id IS NULL AND b.date>=? ORDER BY b.date DESC,b.id LIMIT 200",(rs,n)->new Payment(rs.getObject("id",UUID.class),rs.getString("description"),rs.getBigDecimal("amount"),rs.getObject("date",LocalDate.class),rs.getLong("version")),owner,LocalDate.now(clock).minusDays(150));
  var rules=natures.rules();payments=payments.stream().filter(p->{var k=natures.recognize(p.description(),"DEBIT",rules);return k!=null&&k.nature().equals("CARD_PAYMENT");}).toList();
  return new Summary(states,cards,positions,payments);
 }
 @Transactional public void reconcile(UUID owner,UUID bill,Reconcile input){
  if(!input.confirmed())throw ApiException.bad("Confirme a cobertura da fatura e o pagamento correspondente.");
  var entryLinks=jdbc.query("SELECT link_id FROM bank_entry WHERE id=? AND owner_id=?",(rs,n)->rs.getObject(1,UUID.class),input.entryId(),owner);if(entryLinks.isEmpty())throw ApiException.missing();
  jdbc.queryForObject("SELECT id FROM bank_link WHERE id=? AND owner_id=? FOR UPDATE",UUID.class,entryLinks.getFirst(),owner);
  var entries=jdbc.queryForList("SELECT e.*,l.item_id FROM bank_entry e JOIN bank_link l ON l.id=e.link_id AND l.owner_id=e.owner_id WHERE e.id=? AND e.owner_id=? FOR UPDATE OF e",input.entryId(),owner);var entry=entries.getFirst();
  var billCards=jdbc.query("SELECT card_id FROM bank_card_bill WHERE id=? AND owner_id=?",(rs,n)->rs.getObject(1,UUID.class),bill,owner);if(billCards.isEmpty())throw ApiException.missing();
  jdbc.queryForObject("SELECT id FROM bank_card WHERE id=? AND owner_id=? FOR UPDATE",UUID.class,billCards.getFirst(),owner);
  var snapshot=jdbc.queryForMap("SELECT s.*,c.item_id FROM card_bill_snapshot s JOIN bank_card c ON c.id=s.card_id AND c.owner_id=s.owner_id WHERE s.id=? AND s.owner_id=?",bill,owner);
  if(!Objects.equals(snapshot.get("signature"),input.revision())||((Number)entry.get("version")).longValue()!=input.entryVersion())throw ApiException.conflict("Os dados mudaram. Atualize a fatura antes de conciliar.");
  var knowledge=natures.recognize((String)entry.get("description"),"DEBIT");
  if(knowledge==null||!knowledge.nature().equals("CARD_PAYMENT")||!Objects.equals(entry.get("item_id"),snapshot.get("item_id")))throw ApiException.bad("Escolha o pagamento de fatura da mesma conexão.");
  if(!Boolean.TRUE.equals(snapshot.get("available"))||((Number)snapshot.get("detail_count")).longValue()==0||((Number)snapshot.get("pending_count")).longValue()!=0||((BigDecimal)snapshot.get("detailed_total")).compareTo((BigDecimal)snapshot.get("total"))!=0||((BigDecimal)entry.get("amount")).compareTo((BigDecimal)snapshot.get("total"))!=0)
   throw ApiException.bad("A cobertura ou os totais não conferem. Pagamento parcial, juros, compras ausentes ou parcelas pendentes exigem revisão; não vamos estimar a diferença.");
  if(Boolean.TRUE.equals(entry.get("hidden"))||!"POSTED".equals(entry.get("provider_status"))||!"DEBIT".equals(entry.get("direction"))||"IGNORED".equals(entry.get("state"))||entry.get("movement_id")!=null||entry.get("transfer_id")!=null)throw ApiException.bad("Pagamento indisponível para conciliação.");
  if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM card_bill_reconciliation WHERE entry_id=? AND owner_id=? AND bill_id<>? AND active)",Boolean.class,input.entryId(),owner,bill)))throw ApiException.bad("Esse pagamento já está vinculado a outra fatura.");
  natures.confirm(owner,input.entryId(),new FinancialNatureService.Change(input.entryVersion(),FinancialNatureService.Nature.CARD_PAYMENT,"NONE",null,true));
  jdbc.update("UPDATE card_bill_reconciliation SET active=false WHERE bill_id=? AND owner_id=? AND active",bill,owner);
  jdbc.update("INSERT INTO card_bill_reconciliation(bill_id,owner_id,entry_id,bill_signature) VALUES (?,?,?,?)",bill,owner,input.entryId(),input.revision());
  audit.record(owner,bill,"CARD_BILL_RECONCILED","Detalhes conferidos substituem o pagamento nos indicadores de consumo, sem alterar saldo ou fluxo de caixa.");
 }
 @Transactional public void undo(UUID owner,UUID bill){
  var cards=jdbc.query("SELECT card_id FROM bank_card_bill WHERE id=? AND owner_id=?",(rs,n)->rs.getObject(1,UUID.class),bill,owner);if(cards.isEmpty())throw ApiException.missing();
  jdbc.queryForObject("SELECT id FROM bank_card WHERE id=? AND owner_id=? FOR UPDATE",UUID.class,cards.getFirst(),owner);
  if(jdbc.update("UPDATE card_bill_reconciliation SET active=false WHERE bill_id=? AND owner_id=? AND active",bill,owner)==0)throw ApiException.conflict("A fatura já está sem conciliação ativa.");
  audit.record(owner,bill,"CARD_BILL_RECONCILIATION_UNDONE","Conciliação desfeita, sem apagar categorias ou alterar saldo.");
 }
 @Transactional public void categorize(UUID owner,UUID id,CategoryChange input){
  var rows=jdbc.queryForList("SELECT * FROM bank_card_charge WHERE id=? AND owner_id=? FOR UPDATE",id,owner);if(rows.isEmpty())throw ApiException.missing();var entry=rows.getFirst();
  if(((Number)entry.get("version")).longValue()!=input.version())throw ApiException.conflict("O registro mudou. Atualize o cartão.");
  if(input.categoryId()!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM category WHERE id=? AND owner_id=? AND kind='EXPENSE' AND NOT archived)",Boolean.class,input.categoryId(),owner)))throw ApiException.bad("Escolha uma categoria de despesa ativa.");
  jdbc.update("UPDATE bank_card_charge SET category_id=?,category_description=description,category_direction=direction,category_source='MANUAL',category_auto_blocked=true,category_auto_method=null,category_auto_confidence=null,version=version+1 WHERE id=? AND owner_id=?",input.categoryId(),id,owner);
  if(input.categoryId()!=null&&"DEBIT".equals(entry.get("direction"))){
   String key=CategoryAdvisor.memoryKey((String)entry.get("description"));
   jdbc.update("INSERT INTO category_correction(owner_id,direction,description_key,category_id) VALUES (?,'DEBIT',?,?) ON CONFLICT(owner_id,direction,description_key) DO UPDATE SET category_id=EXCLUDED.category_id,updated_at=now()",owner,key,input.categoryId());
   jdbc.update("UPDATE bank_card_charge SET category_auto_resolution=null WHERE owner_id=? AND category_auto_resolution='NO_CATEGORY' AND category_source IS DISTINCT FROM 'MANUAL' AND NOT category_auto_blocked AND lower(regexp_replace(btrim(description),'\\s+',' ','g'))=?",owner,key);
   if(events!=null)events.publishEvent(new BankAssistanceService.CategoryWorkRequested(owner));
  }
  audit.record(owner,id,"CARD_CATEGORY_CHANGED","Categoria do detalhe de cartão corrigida pelo usuário, sem alterar valores ou saldo.");
 }
 public Charge charge(UUID owner,UUID id){
  var rows=jdbc.query("SELECT * FROM bank_card_charge WHERE id=? AND owner_id=?",(rs,n)->new Charge(rs.getObject("id",UUID.class),rs.getString("description"),rs.getBigDecimal("amount"),rs.getObject("date",LocalDate.class),rs.getString("direction"),rs.getString("provider_status"),Objects.equals(rs.getString("category_description"),rs.getString("description"))&&Objects.equals(rs.getString("category_direction"),rs.getString("direction"))?rs.getObject("category_id",UUID.class):null,rs.getLong("version"),rs.getString("external_bill_id"),rs.getObject("installment",Integer.class),rs.getObject("total_installments",Integer.class),rs.getString("category_source"),rs.getString("category_auto_method"),rs.getString("category_auto_resolution")),id,owner);
  return rows.stream().findFirst().orElseThrow(ApiException::missing);
 }
}
