package ru.spavlochev.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.spavlochev.order.entity.IdempotencyKey;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    Optional<IdempotencyKey> findByKey(String key);

    boolean existsByKey(String key);
}
