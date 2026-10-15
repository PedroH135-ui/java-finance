package br.com.javafinance;

import br.com.javafinance.users.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.registration-enabled=true","server.servlet.session.cookie.secure=true"})
@AutoConfigureMockMvc @ActiveProfiles("cloud")
class CloudSessionIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        TestDatabase.configure(r);
    }
    @Autowired UserService users; @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate jdbc;
    @Test void sessionSurvivesRepositoryReplacementAndLogoutRevokesIt() throws Exception {
        var email=UUID.randomUUID()+"@example.test";
        users.register(new UserService.Registration(email,"Sessão de teste","UmaSenhaSegura123!"));
        var initial=mvc.perform(get("/api/auth/csrf").secure(true)).andExpect(status().isOk()).andReturn().getResponse();
        var csrf=mapper.readTree(initial.getContentAsString());
        var login=mvc.perform(post("/api/auth/login").secure(true).cookie(initial.getCookie("JSESSIONID"))
            .header(csrf.get("headerName").asText(),csrf.get("token").asText()).param("username",email).param("password","UmaSenhaSegura123!"))
            .andExpect(status().isNoContent()).andReturn().getResponse();
        Cookie cookie=login.getCookie("JSESSIONID");
        assertThat(cookie).isNotNull(); assertThat(cookie.isHttpOnly()).isTrue(); assertThat(cookie.getSecure()).isTrue();
        assertThat(login.getHeader("Set-Cookie")).contains("SameSite=Strict");
        String id=new String(Base64.getDecoder().decode(cookie.getValue()),StandardCharsets.UTF_8);
        var replacement=new JdbcIndexedSessionRepository(jdbc,new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())));
        org.springframework.session.Session persisted=replacement.findById(id);
        assertThat(persisted).isNotNull(); assertThat(persisted.<Object>getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();
        mvc.perform(get("/api/auth/me").secure(true).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        var next=mvc.perform(get("/api/auth/csrf").secure(true).cookie(cookie)).andExpect(status().isOk()).andReturn().getResponse();
        var nextCsrf=mapper.readTree(next.getContentAsString());
        mvc.perform(post("/api/auth/logout").secure(true).cookie(cookie).header(nextCsrf.get("headerName").asText(),nextCsrf.get("token").asText())).andExpect(status().isNoContent());
        assertThat(replacement.findById(id)).isNull();
        mvc.perform(get("/api/auth/me").secure(true).cookie(cookie)).andExpect(status().isUnauthorized());
    }
}
