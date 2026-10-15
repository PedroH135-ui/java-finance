package br.com.javafinance.categories;
import br.com.javafinance.common.*;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CategoryService {
    public record Create(@NotBlank @Size(max=80) String name,@NotNull Category.Kind kind,boolean essential) {}
    public record Edit(@NotBlank @Size(max=80) String name,boolean essential,boolean archived,long version) {}
    public record View(UUID id,String name,Category.Kind kind,boolean essential,boolean archived,long version) {}
    private final CategoryRepository categories; private final AuditService audit;
    public CategoryService(CategoryRepository categories,AuditService audit) { this.categories=categories; this.audit=audit; }
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

