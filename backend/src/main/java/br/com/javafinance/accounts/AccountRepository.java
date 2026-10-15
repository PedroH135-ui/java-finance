package br.com.javafinance.accounts;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface AccountRepository extends JpaRepository<Account,UUID> {
    List<Account> findByOwnerIdOrderByNameAsc(UUID owner);
    Optional<Account> findByIdAndOwnerId(UUID id, UUID owner);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id=:id and a.ownerId=:owner")
    Optional<Account> lock(UUID id, UUID owner);
}

