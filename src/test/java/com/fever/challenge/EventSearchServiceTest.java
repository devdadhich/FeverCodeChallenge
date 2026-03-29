package com.fever.challenge;

import com.fever.challenge.dto.EventSummaryDto;
import com.fever.challenge.entity.EventEntity;
import com.fever.challenge.repository.EventRepository;
import com.fever.challenge.service.EventSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EventSearchServiceTest {

    @Autowired
    private EventSearchService searchService;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    @Test
    void shouldReturnDeterministicUuidsForSameEvent() {
        eventRepository.save(createEvent("100", "200", "Test Event",
                LocalDateTime.of(2021, 6, 1, 10, 0),
                LocalDateTime.of(2021, 6, 1, 12, 0)));

        List<EventSummaryDto> result1 = searchService.search(
                LocalDateTime.of(2021, 1, 1, 0, 0),
                LocalDateTime.of(2021, 12, 31, 23, 59));

        List<EventSummaryDto> result2 = searchService.search(
                LocalDateTime.of(2021, 1, 1, 0, 0),
                LocalDateTime.of(2021, 12, 31, 23, 59));

        assertEquals(1, result1.size());
        assertEquals(1, result2.size());
        assertEquals(result1.get(0).getId(), result2.get(0).getId());
    }

    @Test
    void shouldSplitDateAndTime() {
        eventRepository.save(createEvent("100", "200", "Test",
                LocalDateTime.of(2021, 6, 15, 20, 30),
                LocalDateTime.of(2021, 6, 15, 22, 0)));

        List<EventSummaryDto> results = searchService.search(
                LocalDateTime.of(2021, 6, 1, 0, 0),
                LocalDateTime.of(2021, 6, 30, 23, 59));

        assertEquals(1, results.size());
        var dto = results.get(0);
        assertEquals("2021-06-15", dto.getStartDate().toString());
        assertEquals("20:30", dto.getStartTime().toString());
        assertEquals("2021-06-15", dto.getEndDate().toString());
        assertEquals("22:00", dto.getEndTime().toString());
    }

    private EventEntity createEvent(String basePlanId, String planId, String title,
                                    LocalDateTime start, LocalDateTime end) {
        EventEntity event = new EventEntity();
        event.setBasePlanId(basePlanId);
        event.setPlanId(planId);
        event.setTitle(title);
        event.setPlanStartDate(start);
        event.setPlanEndDate(end);
        event.setMinPrice(new BigDecimal("10.00"));
        event.setMaxPrice(new BigDecimal("50.00"));
        event.setUpdatedAt(LocalDateTime.now());
        return event;
    }
}
