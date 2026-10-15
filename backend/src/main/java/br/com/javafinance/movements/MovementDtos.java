package br.com.javafinance.movements;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
public final class MovementDtos {
    public record Create(@NotNull UUID requestKey,@NotBlank @Size(max=200) String description,@NotBlank String amount,
        @NotNull Movement.Kind kind,@NotNull Movement.Status status,@NotNull UUID accountId,UUID destinationId,UUID categoryId,
        @NotNull LocalDate operationDate,@NotNull LocalDate competence,@NotNull LocalDate dueDate,LocalDate settledDate,
        @Size(max=1000) String notes) {}
    public record Change(long version,@NotBlank @Size(max=300) String reason,LocalDate settledDate) {}
    public record NoteChange(long version,@NotBlank @Size(max=200) String description,@Size(max=1000) String notes,
        @NotBlank @Size(max=300) String reason) {}
    public record View(UUID id,String description,BigDecimal amount,Movement.Kind kind,Movement.Status status,
        UUID accountId,UUID destinationId,UUID categoryId,LocalDate operationDate,LocalDate competence,LocalDate dueDate,
        LocalDate settledDate,String notes,String source,String externalReference,UUID batchId,Instant createdAt,long version) {
        public static View from(Movement m) { return new View(m.id,m.description,m.amount,m.kind,m.status,m.accountId,m.destinationId,m.categoryId,
            m.operationDate,m.competence,m.dueDate,m.settledDate,m.notes,m.source,m.externalReference,m.batchId,m.createdAt,m.version); }
    }
    public record Page(List<View> items,long total,int page,int size,int pages) {}
}

