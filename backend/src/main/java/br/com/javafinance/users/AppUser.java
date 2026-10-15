package br.com.javafinance.users;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="app_user")
public class AppUser {
    @Id public UUID id;
    public String email;
    public String displayName;
    public String passwordHash;
    public long credentialVersion;
    public Instant createdAt;
    protected AppUser() {}
    public AppUser(UUID id, String email, String name, String hash, Instant now) {
        this.id=id; this.email=email; displayName=name; passwordHash=hash; createdAt=now;
    }
}

