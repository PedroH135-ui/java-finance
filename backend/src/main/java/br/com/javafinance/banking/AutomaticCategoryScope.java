package br.com.javafinance.banking;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** This prototype may write categories only in the explicitly selected synthetic local profile. */
@Component
public class AutomaticCategoryScope {
    private final boolean enabled;
    private final UUID owner;
    private final JdbcTemplate jdbc;
    private CloudCategoryPolicy cloud;
    @org.springframework.beans.factory.annotation.Autowired
    public void cloudPolicy(CloudCategoryPolicy cloud){this.cloud=cloud;}
    public AutomaticCategoryScope(@Value("${app.category-ai.automatic-demo-enabled:false}") boolean enabled,
        @Value("${app.category-ai.automatic-demo-owner:}") String owner,
        @Value("${app.hosting:local}") String hosting,@Value("${spring.datasource.url}") String database,
        @Value("${VERCEL_ENV:}") String runtime,@Value("${app.category-ai.local-vector-demo:false}") boolean vector,
        JdbcTemplate jdbc) {
        if(enabled && (!"local".equals(hosting) || !runtime.isBlank() || !vector
            || !"jdbc:postgresql://127.0.0.1:55434/java_finance_review".equals(database)))
            throw new IllegalArgumentException("Automatic categories are restricted to the synthetic local review database.");
        this.enabled=enabled;this.owner=enabled?UUID.fromString(owner):null;this.jdbc=jdbc;
    }
    public boolean enabled() {return enabled;}
    public UUID owner() {return owner;}
    public boolean cloudEnabled(){return cloud!=null && cloud.enabled();}
    public boolean availableFor(UUID user){return cloudEnabled() || activeFor(user);}
    public boolean lockActive(UUID user){return cloudEnabled()?cloud.lockActive(user):activeFor(user);}
    public boolean activeFor(UUID user) {
        if(cloudEnabled())return cloud.activeFor(user);
        return enabled && owner.equals(user) && Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM app_user WHERE id=? AND email='demo@javafinance.local')",Boolean.class,user));
    }
}
