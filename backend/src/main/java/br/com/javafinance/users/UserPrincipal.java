package br.com.javafinance.users;
import java.io.Serializable;
import java.util.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
public record UserPrincipal(UUID id, String password, long credentialVersion) implements UserDetails, Serializable {
    @Override public String getUsername() { return id.toString(); }
    @Override public String getPassword() { return password; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_USER")); }
}

