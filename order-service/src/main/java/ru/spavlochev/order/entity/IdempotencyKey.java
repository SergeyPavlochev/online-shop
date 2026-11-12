package ru.spavlochev.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys",
       uniqueConstraints = @UniqueConstraint(columnNames = {"key", "user_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id
    private UUID key;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    @Builder.Default
    private UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000000"); // Default value for new keys

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> responseBody;

    @Column
    private Integer responseStatusCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum Status {
        IN_PROGRESS,
        SUCCESS,
        FAILED
    }
}
