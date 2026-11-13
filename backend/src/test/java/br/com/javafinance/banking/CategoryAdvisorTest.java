package br.com.javafinance.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class CategoryAdvisorTest {
    HttpServer server; AtomicReference<String> body=new AtomicReference<>();
    AtomicReference<String> correlation=new AtomicReference<>(); AtomicInteger calls=new AtomicInteger(); int responseStatus=200;
    String response="{\"categoryId\":\"food\",\"confidence\":\"MEDIUM\",\"method\":\"RULE\",\"reason\":\"untrusted reason\",\"evidenceCount\":0,\"modelUsed\":false}";
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/suggest",exchange->{
            calls.incrementAndGet();correlation.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(responseStatus,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    CategoryAdvisor advisor(boolean enabled) {return new CategoryAdvisor(enabled,"http://127.0.0.1:"+server.getAddress().getPort(),"synthetic-token-at-least-32-characters",new ObjectMapper());}
    CategoryAdvisor.Request request(String text,List<CategoryAdvisor.Example> history) {
        return new CategoryAdvisor.Request(text,List.of(new CategoryAdvisor.Category("food","Alimentação")),history);
    }
    @Test void localCallRedactsIdentifiersAndDoesNotTrustModelReason() {
        var result=advisor(true).suggest(request("Mercádo 123 CPF 999.888 email@example.test",List.of()));
        assertThat(result.categoryId()).isEqualTo("food"); assertThat(result.reason()).doesNotContain("untrusted");
        assertThat(body.get()).doesNotContain("123","999","@","amount","accountId","synthetic-token");
    }
    @Test void invalidCategoryAndMalformedResponseFallBackWithoutLeakingResponse() {
        response=response.replace("food","foreign");
        var history=List.of(new CategoryAdvisor.Example("Mercado Alfa","food"));
        assertThat(advisor(true).suggest(request("Mercado Alfa",history)).method()).isEqualTo("HISTORY");
        response="{private-response";
        assertThat(advisor(true).suggest(request("unknown",List.of())).categoryId()).isNull();
    }
    @Test void disabledUsesOnlyUnambiguousOwnHistoryAndFinancialTermsAbstain() {
        var a=advisor(false);
        assertThat(a.suggest(request("Mercado Alfa",List.of(new CategoryAdvisor.Example("Mercado Alfa","food")))).categoryId()).isEqualTo("food");
        assertThat(a.suggest(request("Transferencia mercado",List.of(new CategoryAdvisor.Example("Transferencia mercado","food")))).categoryId()).isNull();
        assertThat(body.get()).isNull();
    }
    @Test void remoteUrlsAreRejectedBeforeAnyRequest() {
        assertThatThrownBy(()->new CategoryAdvisor(true,"https://example.test","",new ObjectMapper())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void remoteHostRequiresHttpsAndAnExactExplicitAllowlist() {
        String host="isolated-preview.vercel.app";
        assertThatCode(()->new CategoryAdvisor(true,"https://"+host,"synthetic-token-at-least-32-characters",new ObjectMapper(),host,"synthetic-oidc")).doesNotThrowAnyException();
        for(String url:List.of("http://"+host,"https://other.vercel.app","https://"+host+":8443","https://"+host+"/other","https://user@"+host,"https://"+host+"?token=bad","https://"+host+"#bad")) {
            assertThatThrownBy(()->new CategoryAdvisor(true,url,"synthetic-token-at-least-32-characters",new ObjectMapper(),host,"synthetic-oidc")).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void cloudProviderIsPreservedAndUnknownProvidersFallBack() {
        response="{\"categoryId\":\"food\",\"confidence\":\"LOW\",\"method\":\"LLM\",\"reason\":\"untrusted\",\"evidenceCount\":0,\"modelUsed\":true,\"provider\":\"groq\"}";
        var result=advisor(true).suggest(request("Restaurante Sabor da Vila",List.of()));
        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.reason()).contains("IA").doesNotContain("modelo local","untrusted");
        response=response.replace("groq","unknown-provider");
        assertThat(advisor(true).suggest(request("Loja",List.of())).categoryId()).isNull();
    }
    @Test void productionRequiresStableAuthenticatedHttpsWithoutDevOidc() {
        String host="java-finance-category-ai-lab.vercel.app",secret="synthetic-service-token-at-least-32-characters";
        assertThatCode(()->new CategoryAdvisor(true,"https://"+host,secret,new ObjectMapper(),host,"","production")).doesNotThrowAnyException();
        assertThatThrownBy(()->new CategoryAdvisor(true,"https://"+host,secret,new ObjectMapper(),host,"dev-oidc","production")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CategoryAdvisor(true,"http://127.0.0.1:8123",secret,new ObjectMapper(),"","","production")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CategoryAdvisor(true,"https://"+host,"short",new ObjectMapper(),host,"","production")).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(()->new CategoryAdvisor(false,"http://127.0.0.1:8123","",new ObjectMapper(),"","","production")).doesNotThrowAnyException();
    }
    @Test void onlyRelevantBoundedContextLeavesJavaAndConflictsArePreserved() {
        var categories=new ArrayList<CategoryAdvisor.Category>();
        for(int i=0;i<100;i++)categories.add(new CategoryAdvisor.Category("c"+i,"tipo"+i));
        var history=new ArrayList<CategoryAdvisor.Example>();
        for(int i=0;i<100;i++)history.add(new CategoryAdvisor.Example("cantina bela jantar",i==99?"c99":"c0"));
        var request=CategoryAdvisor.minimize(new CategoryAdvisor.Request("cantina bela almoco",categories,history));
        assertThat(request.categories()).hasSize(20);assertThat(request.history().size()).isLessThanOrEqualTo(5);
        assertThat(request.history()).extracting(CategoryAdvisor.Example::categoryId).contains("c0","c99");
        assertThat(CategoryAdvisor.minimize(new CategoryAdvisor.Request("outro estabelecimento",categories,history)).history()).isEmpty();
    }
    @Test void outageReturnsNoSuggestionAndCooldownPreservesLocalHistory() {
        responseStatus=503;var a=advisor(true);
        assertThat(a.suggest(request("Restaurante fictício",List.of())).categoryId()).isNull();
        assertThat(a.suggest(request("Outro estabelecimento",List.of())).categoryId()).isNull();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(UUID.fromString(correlation.get())).isNotNull();
        assertThat(a.suggest(request("Mercado Alfa",List.of(new CategoryAdvisor.Example("Mercado Alfa","food")))).method()).isEqualTo("HISTORY");
        assertThat(calls.get()).isEqualTo(1);
    }
}
