package com.fever.challenge.repository;

import com.fever.challenge.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    /**
     * Find events whose start date falls within the given range.
     * Both bounds are inclusive.
     */
    @Query("SELECT e FROM EventEntity e " +
           "WHERE e.planStartDate >= :startsAt " +
           "AND e.planEndDate <= :endsAt " +
           "ORDER BY e.planStartDate ASC")
    List<EventEntity> findByDateRange(
            @Param("startsAt") LocalDateTime startsAt,
            @Param("endsAt") LocalDateTime endsAt);

    /**
     * Find an existing event by its natural key (provider IDs).
     */
    Optional<EventEntity> findByBasePlanIdAndPlanId(String basePlanId, String planId);
}
