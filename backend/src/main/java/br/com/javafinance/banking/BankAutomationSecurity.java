package br.com.javafinance.banking;

import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class BankAutomationSecurity {
    /** Machine endpoints validate independent secrets, never a user's session. Other routes keep CSRF. */
    @Bean @Order(1)
    SecurityFilterChain bankAutomationChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/banking/hooks/pluggy","/api/banking/jobs/daily")
            .csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.anyRequest().permitAll()).headers(h->h.cacheControl(c->{})).build();
    }
}
