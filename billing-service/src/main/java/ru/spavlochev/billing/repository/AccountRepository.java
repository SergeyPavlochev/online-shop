package ru.spavlochev.billing.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.spavlochev.billing.entity.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserId(UUID userId);

    // Pessimistic write lock для списания средств
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.userId = :userId")
    Optional<Account> findByUserIdForUpdate(@Param("userId") UUID userId);
}
