package br.com.javafinance.categories;
import br.com.javafinance.common.*;
import jakarta.validation.constraints.*;
import java.util.*;
import java.text.Normalizer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CategoryService {
    public record Create(@NotBlank @Size(max=80) String name,@NotNull Category.Kind kind,boolean essential) {}
    public record Edit(@NotBlank @Size(max=80) String name,boolean essential,boolean archived,long version) {}
    public record View(UUID id,String name,Category.Kind kind,boolean essential,boolean archived,long version) {}
    public record Defaults(int added,int existing) {}
    private static final Map<Category.Kind,List<String>> DEFAULTS=Map.of(
        Category.Kind.EXPENSE,List.of("Mercado","Restaurantes e delivery","Moradia","Transporte","Saúde","Educação","Assinaturas","Lazer","Compras","Outros gastos","PIX"),
        Category.Kind.INCOME,List.of("Salário","Trabalho extra","Rendimentos","Outras receitas","PIX"));
    private final CategoryRepository categories; private final AuditService audit; private final JdbcTemplate jdbc;
    public CategoryService(CategoryRepository categories,AuditService audit,JdbcTemplate jdbc) { this.categories=categories; this.audit=audit; this.jdbc=jdbc; }
    /** Serialized per owner; archived/customized categories are never modified or reactivated. */
    @Transactional public Defaults addDefaults(UUID owner) {
        if(jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",owner).isEmpty())throw ApiException.missing();
        Set<String> existing=new HashSet<>();
        for(Category c:categories.findByOwnerIdOrderByNameAsc(owner))existing.add(c.kind+":"+nameKey(c.name));
        int added=0,total=0;
        for(var group:DEFAULTS.entrySet())for(String name:group.getValue()){
            total++;
            if(existing.add(group.getKey()+":"+nameKey(name)))
                added+=jdbc.update("INSERT INTO category(id,owner_id,name,kind,essential,archived,version) VALUES (?,?,?,?,false,false,0) ON CONFLICT DO NOTHING",
                    UUID.randomUUID(),owner,name,group.getKey().name());
        }
        if(added>0)audit.record(owner,owner,"CATEGORY_DEFAULTS_ADDED","Categorias padrão adicionadas: "+added+".");
        return new Defaults(added,total-added);
    }
    private static String nameKey(String name) {
        return Normalizer.normalize(name,Normalizer.Form.NFD).replaceAll("\\p{M}","")
            .strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
    }
    public List<View> list(UUID owner) { return categories.findByOwnerIdOrderByNameAsc(owner).stream().map(this::view).toList(); }
    @Transactional public View create(UUID owner,Create input) {
        Category c=new Category(); c.id=UUID.randomUUID(); c.ownerId=owner; c.name=input.name().strip(); c.kind=input.kind();
        c.essential=input.kind()==Category.Kind.EXPENSE && input.essential();
        categories.saveAndFlush(c); audit.record(owner,c.id,"CATEGORY_CREATED","Categoria criada."); return view(c);
    }
    @Transactional public View edit(UUID owner,UUID id,Edit input) {
        Category c=categories.lock(id,owner).orElseThrow(ApiException::missing);
        if(c.version!=input.version()) throw ApiException.conflict("A categoria mudou. Atualize a página.");
        c.name=input.name().strip(); c.essential=c.kind==Category.Kind.EXPENSE && input.essential(); c.archived=input.archived();
        categories.flush(); audit.record(owner,id,"CATEGORY_UPDATED","Dados da categoria atualizados."); return view(c);
    }
    private View view(Category c) { return new View(c.id,c.name,c.kind,c.essential,c.archived,c.version); }
}

