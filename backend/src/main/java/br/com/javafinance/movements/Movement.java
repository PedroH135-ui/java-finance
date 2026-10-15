package br.com.javafinance.movements;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(name="movement")
public class Movement {
    public enum Kind { OPENING, INCOME, EXPENSE, TRANSFER, ADJUSTMENT }
    public enum Status { PLANNED, CONFIRMED, CANCELLED }
    @Id public UUID id;
    public UUID ownerId;
    public String description;
    @Column(precision=19,scale=2) public BigDecimal amount;
    @Enumerated(EnumType.STRING) public Kind kind;
    @Enumerated(EnumType.STRING) public Status status;
    public UUID accountId;
    public UUID destinationId;
    public UUID categoryId;
    public LocalDate operationDate;
    public LocalDate competence;
    public LocalDate dueDate;
    public LocalDate settledDate;
    public String notes="";
    public String source="MANUAL";
    public String externalReference;
    public UUID batchId;
    public UUID requestKey;
    @Column(length=64) public String requestFingerprint;
    public Instant createdAt;
    @Version public long version;
}

