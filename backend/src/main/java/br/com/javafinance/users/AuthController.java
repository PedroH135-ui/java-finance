package br.com.javafinance.users;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UserService users;
    private final String hosting;
    public AuthController(UserService users,@Value("${app.hosting:local}") String hosting) { this.users=users; this.hosting=hosting; }
    @GetMapping("/config") Map<String,Object> config() { return Map.of("registrationEnabled", users.isRegistrationEnabled(),"hosting",hosting); }
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @PostMapping("/register") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    UserService.Profile register(@Valid @RequestBody UserService.Registration input) { return users.register(input); }
    @GetMapping("/me") UserService.Profile me(@AuthenticationPrincipal UserPrincipal p) { return users.me(p.id()); }
    @PatchMapping("/me") UserService.Profile rename(@AuthenticationPrincipal UserPrincipal p, @Valid @RequestBody UserService.NameChange input) { return users.rename(p.id(),input); }
    @PostMapping("/password") void password(@AuthenticationPrincipal UserPrincipal p, @Valid @RequestBody UserService.PasswordChange input, HttpServletRequest request) {
        users.password(p.id(),input); request.getSession().invalidate();
    }
}

