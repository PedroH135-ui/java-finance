package br.com.javafinance.banking;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Global capability and individual consent are independent, both fail closed. */
@Component
public class CloudCategoryPolicy {
    private final boolean enabled;
    private final JdbcTemplate jdbc;
    public CloudCategoryPolicy(@Value("${app.category-ai.automatic-cloud-enabled:false}") boolean enabled,
        @Value("${app.category-ai.enabled:false}") boolean advisor,
        @Value("${app.category-ai.cloud-rag:false}") boolean rag, JdbcTemplate jdbc) {
        if(enabled && (!advisor || !rag))throw new IllegalArgumentException("Cloud automation requires the authenticated cloud RAG advisor.");
        this.enabled=enabled;this.jdbc=jdbc;
    }
    public boolean enabled(){return enabled;}
    public boolean activeFor(UUID owner){
        return enabled && owner!=null && Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM category_automation_preference WHERE owner_id=? AND enabled)",Boolean.class,owner));
    }
    /** Hold this lock through application, so disabling wins against an in-flight model request. */
    public boolean lockActive(UUID owner){
        if(!enabled)return false;
        var rows=jdbc.queryForList("SELECT enabled FROM category_automation_preference WHERE owner_id=? FOR UPDATE",owner);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.getFirst().get("enabled"));
    }
}
