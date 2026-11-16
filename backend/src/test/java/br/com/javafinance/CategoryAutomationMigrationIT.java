package br.com.javafinance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

class CategoryAutomationMigrationIT {
    @Test void upgradingV9PreservesMoneyAndExistingChoicesIncludingRemoval(){
        try(var db=new PostgreSQLContainer<>("postgres:16.10-alpine")){
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).target("9").load().migrate();
            var jdbc=new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()));
            UUID owner=UUID.randomUUID(),account=UUID.randomUUID(),link=UUID.randomUUID(),category=UUID.randomUUID();
            jdbc.update("INSERT INTO app_user(id,email,display_name,password_hash,created_at) VALUES (?,?,?,'synthetic-placeholder',now())",owner,"demo@example.test","Fictício");
            jdbc.update("INSERT INTO account(id,owner_id,name,kind,opening_date) VALUES (?,?,?,'CHECKING',?)",account,owner,"Banco fictício",LocalDate.of(2026,11,1));
            jdbc.update("INSERT INTO category(id,owner_id,name,kind) VALUES (?,?,?,'EXPENSE')",category,owner,"Mercado");
            jdbc.update("INSERT INTO bank_link(id,owner_id,item_id,external_account_id,account_id,name,import_from,reported_balance) VALUES (?,?,?,?,?,?,?,?)",link,owner,UUID.randomUUID(),UUID.randomUUID(),account,"Fictício",LocalDate.of(2026,11,1),new BigDecimal("123.45"));
            UUID selected=UUID.randomUUID(),removed=UUID.randomUUID(),untouched=UUID.randomUUID();
            for(UUID id:new UUID[]{selected,removed,untouched})
                jdbc.update("INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status) VALUES (?,?,?,?,?,'10.00','2026-11-15','DEBIT','POSTED')",id,owner,link,id.toString(),"Compra fictícia");
            jdbc.update("UPDATE bank_entry SET category_id=?,category_description=description,category_direction=direction WHERE id=?",category,selected);
            jdbc.update("INSERT INTO audit_event(id,owner_id,resource_id,action,reason,created_at) VALUES (?,?,?,'BANK_CATEGORY_CHANGED','Categoria removida pelo usuário',now())",UUID.randomUUID(),owner,removed);
            String before=jdbc.queryForObject("SELECT jsonb_agg(to_jsonb(f) ORDER BY id)::text FROM financial_record f",String.class);
            var result=Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).load().migrate();
            assertThat(result.migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT jsonb_agg(to_jsonb(f) ORDER BY id)::text FROM financial_record f",String.class)).isEqualTo(before);
            for(UUID id:new UUID[]{selected,removed}){
                assertThat(jdbc.queryForObject("SELECT category_source FROM bank_entry WHERE id=?",String.class,id)).isEqualTo("MANUAL");
                assertThat(jdbc.queryForObject("SELECT category_auto_blocked FROM bank_entry WHERE id=?",Boolean.class,id)).isTrue();
            }
            assertThat(jdbc.queryForObject("SELECT category_source FROM bank_entry WHERE id=?",String.class,untouched)).isNull();
            assertThat(jdbc.queryForObject("SELECT category_auto_blocked FROM bank_entry WHERE id=?",Boolean.class,untouched)).isFalse();
            assertThat(jdbc.queryForObject("SELECT reported_balance FROM bank_link WHERE id=?",BigDecimal.class,link)).isEqualByComparingTo("123.45");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM movement",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM posting",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_transfer_pair",Long.class)).isZero();
        }
    }
}
