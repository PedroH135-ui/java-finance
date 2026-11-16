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
    AtomicReference<String> ownerHeader=new AtomicReference<>(),directionHeader=new AtomicReference<>();
    String response="{\"categoryId\":\"food\",\"confidence\":\"MEDIUM\",\"method\":\"RULE\",\"reason\":\"untrusted reason\",\"evidenceCount\":0,\"modelUsed\":false}";
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/suggest",exchange->{
            calls.incrementAndGet();correlation.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
            ownerHeader.set(exchange.getRequestHeaders().getFirst("X-Local-Demo-Owner"));directionHeader.set(exchange.getRequestHeaders().getFirst("X-Local-Demo-Direction"));
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(responseStatus,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    CategoryAdvisor advisor(boolean enabled) {return new CategoryAdvisor(enabled,"http://127.0.0.1:"+server.getAddress().getPort(),"synthetic-token-at-least-32-characters",new ObjectMapper());}
    CategoryAdvisor.Request request(String text,List<CategoryAdvisor.Example> history) {
        return new CategoryAdvisor.Request(text,List.of(new CategoryAdvisor.Category("food","Alimentação")),history);
    }
    @Test void repeatedAiDecisionIsReusedAcrossAdvisorInstancesWithoutAnyHttpCall() {
        var prior=List.of(new CategoryAdvisor.Example("Restaurante Alfa 123","food"));
        for(int i=0;i<2;i++){
            var result=advisor(true).suggest(UUID.randomUUID(),"EXPENSE",request(" RESTAURANTE   ALFA 123 ",List.of()),prior);
            assertThat(result.method()).isEqualTo("MEMORY");assertThat(result.categoryId()).isEqualTo("food");
            assertThat(result.modelUsed()).isFalse();assertThat(result.confidence()).isEqualTo("LOW");
        }
        assertThat(calls.get()).isZero();
    }
    @Test void humanDecisionOverridesMemoAndConflictingMemosAbstain() {
        var categories=List.of(new CategoryAdvisor.Category("food","Alimentação"),new CategoryAdvisor.Category("other","Outros"));
        var prior=List.of(new CategoryAdvisor.Example("Restaurante Alfa","other"));
        var human=new CategoryAdvisor.Request("Restaurante Alfa",categories,List.of(new CategoryAdvisor.Example("Restaurante Alfa","food")));
        assertThat(advisor(true).suggest(UUID.randomUUID(),"EXPENSE",human,prior).method()).isEqualTo("HISTORY");
        assertThat(advisor(true).suggest(UUID.randomUUID(),"EXPENSE",human,prior).categoryId()).isEqualTo("food");
        var conflict=List.of(new CategoryAdvisor.Example("Restaurante Alfa","food"),new CategoryAdvisor.Example("Restaurante Alfa","other"));
        assertThat(advisor(true).suggest(UUID.randomUUID(),"EXPENSE",new CategoryAdvisor.Request("Restaurante Alfa",categories,List.of()),conflict).categoryId()).isNull();
        assertThat(calls.get()).isZero();
    }
    @Test void memoPreservesIdentifiersAndCannotReuseForeignCategoriesOrFinancialDescriptions() {
        var prior=List.of(new CategoryAdvisor.Example("Restaurante Alfa 456","food"));
        advisor(true).suggest(UUID.randomUUID(),"EXPENSE",request("Restaurante Alfa 123",List.of()),prior);
        assertThat(calls.get()).isEqualTo(1);
        advisor(true).suggest(UUID.randomUUID(),"EXPENSE",request("Restaurante Alfa 123",List.of()),List.of(new CategoryAdvisor.Example("Restaurante Alfa 123","foreign")));
        assertThat(calls.get()).isEqualTo(2);
        assertThat(advisor(true).suggest(UUID.randomUUID(),"EXPENSE",request("Transferencia Alfa",List.of()),List.of(new CategoryAdvisor.Example("Transferencia Alfa","food"))).categoryId()).isNull();
        assertThat(calls.get()).isEqualTo(2);
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
    CategoryAdvisor vectorAdvisor() {
        return new CategoryAdvisor(true,"http://127.0.0.1:"+server.getAddress().getPort(),"synthetic-token-at-least-32-characters",new ObjectMapper(),"","","development",true,"local");
    }
    String vectorResponse(String category,double similarity) {
        return "{\"categoryId\":\"food\",\"confidence\":\"LOW\",\"method\":\"LLM\",\"reason\":\"synthetic\",\"evidenceCount\":1,\"modelUsed\":true,\"retrieval\":{\"method\":\"PGVECTOR\",\"model\":\"synthetic-model\",\"dimensions\":384,\"examples\":[{\"description\":\"pedido de pizza entregue em casa\",\"categoryId\":\""+category+"\",\"similarity\":"+similarity+"}]}}";
    }
    @Test void vectorDemoForwardsBoundedHistoryAndAuthenticatedScopeOnlyToLoopback() {
        response=vectorResponse("food",0.92);UUID owner=UUID.randomUUID();
        var history=List.of(new CategoryAdvisor.Example("pedido de pizza entregue em casa","food"),new CategoryAdvisor.Example("remedios comprados na farmacia","food"));
        var result=vectorAdvisor().suggest(owner,"EXPENSE",request("pizza em domicilio",history));
        assertThat(ownerHeader.get()).isEqualTo(owner.toString());assertThat(directionHeader.get()).isEqualTo("EXPENSE");
        assertThat(body.get()).contains("remedios comprados na farmacia");
        assertThat(result.retrieval().dimensions()).isEqualTo(384);assertThat(result.retrieval().examples()).hasSize(1);
    }
    @Test void vectorDemoRejectsCloudProductionAndMissingAuthenticatedScope() {
        String token="synthetic-token-at-least-32-characters";
        assertThatThrownBy(()->new CategoryAdvisor(true,"http://127.0.0.1:8126",token,new ObjectMapper(),"","","development",true,"cloud")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CategoryAdvisor(true,"https://example.vercel.app",token,new ObjectMapper(),"example.vercel.app","","production",true,"local")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->vectorAdvisor().suggest(request("pizza",List.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls.get()).isZero();
    }
    @Test void normalModeDoesNotSendOwnerOrExposeVectorDetailsAndInvalidTraceIsDiscarded() {
        response=vectorResponse("food",0.92);
        assertThat(advisor(true).suggest(UUID.randomUUID(),"EXPENSE",request("pizza",List.of())).retrieval()).isNull();
        assertThat(ownerHeader.get()).isNull();assertThat(directionHeader.get()).isNull();
        response=vectorResponse("foreign",0.92);
        assertThat(vectorAdvisor().suggest(UUID.randomUUID(),"EXPENSE",request("pizza",List.of())).retrieval()).isNull();
    }
}
