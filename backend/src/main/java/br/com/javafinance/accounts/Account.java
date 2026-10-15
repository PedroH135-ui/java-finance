package br.com.javafinance.accounts;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="account")
public class Account {
    public enum Kind { CHECKING, SAVINGS, CASH, WALLET, INVESTMENT, OTHER }
    @Id public UUID id;
    public UUID ownerId;
    public String name;
    public String institution;
    @Enumerated(EnumType.STRING) public Kind kind;
    public String currency="BRL";
    public LocalDate openingDate;
    public boolean archived;
    @Version public long version;
}

