package com.fever.challenge.controller;

import com.fever.challenge.dto.ApiResponse;
import com.fever.challenge.dto.EventListDto;
import com.fever.challenge.dto.EventSummaryDto;
import com.fever.challenge.service.EventSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/search")
public class EventSearchController {

    private static final Logger log = LoggerFactory.getLogger(EventSearchController.class);

    private final EventSearchService searchService;

    public EventSearchController(EventSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<EventListDto>> searchEvents(
            @RequestParam(name = "starts_at", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startsAt,

            @RequestParam(name = "ends_at", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endsAt) {

        // Validate: if one is provided, both should be provided
        if ((startsAt == null) != (endsAt == null)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("BAD_REQUEST",
                            "Both 'starts_at' and 'ends_at' must be provided, or neither"));
        }

        // Default range if neither is provided: return all events
        if (startsAt == null) {
            startsAt = LocalDateTime.of(1970, 1, 1, 0, 0);
            endsAt = LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        }

        if (startsAt.isAfter(endsAt)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("BAD_REQUEST",
                            "'starts_at' must be before 'ends_at'"));
        }

        log.info("Searching events from {} to {}", startsAt, endsAt);
        List<EventSummaryDto> events = searchService.search(startsAt, endsAt);

        return ResponseEntity.ok(ApiResponse.success(new EventListDto(events)));
    }
}
