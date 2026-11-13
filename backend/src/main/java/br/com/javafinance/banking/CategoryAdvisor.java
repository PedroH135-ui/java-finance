package br.com.javafinance.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

/** Optional Python advisor on loopback or an explicitly allowed HTTPS Vercel host. */
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
    private final String previewOidc;
    private volatile long retryAfterNanos;
    private static final Set<String> STOP=Set.of("compra","pagamento","cartao","credito","debito","pix","recebido","enviado","para","de","do","da","via");
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).proxy(new java.net.ProxySelector() {
        public java.util.List<java.net.Proxy> select(URI uri) { return List.of(java.net.Proxy.NO_PROXY); }
        public void connectFailed(URI uri,java.net.SocketAddress sa,java.io.IOException ioe) {}
    }).build();
    public CategoryAdvisor(boolean enabled,String url,String token,ObjectMapper mapper) {
        this(enabled,url,token,mapper,"","");
    }
    public CategoryAdvisor(@Value("${app.category-ai.enabled:false}") boolean enabled,
        @Value("${app.category-ai.url:http://127.0.0.1:8123}") String url,@Value("${app.category-ai.token:}") String token,ObjectMapper mapper,
        @Value("${app.category-ai.allowed-host:}") String allowedHost,@Value("${app.category-ai.preview-oidc:}") String previewOidc) {
        this(enabled,url,token,mapper,allowedHost,previewOidc,"development");
    }
    @Autowired
    public CategoryAdvisor(@Value("${app.category-ai.enabled:false}") boolean enabled,
        @Value("${app.category-ai.url:http://127.0.0.1:8123}") String url,@Value("${app.category-ai.token:}") String token,ObjectMapper mapper,
        @Value("${app.category-ai.allowed-host:}") String allowedHost,@Value("${app.category-ai.preview-oidc:}") String previewOidc,
        @Value("${VERCEL_ENV:development}") String runtimeEnvironment) {
        this.enabled=enabled; this.token=token; this.mapper=mapper;
        URI base=URI.create(url);
        boolean local="http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost());
        boolean remote="https".equals(base.getScheme()) && base.getHost()!=null && base.getHost().equals(allowedHost)
            && allowedHost.endsWith(".vercel.app") && (base.getPort()==-1 || base.getPort()==443);
        if(!(local || remote) || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null
            || !(base.getPath().isEmpty() || base.getPath().equals("/"))) throw new IllegalArgumentException("Category advisor requires loopback HTTP or the explicitly allowed HTTPS Vercel host.");
        if(enabled && "production".equals(runtimeEnvironment) && (!remote || token.length()<32 || !previewOidc.isBlank()))
            throw new IllegalArgumentException("Production AI requires authenticated HTTPS and cannot use a development OIDC token.");
        this.previewOidc=remote?previewOidc:"";
        endpoint=base.resolve("/suggest");
    }
    public static String sanitize(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT)
            .replaceAll("\\S+@\\S+"," ").replaceAll("https?://\\S+"," ").replaceAll("[^a-z ]"," ").replaceAll("\\s+"," ").strip();
    }
    private static Set<String> terms(String text) {
        var words=new HashSet<>(Arrays.asList(text.split(" +")));words.removeAll(STOP);words.remove("");return words;
    }
    static Request minimize(Request request) {
        record Hit(Example example,double score) {}
        var target=terms(request.description());
        var hits=request.history().stream().map(e->{
            var words=terms(e.description());var union=new HashSet<>(target);union.addAll(words);words.retainAll(target);
            return new Hit(e,union.isEmpty()?0:(double)words.size()/union.size());
        }).filter(h->h.score()>=0.45).sorted(Comparator.comparingDouble(Hit::score).reversed().thenComparing(h->h.example().categoryId()).thenComparing(h->h.example().description())).toList();
        var selected=new ArrayList<Example>();var strongCategories=new HashSet<String>();
        for(var hit:hits)if(hit.score()>=0.8 && strongCategories.add(hit.example().categoryId()) && selected.size()<5)selected.add(hit.example());
        for(var hit:hits)if(selected.size()<5 && !selected.contains(hit.example()))selected.add(hit.example());
        var relevant=selected.stream().map(Example::categoryId).collect(java.util.stream.Collectors.toSet());
        var categories=request.categories().stream().sorted(Comparator.<Category,Boolean>comparing(c->!relevant.contains(c.id()))
            .thenComparing(c->-terms(c.name()).stream().filter(target::contains).count()).thenComparing(Category::id)).limit(20).toList();
        var allowed=categories.stream().map(Category::id).collect(java.util.stream.Collectors.toSet());
        return new Request(request.description(),categories,selected.stream().filter(e->allowed.contains(e.categoryId())).toList());
    }
    public Suggestion suggest(Request raw) {
        var request=new Request(sanitize(raw.description()),raw.categories().stream().map(c->new Category(c.id(),sanitize(c.name()))).filter(c->!c.name().isBlank()).toList(),raw.history().stream().map(h->new Example(sanitize(h.description()),h.categoryId())).toList());
        var allowed=new HashSet<>(request.categories().stream().map(Category::id).toList());
        var words=terms(request.description());
        if(words.stream().anyMatch(t->Set.of("emprestimo","aplicacao","resgate","investimento","transferencia","estorno","fatura").contains(t)) || request.description().contains("mercado pago"))
            return new Suggestion(null,"NONE","NONE","Confira a natureza financeira antes de escolher uma categoria.",0,false);
        var exact=request.history().stream().filter(e->e.description().equals(request.description()) && allowed.contains(e.categoryId())).toList();
        var ids=exact.stream().map(Example::categoryId).distinct().toList();
        if(!request.description().isBlank() && ids.size()==1)return new Suggestion(ids.getFirst(),exact.size()>=3?"HIGH":"MEDIUM","HISTORY",reason("HISTORY"),Math.min(100,exact.size()),false);
        if(ids.size()>1)return new Suggestion(null,"NONE","NONE","Seu histórico contém classificações conflitantes; escolha uma categoria.",0,false);
        var outgoing=minimize(request);
        var sentIds=new HashSet<>(outgoing.categories().stream().map(Category::id).toList());
        if(enabled && token.length()>=32 && (retryAfterNanos==0 || System.nanoTime()-retryAfterNanos>=0)) {
            String correlation=UUID.randomUUID().toString();long start=System.nanoTime();int status=0;
            try {
                var builder=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").header("Authorization","Bearer "+token).header("X-Correlation-ID",correlation);
                if(!previewOidc.isBlank())builder.header("x-vercel-trusted-oidc-idp-token",previewOidc);
                var response=http.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(outgoing))).build(),HttpResponse.BodyHandlers.ofInputStream());
                status=response.statusCode();
                if(status!=200)retryAfterNanos=System.nanoTime()+Duration.ofSeconds(30).toNanos();
                try(var stream=response.body()) {
                    byte[] bytes=stream.readNBytes(8193);
                    if(response.statusCode()==200 && bytes.length<=8192) {
                        var result=mapper.readValue(bytes,Suggestion.class);
                        String provider=result.provider()==null?"none":result.provider();
                        if((result.categoryId()==null || sentIds.contains(result.categoryId())) && Set.of("HIGH","MEDIUM","LOW","NONE").contains(result.confidence())
                            && Set.of("HISTORY","RULE","LLM","NONE").contains(result.method()) && Set.of("none","ollama","groq").contains(provider) && result.evidenceCount()>=0 && result.evidenceCount()<=100)
                            return new Suggestion(result.categoryId(),result.confidence(),result.method(),reason(result.method()),result.evidenceCount(),result.modelUsed(),provider);
                    }
                }
            } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            catch(Exception ignored) {retryAfterNanos=System.nanoTime()+Duration.ofSeconds(30).toNanos();}
            finally {LoggerFactory.getLogger(CategoryAdvisor.class).info("category_ai_request correlation_id={} status={} duration_ms={}",correlation,status,(System.nanoTime()-start)/1_000_000);}
        }
        return new Suggestion(null,"NONE","NONE","Sem uma sugestão segura agora. Você pode escolher a categoria manualmente.",0,false);
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
