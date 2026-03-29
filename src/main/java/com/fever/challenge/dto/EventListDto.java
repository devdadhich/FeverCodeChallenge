package com.fever.challenge.dto;

import java.util.List;

/**
 * Wrapper for the event list, matching the OpenAPI EventList schema.
 */
public class EventListDto {

    private List<EventSummaryDto> events;

    public EventListDto(List<EventSummaryDto> events) {
        this.events = events;
    }

    public List<EventSummaryDto> getEvents() { return events; }
    public void setEvents(List<EventSummaryDto> events) { this.events = events; }
}
