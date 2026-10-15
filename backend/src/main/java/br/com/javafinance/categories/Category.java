package br.com.javafinance.categories;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="category")
public class Category {
    public enum Kind { INCOME, EXPENSE }
    @Id public UUID id;
    public UUID ownerId;
    public String name;
    @Enumerated(EnumType.STRING) public Kind kind;
    public boolean essential;
    public boolean archived;
    @Version public long version;
}

