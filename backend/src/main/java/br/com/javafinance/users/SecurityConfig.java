package br.com.javafinance.users;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.github.benmanes.caffeine.cache.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;
@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
    @Bean UserDetailsService userDetails(UserRepository users) {
        return email -> users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
            .map(u -> new UserPrincipal(u.id,u.passwordHash,u.credentialVersion))
            .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));
    }
    @Bean SecurityFilterChain chain(HttpSecurity http, UserRepository users, @Value("${app.allowed-origin}") String origin) throws Exception {
        http.cors(c -> c.configurationSource(cors(origin)))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/health","/api/auth/csrf","/api/auth/config","/api/auth/register","/api/auth/login").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .formLogin(f -> f.loginProcessingUrl("/api/auth/login")
                .successHandler((req,res,auth) -> res.setStatus(204))
                .failureHandler((req,res,ex) -> json(res,401,"E-mail ou senha inválidos.")))
            .logout(l -> l.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> json(res,401,"Entre na sua conta para continuar."))
                .accessDeniedHandler((req,res,ex) -> json(res,403,"Acesso negado ou sessão expirada. Atualize a página.")))
            .headers(h -> h.cacheControl(c -> {}).contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; worker-src 'self'; manifest-src 'self'; frame-ancestors 'none'")))
            .addFilterBefore(new RateLimit(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new CredentialCheck(users), AuthorizationFilter.class);
        return http.build();
    }
    private CorsConfigurationSource cors(String origin) {
        CorsConfiguration c=new CorsConfiguration();
        c.setAllowedOrigins(List.of(origin)); c.setAllowedMethods(List.of("GET","POST","PATCH","PUT","DELETE"));
        c.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN")); c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/api/**",c); return source;
    }
    static void json(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\""+message+"\",\"fields\":{}}");
    }
    static class RateLimit extends OncePerRequestFilter {
        private final Cache<String,AtomicInteger> login=Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofMinutes(5)).build();
        private final Cache<String,AtomicInteger> registration=Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofHours(1)).build();
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
            String path=req.getServletPath();
            if ("POST".equals(req.getMethod()) && (path.equals("/api/auth/login") || path.equals("/api/auth/register"))) {
                boolean signup=path.endsWith("register"); Cache<String,AtomicInteger> cache=signup?registration:login;
                if(cache.get(req.getRemoteAddr(), k -> new AtomicInteger()).incrementAndGet() > (signup?5:20)) {
                    res.setHeader("Retry-After",signup?"3600":"300"); json(res,429,"Muitas tentativas. Aguarde antes de tentar novamente."); return;
                }
            }
            chain.doFilter(req,res);
        }
    }
    static class CredentialCheck extends OncePerRequestFilter {
        private final UserRepository users;
        CredentialCheck(UserRepository users) { this.users=users; }
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
            var auth=SecurityContextHolder.getContext().getAuthentication();
            if(auth!=null && auth.getPrincipal() instanceof UserPrincipal p &&
                users.findById(p.id()).map(u -> u.credentialVersion != p.credentialVersion()).orElse(true)) {
                if(req.getSession(false)!=null) req.getSession(false).invalidate();
                SecurityContextHolder.clearContext(); json(res,401,"Sessão encerrada. Entre novamente."); return;
            }
            chain.doFilter(req,res);
        }
    }
}

