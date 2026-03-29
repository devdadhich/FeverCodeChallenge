package com.fever.challenge.service;

import com.fever.challenge.dto.EventSummaryDto;
import com.fever.challenge.entity.EventEntity;
import com.fever.challenge.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for searching events from the local database.
 * Results are cached in-memory (Caffeine) to handle high request throughput
 * without hitting the DB on every call.
 *
 * Cache is evicted after each sync cycle so clients always see fresh data.
 */
@Service
public class EventSearchService {

    private static final Logger log = LoggerFactory.getLogger(EventSearchService.class);
    private static final int MAX_RESULTS = 1000;

    private final EventRepository eventRepository;

    public EventSearchService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Searches events by date range. Results are cached by (startsAt, endsAt) key.
     *
     * Under 10k req/s with common date ranges, most requests will be served
     * from cache without touching the database at all.
     */
    @Cacheable(value = "events", key = "#startsAt.toString() + '_' + #endsAt.toString()")
    public List<EventSummaryDto> search(LocalDateTime startsAt, LocalDateTime endsAt) {
        List<EventEntity> events = eventRepository.findByDateRange(startsAt, endsAt);

        if (events.size() > MAX_RESULTS) {
            log.warn("Query returned {} results, truncating to {}", events.size(), MAX_RESULTS);
            events = events.subList(0, MAX_RESULTS);
        }

        return events.stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Evicts the entire search cache. Called by the sync service after
     * new data has been persisted to ensure freshness.
     */
    @CacheEvict(value = "events", allEntries = true)
    public void evictCache() {
        log.debug("Search cache evicted");
    }

    private EventSummaryDto toDto(EventEntity entity) {
        EventSummaryDto dto = new EventSummaryDto();

        dto.setId(generateUuid(entity.getBasePlanId(), entity.getPlanId()));
        dto.setTitle(entity.getTitle());

        if (entity.getPlanStartDate() != null) {
            dto.setStartDate(entity.getPlanStartDate().toLocalDate());
            dto.setStartTime(entity.getPlanStartDate().toLocalTime());
        }

        if (entity.getPlanEndDate() != null) {
            dto.setEndDate(entity.getPlanEndDate().toLocalDate());
            dto.setEndTime(entity.getPlanEndDate().toLocalTime());
        }

        dto.setMinPrice(entity.getMinPrice());
        dto.setMaxPrice(entity.getMaxPrice());

        return dto;
    }

    /**
     * Generates a stable UUID v3 (name-based, MD5) from the composite key.
     * This ensures the same event always maps to the same UUID.
     */
    private UUID generateUuid(String basePlanId, String planId) {
        String name = basePlanId + ":" + planId;
        return UUID.nameUUIDFromBytes(name.getBytes());
    }
}
