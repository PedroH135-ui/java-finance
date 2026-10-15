package br.com.javafinance.common;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public AuditService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    public void record(UUID owner, UUID resource, String action, String reason) {
        jdbc.update("INSERT INTO audit_event(id, owner_id, resource_id, action, reason, created_at) VALUES (?,?,?,?,?,?)",
            UUID.randomUUID(), owner, resource, action, reason, java.sql.Timestamp.from(clock.instant()));
    }
    public List<Map<String,Object>> forResource(UUID owner, UUID resource) {
        return jdbc.queryForList("SELECT action, reason, created_at, previous_description, previous_notes FROM audit_event WHERE owner_id=? AND resource_id=? ORDER BY created_at", owner, resource);
    }
    public void revision(UUID owner, UUID resource, String reason, String description, String notes) {
        jdbc.update("INSERT INTO audit_event(id, owner_id, resource_id, action, reason, created_at, previous_description, previous_notes) VALUES (?,?,?,?,?,?,?,?)",
            UUID.randomUUID(), owner, resource, "DESCRIPTION_UPDATED", reason, java.sql.Timestamp.from(clock.instant()), description, notes);
    }
}

