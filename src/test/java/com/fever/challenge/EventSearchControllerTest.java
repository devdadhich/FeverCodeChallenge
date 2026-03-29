package com.fever.challenge;

import com.fever.challenge.dto.ApiResponse;
import com.fever.challenge.dto.EventListDto;
import com.fever.challenge.entity.EventEntity;
import com.fever.challenge.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EventSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();

        // Seed test data
        eventRepository.save(createEvent("291", "291",
                "Camela en concierto",
                LocalDateTime.of(2021, 6, 30, 21, 0),
                LocalDateTime.of(2021, 6, 30, 22, 0),
                new BigDecimal("15.00"), new BigDecimal("30.00")));

        eventRepository.save(createEvent("322", "1642",
                "Pantomima Full",
                LocalDateTime.of(2021, 2, 10, 20, 0),
                LocalDateTime.of(2021, 2, 10, 21, 30),
                new BigDecimal("55.00"), new BigDecimal("55.00")));

        eventRepository.save(createEvent("1591", "1642",
                "Los Morancos",
                LocalDateTime.of(2021, 7, 31, 20, 0),
                LocalDateTime.of(2021, 7, 31, 21, 0),
                new BigDecimal("65.00"), new BigDecimal("75.00")));
    }

    @Test
    void shouldReturnEventsWithinDateRange() throws Exception {
        mockMvc.perform(get("/search")
                        .param("starts_at", "2021-06-01T00:00:00")
                        .param("ends_at", "2021-07-01T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events", hasSize(1)))
                .andExpect(jsonPath("$.data.events[0].title").value("Camela en concierto"))
                .andExpect(jsonPath("$.error").value(nullValue()));
    }

    @Test
    void shouldReturnAllEventsForWideRange() throws Exception {
        mockMvc.perform(get("/search")
                        .param("starts_at", "2020-01-01T00:00:00")
                        .param("ends_at", "2022-12-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events", hasSize(3)));
    }

    @Test
    void shouldReturnEmptyForNoMatchingRange() throws Exception {
        mockMvc.perform(get("/search")
                        .param("starts_at", "2025-01-01T00:00:00")
                        .param("ends_at", "2025-12-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events", hasSize(0)));
    }

    @Test
    void shouldReturnBadRequestWhenStartsAtAfterEndsAt() throws Exception {
        mockMvc.perform(get("/search")
                        .param("starts_at", "2022-01-01T00:00:00")
                        .param("ends_at", "2021-01-01T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void shouldReturnBadRequestWhenOnlyOneParamProvided() throws Exception {
        mockMvc.perform(get("/search")
                        .param("starts_at", "2021-01-01T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void shouldReturnAllEventsWhenNoParamsProvided() throws Exception {
        mockMvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events", hasSize(3)));
    }

    @Test
    void shouldReturnCorrectEventStructure() throws Exception {
        mockMvc.perform(get("/search")
                        .param("starts_at", "2021-06-01T00:00:00")
                        .param("ends_at", "2021-07-01T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].id").isNotEmpty())
                .andExpect(jsonPath("$.data.events[0].title").value("Camela en concierto"))
                .andExpect(jsonPath("$.data.events[0].start_date").value("2021-06-30"))
                .andExpect(jsonPath("$.data.events[0].start_time").value("21:00:00"))
                .andExpect(jsonPath("$.data.events[0].end_date").value("2021-06-30"))
                .andExpect(jsonPath("$.data.events[0].end_time").value("22:00:00"))
                .andExpect(jsonPath("$.data.events[0].min_price").value(15.00))
                .andExpect(jsonPath("$.data.events[0].max_price").value(30.00));
    }

    private EventEntity createEvent(String basePlanId, String planId, String title,
                                    LocalDateTime start, LocalDateTime end,
                                    BigDecimal minPrice, BigDecimal maxPrice) {
        EventEntity event = new EventEntity();
        event.setBasePlanId(basePlanId);
        event.setPlanId(planId);
        event.setTitle(title);
        event.setPlanStartDate(start);
        event.setPlanEndDate(end);
        event.setMinPrice(minPrice);
        event.setMaxPrice(maxPrice);
        event.setUpdatedAt(LocalDateTime.now());
        return event;
    }
}
