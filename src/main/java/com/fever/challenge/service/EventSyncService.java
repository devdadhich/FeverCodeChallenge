package com.fever.challenge.service;

import com.fever.challenge.client.ProviderClient;
import com.fever.challenge.client.ProviderXmlModels.*;
import com.fever.challenge.entity.EventEntity;
import com.fever.challenge.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Periodically syncs events from the external provider into our database.
 *
 * Key design decisions:
 * - Only "online" sell_mode events are stored (as per requirements).
 * - Events are upserted: new ones are created, existing ones are updated.
 * - Events are never deleted, so past events remain searchable.
 */
@Service
public class EventSyncService {

    private static final Logger log = LoggerFactory.getLogger(EventSyncService.class);
    private static final String SELL_MODE_ONLINE = "online";

    private final ProviderClient providerClient;
    private final EventRepository eventRepository;

    public EventSyncService(ProviderClient providerClient, EventRepository eventRepository) {
        this.providerClient = providerClient;
        this.eventRepository = eventRepository;
    }

    /**
     * Scheduled task that fetches events from the provider and upserts them.
     * Runs on a fixed delay to avoid overlapping executions.
     */
    @Scheduled(fixedDelayString = "${provider.sync-interval-ms}", initialDelay = 0)
    @Transactional
    public void syncEvents() {
        log.info("Starting event sync from provider...");

        Optional<PlanList> maybePlanList = providerClient.fetchEvents();
        if (maybePlanList.isEmpty()) {
            log.warn("Sync skipped: could not fetch events from provider");
            return;
        }

        PlanList planList = maybePlanList.get();
        if (planList.getOutput() == null || planList.getOutput().getBasePlans() == null) {
            log.warn("Sync skipped: provider returned empty data");
            return;
        }

        int created = 0;
        int updated = 0;

        for (BasePlan basePlan : planList.getOutput().getBasePlans()) {
            if (!SELL_MODE_ONLINE.equalsIgnoreCase(basePlan.getSellMode())) {
                continue; // Skip non-online events
            }

            if (basePlan.getPlans() == null) {
                continue;
            }

            for (Plan plan : basePlan.getPlans()) {
                try {
                    boolean isNew = upsertEvent(basePlan, plan);
                    if (isNew) created++;
                    else updated++;
                } catch (Exception e) {
                    log.error("Failed to process plan {} for base_plan {}: {}",
                            plan.getPlanId(), basePlan.getBasePlanId(), e.getMessage());
                }
            }
        }

        log.info("Sync complete: {} created, {} updated", created, updated);
    }

    /**
     * Upserts a single event. Returns true if a new record was created.
     */
    private boolean upsertEvent(BasePlan basePlan, Plan plan) {
        String basePlanId = basePlan.getBasePlanId();
        String planId = plan.getPlanId();

        Optional<EventEntity> existing = eventRepository.findByBasePlanIdAndPlanId(basePlanId, planId);

        EventEntity entity = existing.orElseGet(EventEntity::new);
        boolean isNew = existing.isEmpty();

        entity.setBasePlanId(basePlanId);
        entity.setPlanId(planId);
        entity.setTitle(basePlan.getTitle());
        entity.setPlanStartDate(parseDateTime(plan.getPlanStartDate()));
        entity.setPlanEndDate(parseDateTime(plan.getPlanEndDate()));
        entity.setUpdatedAt(LocalDateTime.now());

        // Compute min/max prices from zones
        if (plan.getZones() != null && !plan.getZones().isEmpty()) {
            BigDecimal minPrice = null;
            BigDecimal maxPrice = null;

            for (Zone zone : plan.getZones()) {
                BigDecimal price = new BigDecimal(zone.getPrice());
                if (minPrice == null || price.compareTo(minPrice) < 0) {
                    minPrice = price;
                }
                if (maxPrice == null || price.compareTo(maxPrice) > 0) {
                    maxPrice = price;
                }
            }
            entity.setMinPrice(minPrice);
            entity.setMaxPrice(maxPrice);
        }

        eventRepository.save(entity);
        return isNew;
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse date '{}': {}", dateTimeStr, e.getMessage());
            return null;
        }
    }
}
