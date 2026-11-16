package br.com.javafinance.movements;

import br.com.javafinance.common.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialRecordService {
    public record Record(UUID id,String description,BigDecimal amount,String kind,String status,UUID accountId,
        UUID destinationId,UUID categoryId,LocalDate operationDate,LocalDate competence,LocalDate dueDate,
        LocalDate settledDate,String source,String notes,long version,UUID bankEntryId,UUID bankLinkId,
        Long bankEntryVersion,String bankProviderStatus,String bankState,boolean hidden,String categorySource,String financialNature,String categoryResolution) {}
    public record Page(List<Record> items,long total,int page,int size,long pages) {}
    private final NamedParameterJdbcTemplate jdbc;
    public FinancialRecordService(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page list(UUID owner,LocalDate from,LocalDate to,UUID account,String category,Movement.Kind kind,
        Movement.Status status,String text,int page,int size,String sort,String direction,String basis,boolean hidden) {
        return list(owner,from,to,account,category,kind,status,text,page,size,sort,direction,basis,hidden,false);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page list(UUID owner,LocalDate from,LocalDate to,UUID account,String category,Movement.Kind kind,
        Movement.Status status,String text,int page,int size,String sort,String direction,String basis,boolean hidden,boolean consumption) {
        String relation=consumption?"consumption_record":"financial_record";
        Map<String,String> fields=Map.of("operationDate","operation_date","competence","competence","settledDate","settled_date",
            "dueDate","due_date","amount","amount","description","description");
        if(from==null || to==null || to.isBefore(from) || to.isAfter(from.plusYears(10)) || page<0 || page>10000
            || size<1 || size>100 || !fields.containsKey(sort) || !Set.of("asc","desc").contains(direction)
            || !Set.of("operationDate","competence","settledDate").contains(basis) || text.length()>200)
            throw ApiException.bad("Filtros inválidos.");
        var args=new HashMap<String,Object>(); args.put("owner",owner); args.put("from",from); args.put("to",to);
        args.put("hidden",hidden);
        String where="owner_id=:owner AND hidden=:hidden AND "+fields.get(basis)+" BETWEEN :from AND :to";
        if(account!=null) { where+=" AND (account_id=:account OR destination_id=:account)"; args.put("account",account); }
        if(category!=null && !category.isBlank()) {
            if(category.equals("uncategorized")) where+=" AND category_id IS NULL AND kind IN ('INCOME','EXPENSE') AND NOT EXISTS(SELECT 1 FROM valid_financial_nature n WHERE n.entry_id="+relation+".bank_entry_id AND n.owner_id="+relation+".owner_id AND n.nature<>'ORDINARY')";
            else { try { args.put("category",UUID.fromString(category)); } catch(IllegalArgumentException ex) { throw ApiException.bad("Categoria inválida."); }
                where+=" AND category_id=:category"; }
        }
        if(kind!=null) { where+=" AND kind=:kind"; args.put("kind",kind.name()); }
        if(status!=null) { where+=" AND status=:status"; args.put("status",status.name()); }
        if(!text.isBlank()) { where+=" AND position(lower(:text) in lower(description))>0"; args.put("text",text.strip()); }
        long total=jdbc.queryForObject("SELECT count(*) FROM "+relation+" WHERE "+where,args,Long.class);
        args.put("limit",size); args.put("offset",page*size);
        var rows=jdbc.query("SELECT f.*,b.category_source,b.category_auto_resolution,n.nature financial_nature FROM (SELECT * FROM "+relation+" WHERE "+where+" ORDER BY "+fields.get(sort)+" "+direction+",id LIMIT :limit OFFSET :offset) f LEFT JOIN bank_entry b ON b.id=f.bank_entry_id AND b.owner_id=f.owner_id LEFT JOIN valid_financial_nature n ON n.entry_id=f.bank_entry_id AND n.owner_id=f.owner_id ORDER BY f."+fields.get(sort)+" "+direction+",f.id",args,
            (rs,n)->new Record(rs.getObject("id",UUID.class),rs.getString("description"),rs.getBigDecimal("amount"),rs.getString("kind"),
                rs.getString("status"),rs.getObject("account_id",UUID.class),rs.getObject("destination_id",UUID.class),rs.getObject("category_id",UUID.class),
                rs.getObject("operation_date",LocalDate.class),rs.getObject("competence",LocalDate.class),rs.getObject("due_date",LocalDate.class),
                rs.getObject("settled_date",LocalDate.class),rs.getString("source"),rs.getString("notes"),rs.getLong("version"),
                rs.getObject("bank_entry_id",UUID.class),rs.getObject("bank_link_id",UUID.class),rs.getObject("bank_entry_version",Long.class),
                rs.getString("bank_provider_status"),rs.getString("bank_state"),rs.getBoolean("hidden"),rs.getString("category_source"),rs.getString("financial_nature"),rs.getString("category_auto_resolution")));
        return new Page(rows,total,page,size,(total+size-1)/size);
    }
}
