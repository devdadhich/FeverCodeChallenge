package com.fever.challenge.service;

import com.fever.challenge.dto.EventSummaryDto;
import com.fever.challenge.entity.EventEntity;
import com.fever.challenge.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for searching events from the local database.
 *
 * Since H2 is an embedded in-process database and the dataset is small,
 * indexed range queries are already sub-millisecond. No caching layer
 * is needed -- it would add complexity without meaningful benefit.
 *
 * If the database were external (network hop) or the dataset large,
 * a Caffeine or Redis cache would be justified.
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
     * Searches events by date range, directly from the database.
     */
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
