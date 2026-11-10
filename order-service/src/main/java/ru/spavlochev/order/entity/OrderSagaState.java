package ru.spavlochev.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    @Version
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Setter
    private SagaStatus status;

    @Setter
    private boolean paymentReserved;

    @Setter
    private boolean stockReserved;

    @Setter
    private boolean deliveryReserved;

    @Setter
    private boolean pickingCompleted;

    @Column(length = 100)
    @Setter
    private String trackingNumber;

    @Setter
    private UUID activeCourierId;

    @Column(length = 500)
    @Setter
    private String failureReason;

    @Setter
    private LocalDateTime startedAt;

    @Setter
    private LocalDateTime pickingStartedAt;

    @Setter
    private LocalDateTime deliveredAt;

    @Setter
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
        PICKING,
        PICKING_FAILED,
        READY_FOR_DELIVERY,
        IN_DELIVERY,
        DELIVERY_FAILED,
        DELIVERED,
        COMPENSATING,
        CANCELLED,
        FAILED
    }
}
