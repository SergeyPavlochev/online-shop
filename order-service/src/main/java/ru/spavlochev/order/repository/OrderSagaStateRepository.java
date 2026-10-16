package ru.spavlochev.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.spavlochev.order.entity.OrderSagaState;

import java.util.UUID;

public interface OrderSagaStateRepository extends JpaRepository<OrderSagaState, UUID> {
}
