package br.com.javafinance.users;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@Profile("cloud")
@DependsOn("flywayInitializer")
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds=1800, cleanupCron="0 */15 * * * *")
public class CloudSessionConfig {
    @Bean
    DefaultCookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure}") boolean secure) {
        var cookie=new DefaultCookieSerializer();
        cookie.setCookieName("JSESSIONID");
        cookie.setCookiePath("/");
        cookie.setUseHttpOnlyCookie(true);
        cookie.setUseSecureCookie(secure);
        cookie.setSameSite("Strict");
        return cookie;
    }
}
