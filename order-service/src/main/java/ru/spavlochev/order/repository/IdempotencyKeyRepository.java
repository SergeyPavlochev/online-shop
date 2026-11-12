package ru.spavlochev.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.spavlochev.order.entity.IdempotencyKey;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByKey(UUID key);

    /**
     * Атомарная вставка ключа с ON CONFLICT DO NOTHING.
     * Возвращает количество вставленных строк:
     *   1 — ключ новый, запись создана
     *   0 — ключ уже существует (конфликт)
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (key, user_id, order_id, status, created_at)
            VALUES (:key, :userId, '00000000-0000-0000-0000-000000000000', 'IN_PROGRESS', NOW())
            ON CONFLICT (key) DO NOTHING
            """, nativeQuery = true)
    int insertWithConflictCheck(@Param("key") UUID key, @Param("userId") UUID userId);
}
