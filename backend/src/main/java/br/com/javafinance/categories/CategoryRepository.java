package br.com.javafinance.categories;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface CategoryRepository extends JpaRepository<Category,UUID> {
    List<Category> findByOwnerIdOrderByNameAsc(UUID owner);
    Optional<Category> findByIdAndOwnerId(UUID id,UUID owner);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.id=:id and c.ownerId=:owner")
    Optional<Category> lock(UUID id,UUID owner);
}

