package br.com.javafinance.users;
import br.com.javafinance.common.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class UserService {
    public record Registration(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(max=100) String name, @NotBlank @Size(min=10,max=64) String password) {}
    public record Profile(UUID id, String email, String name) {}
    public record NameChange(@NotBlank @Size(max=100) String name) {}
    public record PasswordChange(@NotBlank String currentPassword, @NotBlank @Size(min=10,max=64) String newPassword) {}
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final AuditService audit;
    private final boolean registrationEnabled;
    public boolean isRegistrationEnabled() { return registrationEnabled; }
    public UserService(UserRepository users, PasswordEncoder encoder, Clock clock, AuditService audit,
        @Value("${app.registration-enabled}") boolean enabled) {
        this.users=users; this.encoder=encoder; this.clock=clock; this.audit=audit; registrationEnabled=enabled;
    }
    @Transactional
    public Profile register(Registration input) {
        if (!registrationEnabled) throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,"Novos cadastros estão desabilitados.");
        validatePassword(input.password());
        AppUser user = users.saveAndFlush(new AppUser(UUID.randomUUID(), input.email().strip().toLowerCase(Locale.ROOT),
            input.name().strip(), encoder.encode(input.password()), clock.instant()));
        audit.record(user.id,user.id,"REGISTER","Conta criada.");
        return profile(user);
    }
    public Profile me(UUID id) { return profile(get(id)); }
    public AppUser get(UUID id) { return users.findById(id).orElseThrow(ApiException::missing); }
    @Transactional public Profile rename(UUID id, NameChange input) {
        AppUser user=get(id); user.displayName=input.name().strip(); audit.record(id,id,"PROFILE","Nome alterado."); return profile(user);
    }
    @Transactional public void password(UUID id, PasswordChange input) {
        AppUser user=get(id);
        if (!encoder.matches(input.currentPassword(), user.passwordHash)) throw ApiException.bad("Senha atual incorreta.");
        validatePassword(input.newPassword());
        user.passwordHash=encoder.encode(input.newPassword()); user.credentialVersion++;
        audit.record(id,id,"PASSWORD","Senha alterada; sessões anteriores invalidadas.");
    }
    private void validatePassword(String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw ApiException.bad("A senha deve ter no máximo 72 bytes em UTF-8.");
    }
    private Profile profile(AppUser u) { return new Profile(u.id,u.email,u.displayName); }
}

