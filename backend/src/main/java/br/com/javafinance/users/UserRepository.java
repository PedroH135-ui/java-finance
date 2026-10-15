package br.com.javafinance.users;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<AppUser,UUID> {
    Optional<AppUser> findByEmail(String email);
}

