package br.com.javafinance.common;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @GetMapping("/api/health")
    public Map<String,String> health() { jdbc.queryForObject("SELECT 1",Integer.class); return Map.of("status","UP"); }
}
