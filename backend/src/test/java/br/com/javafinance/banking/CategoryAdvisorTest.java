package br.com.javafinance.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class CategoryAdvisorTest {
    HttpServer server; AtomicReference<String> body=new AtomicReference<>();
    String response="{\"categoryId\":\"food\",\"confidence\":\"MEDIUM\",\"method\":\"RULE\",\"reason\":\"untrusted reason\",\"evidenceCount\":0,\"modelUsed\":false}";
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/suggest",exchange->{
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
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
}
