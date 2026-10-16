package ru.spavlochev.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.spavlochev.delivery.entity.Courier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierRepository extends JpaRepository<Courier, UUID> {

    List<Courier> findByZone(String zone);

    @Query(value = """
            SELECT c.* FROM couriers c
            LEFT JOIN slot_reservations sr 
                ON sr.courier_id = c.id 
                AND sr.slot_id = :slotId 
                AND sr.status = 'RESERVED'
            WHERE c.zone = :zone
            GROUP BY c.id
            HAVING COUNT(sr.id) < c.max_orders_slot
            LIMIT 1
            """, nativeQuery = true)
    Optional<Courier> findFreeCourierForSlot(@Param("slotId") UUID slotId,
                                             @Param("zone") String zone);
}
