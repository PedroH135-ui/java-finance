package br.com.javafinance.organization;

import br.com.javafinance.common.ApiException;
import br.com.javafinance.users.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goals/positions")
public class GoalPositionController {
    private final JdbcTemplate jdbc;
    public GoalPositionController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Link(UUID goalId, UUID previousGoalId) {}
    public record Position(UUID positionId, UUID goalId,String name,String status,boolean present,
                           BigDecimal balance,BigDecimal missing,Instant checkedAt) {}

    @GetMapping List<Position> list(@AuthenticationPrincipal UserPrincipal user) {
        return jdbc.query("""
            SELECT p.id,p.goal_id,p.name,p.status,p.present,p.balance,greatest(g.target-p.balance,0),p.checked_at
            FROM bank_investment p JOIN savings_goal g ON g.id=p.goal_id AND g.owner_id=p.owner_id
            WHERE p.owner_id=? ORDER BY g.title
            """,(rs,i)->new Position(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),
            rs.getString(4),rs.getBoolean(5),rs.getBigDecimal(6),rs.getBigDecimal(7),rs.getTimestamp(8).toInstant()),user.id());
    }

    @PutMapping("/{id}") @Transactional
    void link(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id,@RequestBody Link link) {
        if(link.goalId()!=null) {
            if(jdbc.queryForList("SELECT id FROM savings_goal WHERE owner_id=? AND id=? FOR UPDATE",user.id(),link.goalId()).isEmpty())
                throw ApiException.missing();
            if(!jdbc.queryForList("SELECT id FROM bank_investment WHERE owner_id=? AND goal_id=? AND id<>?",user.id(),link.goalId(),id).isEmpty())
                throw ApiException.conflict("Esta meta já tem uma posição vinculada.");
        }
        if(jdbc.update("""
            UPDATE bank_investment SET goal_id=? WHERE owner_id=? AND id=? AND goal_id IS NOT DISTINCT FROM ?::uuid
            AND (?::uuid IS NULL OR (present AND status='ACTIVE'))
            """,link.goalId(),user.id(),id,link.previousGoalId(),link.goalId())!=1)
            throw ApiException.conflict("A posição mudou. Atualize antes de vincular.");
    }
}
