package ru.spavlochev.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_saga_states")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSagaState {

    @Id
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Setter
    private SagaStatus status;

    @Setter
    private boolean paymentReserved;

    @Setter
    private boolean stockReserved;

    @Setter
    private boolean deliveryReserved;

    private LocalDateTime startedAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        startedAt = LocalDateTime.now();
        updatedAt = startedAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum SagaStatus {
        STARTED,
        PAYMENT_RESERVED,
        STOCK_RESERVED,
        DELIVERY_RESERVED,
        CONFIRMED,
        COMPENSATING,
        CANCELLED,
        FAILED
    }
}
