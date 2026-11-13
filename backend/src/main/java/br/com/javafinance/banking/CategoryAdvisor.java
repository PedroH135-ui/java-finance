package br.com.javafinance.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Optional loopback-only Python advisor. No amounts, dates, account IDs or raw descriptions leave Java. */
@Component
public class CategoryAdvisor {
    public record Category(String id,String name) {}
    public record Example(String description,String categoryId) {}
    public record Request(String description,List<Category> categories,List<Example> history) {}
    public record Suggestion(String categoryId,String confidence,String method,String reason,int evidenceCount,boolean modelUsed,String provider) {
        public Suggestion(String categoryId,String confidence,String method,String reason,int evidenceCount,boolean modelUsed) {
            this(categoryId,confidence,method,reason,evidenceCount,modelUsed,"none");
        }
    }
    private final boolean enabled; private final URI endpoint; private final String token; private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).proxy(new java.net.ProxySelector() {
        public java.util.List<java.net.Proxy> select(URI uri) { return List.of(java.net.Proxy.NO_PROXY); }
        public void connectFailed(URI uri,java.net.SocketAddress sa,java.io.IOException ioe) {}
    }).build();
    public CategoryAdvisor(@Value("${app.category-ai.enabled:false}") boolean enabled,
        @Value("${app.category-ai.url:http://127.0.0.1:8123}") String url,@Value("${app.category-ai.token:}") String token,ObjectMapper mapper) {
        this.enabled=enabled; this.token=token; this.mapper=mapper;
        URI base=URI.create(url);
        if(!base.getScheme().equals("http") || !"127.0.0.1".equals(base.getHost()) || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null
            || !(base.getPath().isEmpty() || base.getPath().equals("/"))) throw new IllegalArgumentException("Category advisor must use loopback HTTP.");
        endpoint=base.resolve("/suggest");
    }
    public static String sanitize(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT)
            .replaceAll("\\S+@\\S+"," ").replaceAll("https?://\\S+"," ").replaceAll("[^a-z ]"," ").replaceAll("\\s+"," ").strip();
    }
    public Suggestion suggest(Request raw) {
        var request=new Request(sanitize(raw.description()),raw.categories(),raw.history().stream().map(h->new Example(sanitize(h.description()),h.categoryId())).toList());
        var allowed=new HashSet<>(request.categories().stream().map(Category::id).toList());
        if(enabled && token.length()>=32) {
            try {
                var response=http.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json")
                    .header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request))).build(),HttpResponse.BodyHandlers.ofInputStream());
                try(var stream=response.body()) {
                    byte[] bytes=stream.readNBytes(8193);
                    if(response.statusCode()==200 && bytes.length<=8192) {
                        var result=mapper.readValue(bytes,Suggestion.class);
                        String provider=result.provider()==null?"none":result.provider();
                        if((result.categoryId()==null || allowed.contains(result.categoryId())) && Set.of("HIGH","MEDIUM","LOW","NONE").contains(result.confidence())
                            && Set.of("HISTORY","RULE","LLM","NONE").contains(result.method()) && Set.of("none","ollama","groq").contains(provider) && result.evidenceCount()>=0 && result.evidenceCount()<=100)
                            return new Suggestion(result.categoryId(),result.confidence(),result.method(),reason(result.method()),result.evidenceCount(),result.modelUsed(),provider);
                    }
                }
            } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            catch(Exception ignored) { /* No payload, secrets or model responses in logs; use deterministic fallback. */ }
        }
        var terms=new HashSet<>(Arrays.asList(request.description().split(" +")));
        if(terms.stream().anyMatch(t->Set.of("emprestimo","aplicacao","resgate","investimento","transferencia","estorno","fatura").contains(t)) || request.description().contains("mercado pago"))
            return new Suggestion(null,"NONE","NONE","Confira a natureza financeira antes de escolher uma categoria.",0,false);
        var exact=request.history().stream().filter(e->e.description().equals(request.description()) && allowed.contains(e.categoryId())).toList();
        var ids=exact.stream().map(Example::categoryId).distinct().toList();
        if(!request.description().isBlank() && ids.size()==1) return new Suggestion(ids.getFirst(),exact.size()>=3?"HIGH":"MEDIUM","HISTORY",reason("HISTORY"),exact.size(),false);
        return new Suggestion(null,"NONE","NONE","Sem evidência suficiente. Escolha uma categoria; sua decisão poderá orientar as próximas sugestões.",0,false);
    }
    private static String reason(String method) {
        return switch(method) {
            case "HISTORY" -> "Sugestão baseada em classificações anteriores semelhantes deste perfil.";
            case "RULE" -> "Sugestão por descrição do estabelecimento; confirme se faz sentido.";
            case "LLM" -> "Sugestão de IA com contexto limitado; confirme antes de aplicar.";
            default -> "Sem evidência suficiente para sugerir uma categoria.";
        };
    }
}
