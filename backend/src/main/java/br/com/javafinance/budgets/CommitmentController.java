package br.com.javafinance.budgets;

import br.com.javafinance.users.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/budgets/commitments")
public class CommitmentController {
    private final CommitmentService commitments;

    public CommitmentController(CommitmentService commitments) {
        this.commitments = commitments;
    }

    @GetMapping CommitmentService.Upcoming upcoming(@AuthenticationPrincipal UserPrincipal principal) {
        return commitments.upcoming(principal.id());
    }

    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    void create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CommitmentService.Create input) {
        commitments.create(principal.id(), input);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        commitments.remove(principal.id(), id);
    }
}
