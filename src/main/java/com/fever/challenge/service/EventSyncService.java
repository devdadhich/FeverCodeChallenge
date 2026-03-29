package com.fever.challenge.service;

import com.fever.challenge.client.ProviderClientPort;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Periodically syncs events from the external provider into our database.
 *
 * Key design decisions:
 * - Only "online" sell_mode events are stored (as per requirements).
 * - Events are upserted in batches to minimize DB round-trips.
 * - Events are never deleted, so past events remain searchable.
 */
@Service
public class EventSyncService {

    private static final Logger log = LoggerFactory.getLogger(EventSyncService.class);
    private static final String SELL_MODE_ONLINE = "online";

    private final ProviderClientPort providerClient;
    private final EventRepository eventRepository;
    private final EventSearchService eventSearchService;

    public EventSyncService(ProviderClientPort providerClient,
                            EventRepository eventRepository,
                            EventSearchService eventSearchService) {
        this.providerClient = providerClient;
        this.eventRepository = eventRepository;
        this.eventSearchService = eventSearchService;
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

        // 1. Collect all online events from the XML into a flat list of pending upserts
        List<PendingEvent> pendingEvents = collectOnlineEvents(planList);
        if (pendingEvents.isEmpty()) {
            log.info("Sync complete: no online events found");
            return;
        }

        // 2. Bulk-load existing records by composite key to avoid N+1 queries
        List<String> compositeKeys = pendingEvents.stream()
                .map(PendingEvent::compositeKey)
                .toList();

        Map<String, EventEntity> existingByKey = eventRepository.findByCompositeKeys(compositeKeys)
                .stream()
                .collect(Collectors.toMap(
                        e -> e.getBasePlanId() + ":" + e.getPlanId(),
                        e -> e));

        // 3. Merge: update existing entities, create new ones
        List<EventEntity> toSave = new ArrayList<>();
        int created = 0;
        int updated = 0;

        for (PendingEvent pending : pendingEvents) {
            try {
                EventEntity entity = existingByKey.get(pending.compositeKey());
                boolean isNew = (entity == null);
                if (isNew) {
                    entity = new EventEntity();
                    created++;
                } else {
                    updated++;
                }

                entity.setBasePlanId(pending.basePlanId);
                entity.setPlanId(pending.planId);
                entity.setTitle(pending.title);
                entity.setPlanStartDate(parseDateTime(pending.startDate));
                entity.setPlanEndDate(parseDateTime(pending.endDate));
                entity.setMinPrice(pending.minPrice);
                entity.setMaxPrice(pending.maxPrice);
                entity.setUpdatedAt(LocalDateTime.now());

                toSave.add(entity);
            } catch (Exception e) {
                log.error("Failed to process plan {} for base_plan {}: {}",
                        pending.planId, pending.basePlanId, e.getMessage());
            }
        }

        // 4. Batch save all entities
        eventRepository.saveAll(toSave);

        // 5. Invalidate search cache since data has changed
        eventSearchService.evictCache();

        log.info("Sync complete: {} created, {} updated (batch size: {})",
                created, updated, toSave.size());
    }

    /**
     * Collects all online events from the parsed XML into a flat list,
     * computing min/max prices from zones during collection.
     */
    private List<PendingEvent> collectOnlineEvents(PlanList planList) {
        List<PendingEvent> result = new ArrayList<>();

        for (BasePlan basePlan : planList.getOutput().getBasePlans()) {
            if (!SELL_MODE_ONLINE.equalsIgnoreCase(basePlan.getSellMode())) {
                continue;
            }
            if (basePlan.getPlans() == null) {
                continue;
            }

            for (Plan plan : basePlan.getPlans()) {
                BigDecimal minPrice = null;
                BigDecimal maxPrice = null;

                if (plan.getZones() != null) {
                    for (Zone zone : plan.getZones()) {
                        BigDecimal price = new BigDecimal(zone.getPrice());
                        if (minPrice == null || price.compareTo(minPrice) < 0) minPrice = price;
                        if (maxPrice == null || price.compareTo(maxPrice) > 0) maxPrice = price;
                    }
                }

                result.add(new PendingEvent(
                        basePlan.getBasePlanId(),
                        plan.getPlanId(),
                        basePlan.getTitle(),
                        plan.getPlanStartDate(),
                        plan.getPlanEndDate(),
                        minPrice,
                        maxPrice));
            }
        }

        return result;
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

    /**
     * Internal record to hold parsed event data before persisting.
     */
    private record PendingEvent(
            String basePlanId, String planId, String title,
            String startDate, String endDate,
            BigDecimal minPrice, BigDecimal maxPrice) {

        String compositeKey() {
            return basePlanId + ":" + planId;
        }
    }
}
