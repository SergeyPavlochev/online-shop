package ru.spavlochev.order.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import ru.spavlochev.order.entity.OrderSagaState;

import java.util.List;
import java.util.UUID;

public interface OrderSagaStateRepository extends JpaRepository<OrderSagaState, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = AvailableSettings.JAKARTA_LOCK_TIMEOUT, value = "-2")) // -2 = SKIP LOCKED
    List<OrderSagaState> findAllByStatusOrderByUpdatedAtAsc(OrderSagaState.SagaStatus status, Pageable pageable);
}
