package com.fever.challenge.repository;

import com.fever.challenge.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    /**
     * Find events that overlap with the given date range.
     * An event overlaps if it starts before the range ends AND ends after the range starts.
     * This correctly captures partial overlaps, not just events fully contained in the range.
     */
    @Query("SELECT e FROM EventEntity e " +
           "WHERE e.planStartDate <= :endsAt " +
           "AND e.planEndDate >= :startsAt " +
           "ORDER BY e.planStartDate ASC")
    List<EventEntity> findByDateRange(
            @Param("startsAt") LocalDateTime startsAt,
            @Param("endsAt") LocalDateTime endsAt);

    /**
     * Find an existing event by its natural key (provider IDs).
     */
    Optional<EventEntity> findByBasePlanIdAndPlanId(String basePlanId, String planId);

    /**
     * Bulk-load all existing events for a set of composite keys.
     * Used during sync to avoid N+1 queries when upserting.
     */
    @Query("SELECT e FROM EventEntity e WHERE CONCAT(e.basePlanId, ':', e.planId) IN :keys")
    List<EventEntity> findByCompositeKeys(@Param("keys") List<String> keys);
}
