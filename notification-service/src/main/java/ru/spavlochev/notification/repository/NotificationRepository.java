package ru.spavlochev.notification.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.spavlochev.notification.entity.Notification;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
