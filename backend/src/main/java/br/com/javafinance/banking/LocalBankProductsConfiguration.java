package br.com.javafinance.banking;
import br.com.javafinance.common.ApiException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit local fixture profile; fail closed before any provider is selected outside the guarded DB. */
@Configuration @Profile("local-review")
public class LocalBankProductsConfiguration {
 private final UUID item;private final JdbcTemplate jdbc;private final BankProperties config;private final Clock clock;
 private final String readId="synthetic-next-salary-"+UUID.randomUUID();
 public LocalBankProductsConfiguration(@Value("${spring.datasource.url}")String url,@Value("${app.hosting}")String hosting,JdbcTemplate jdbc,BankProperties config,Clock clock){
  if(!url.equals("jdbc:postgresql://127.0.0.1:55434/java_finance_review")||!hosting.equals("local")||!config.getClientId().equals("synthetic-products")||!config.getClientSecret().equals("synthetic-products")||config.getItemIds().size()!=1)throw new IllegalStateException("Synthetic products require the isolated loopback review configuration.");
  var users=jdbc.query("SELECT email FROM app_user WHERE id=?",(rs,n)->rs.getString(1),config.getOwnerId());
  if(users.size()!=1||!users.getFirst().matches("modules-[a-f0-9-]+@example\\.test"))throw new IllegalStateException("Synthetic review owner guard failed.");
  this.jdbc=jdbc;this.config=config;this.clock=clock;this.item=config.getItemIds().iterator().next();
 }
 private void check(UUID id){if(!item.equals(id))throw ApiException.missing();}
 @Bean @Primary BankProvider syntheticBankProvider(){return new BankProvider(){
  @Override public Snapshot accounts(UUID id){check(id);var accounts=jdbc.query("SELECT external_account_id,name,reported_balance FROM bank_link WHERE owner_id=? AND item_id=?",(rs,n)->new RemoteAccount(rs.getObject(1,UUID.class),rs.getString(2),"BANK","BRL",rs.getBigDecimal(3)),config.getOwnerId(),item);return new Snapshot(clock.instant(),accounts);}
  @Override public List<RemoteEntry> transactions(UUID account,LocalDate from,LocalDate to){
   if(accounts(item).accounts().stream().noneMatch(a->a.id().equals(account)))throw ApiException.missing();LocalDate date=LocalDate.now(clock);
   return date.isBefore(from)||date.isAfter(to)?List.of():List.of(new RemoteEntry(readId,"Transferência Recebida|EMPRESA DEMONSTRAÇÃO LTDA",new BigDecimal("3100"),date,"CREDIT","POSTED","BUSINESS"));
  }
 };}
 @Bean @Primary BankProductsProvider syntheticProducts(){return new BankProductsProvider(){
  @Override public List<Card> cards(UUID id,LocalDate from,LocalDate to){check(id);LocalDate date=LocalDate.now(clock);
   var charges=List.of(new Charge("synthetic-market","Mercado de demonstração",new BigDecimal("400"),date,"DEBIT","POSTED","synthetic-current-bill",null,null),new Charge("synthetic-delivery","Delivery de demonstração",new BigDecimal("100"),date,"DEBIT","POSTED","synthetic-current-bill",1,3),new Charge("synthetic-refund","Estorno de demonstração",new BigDecimal("20"),date,"CREDIT","POSTED","synthetic-current-bill",null,null));
   return List.of(new Card(UUID.fromString("11111111-1111-4111-8111-111111111111"),"Cartão fictício",List.of(new Bill("synthetic-current-bill",date,new BigDecimal("480"))),charges,true));
  }
  @Override public List<Position> investments(UUID id){check(id);return List.of(new Position(UUID.fromString("22222222-2222-4222-8222-222222222222"),"RDB da demonstração","FIXED_INCOME","RDB",new BigDecimal("2500"),LocalDate.now(clock),null,new BigDecimal("2400"),"ACTIVE"));}
 };}
}
