package br.com.javafinance.movements;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface MovementRepository extends JpaRepository<Movement,UUID>, JpaSpecificationExecutor<Movement> {
    Optional<Movement> findByIdAndOwnerId(UUID id,UUID owner);
    Optional<Movement> findByOwnerIdAndRequestKey(UUID owner,UUID key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Movement m where m.id=:id and m.ownerId=:owner")
    Optional<Movement> lock(UUID id,UUID owner);
}

