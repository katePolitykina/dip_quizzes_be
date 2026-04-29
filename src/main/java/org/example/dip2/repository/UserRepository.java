package org.example.dip2.repository;

import java.util.Optional;
import java.util.UUID;
import org.example.dip2.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
