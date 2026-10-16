package ru.spavlochev.billing.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import ru.spavlochev.billing.entity.PaymentHold;

import java.util.Optional;
import java.util.UUID;

public interface PaymentHoldRepository extends JpaRepository<PaymentHold, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentHold> findByOrderId(UUID orderId);
}
