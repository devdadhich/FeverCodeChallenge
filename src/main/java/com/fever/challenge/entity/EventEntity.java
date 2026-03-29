package com.fever.challenge.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted event entity. Each row represents a unique plan from a base_plan,
 * identified by a composite key of (basePlanId, planId).
 *
 * We store the latest snapshot of each event. Once stored, events are never
 * deleted (so past events remain searchable even when the provider stops
 * returning them).
 */
@Entity
@Table(
    name = "events",
    uniqueConstraints = @UniqueConstraint(columnNames = {"base_plan_id", "plan_id"}),
    indexes = {
        @Index(name = "idx_event_dates", columnList = "plan_start_date, plan_end_date"),
        @Index(name = "idx_event_natural_key", columnList = "base_plan_id, plan_id")
    }
)
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_plan_id", nullable = false)
    private String basePlanId;

    @Column(name = "plan_id", nullable = false)
    private String planId;

    @Column(nullable = false)
    private String title;

    @Column(name = "plan_start_date", nullable = false)
    private LocalDateTime planStartDate;

    @Column(name = "plan_end_date")
    private LocalDateTime planEndDate;

    @Column(name = "min_price")
    private BigDecimal minPrice;

    @Column(name = "max_price")
    private BigDecimal maxPrice;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // -- Getters and Setters --

    public Long getId() { return id; }

    public String getBasePlanId() { return basePlanId; }
    public void setBasePlanId(String basePlanId) { this.basePlanId = basePlanId; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDateTime getPlanStartDate() { return planStartDate; }
    public void setPlanStartDate(LocalDateTime planStartDate) { this.planStartDate = planStartDate; }

    public LocalDateTime getPlanEndDate() { return planEndDate; }
    public void setPlanEndDate(LocalDateTime planEndDate) { this.planEndDate = planEndDate; }

    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }

    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
