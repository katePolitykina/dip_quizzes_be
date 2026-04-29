package org.example.dip2.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.dip2.model.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    Optional<Quiz> findByIdAndAuthorId(UUID id, UUID authorId);

    List<Quiz> findAllByAuthorIdOrderByUpdatedAtDesc(UUID authorId);
}
