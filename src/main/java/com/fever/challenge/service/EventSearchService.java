package com.fever.challenge.service;

import com.fever.challenge.dto.EventSummaryDto;
import com.fever.challenge.entity.EventEntity;
import com.fever.challenge.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for searching events from the local database.
 * All queries hit the DB only (no external calls), ensuring fast response times.
 */
@Service
public class EventSearchService {

    private final EventRepository eventRepository;

    public EventSearchService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<EventSummaryDto> search(LocalDateTime startsAt, LocalDateTime endsAt) {
        List<EventEntity> events = eventRepository.findByDateRange(startsAt, endsAt);

        return events.stream()
                .map(this::toDto)
                .toList();
    }

    private EventSummaryDto toDto(EventEntity entity) {
        EventSummaryDto dto = new EventSummaryDto();

        // Generate a deterministic UUID from basePlanId + planId
        // so the same event always has the same UUID across responses
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
     * Generates a stable UUID v5 (name-based) from the composite key.
     * This ensures the same event always maps to the same UUID.
     */
    private UUID generateUuid(String basePlanId, String planId) {
        String name = basePlanId + ":" + planId;
        return UUID.nameUUIDFromBytes(name.getBytes());
    }
}
