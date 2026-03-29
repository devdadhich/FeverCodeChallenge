package com.fever.challenge;

import com.fever.challenge.client.ProviderClientPort;
import com.fever.challenge.client.ProviderXmlModels.*;
import com.fever.challenge.entity.EventEntity;
import com.fever.challenge.repository.EventRepository;
import com.fever.challenge.service.EventSyncService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class EventSyncServiceTest {

    // XML with 2 base plans: one online (1 plan, 3 zones), one offline
    private static final String PROVIDER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <planList version="1.0">
               <output>
                  <base_plan base_plan_id="291" sell_mode="online" title="Camela en concierto">
                     <plan plan_start_date="2021-06-30T21:00:00" plan_end_date="2021-06-30T22:00:00"
                           plan_id="291" sell_from="2020-07-01T00:00:00" sell_to="2021-06-30T20:00:00" sold_out="false">
                        <zone zone_id="40" capacity="243" price="20.00" name="Platea" numbered="true" />
                        <zone zone_id="38" capacity="100" price="15.00" name="Grada 2" numbered="false" />
                        <zone zone_id="30" capacity="90" price="30.00" name="A28" numbered="true" />
                     </plan>
                  </base_plan>
                  <base_plan base_plan_id="444" sell_mode="offline" title="Offline Event">
                     <plan plan_start_date="2021-09-15T20:00:00" plan_end_date="2021-09-15T21:00:00"
                           plan_id="1642" sell_from="2021-02-10T00:00:00" sell_to="2021-09-15T19:50:00" sold_out="false">
                        <zone zone_id="7" capacity="22" price="65.00" name="Amfiteatre" numbered="false" />
                     </plan>
                  </base_plan>
               </output>
            </planList>
            """;

    // XML where the same online event has updated prices and title
    private static final String PROVIDER_XML_UPDATED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <planList version="1.0">
               <output>
                  <base_plan base_plan_id="291" sell_mode="online" title="Camela en concierto - Updated">
                     <plan plan_start_date="2021-06-30T21:00:00" plan_end_date="2021-06-30T22:00:00"
                           plan_id="291" sell_from="2020-07-01T00:00:00" sell_to="2021-06-30T20:00:00" sold_out="false">
                        <zone zone_id="40" capacity="243" price="25.00" name="Platea" numbered="true" />
                        <zone zone_id="38" capacity="100" price="10.00" name="Grada 2" numbered="false" />
                     </plan>
                  </base_plan>
               </output>
            </planList>
            """;

    // XML with a new online event added alongside the original
    private static final String PROVIDER_XML_NEW_EVENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <planList version="1.0">
               <output>
                  <base_plan base_plan_id="291" sell_mode="online" title="Camela en concierto">
                     <plan plan_start_date="2021-06-30T21:00:00" plan_end_date="2021-06-30T22:00:00"
                           plan_id="291" sell_from="2020-07-01T00:00:00" sell_to="2021-06-30T20:00:00" sold_out="false">
                        <zone zone_id="40" capacity="243" price="20.00" name="Platea" numbered="true" />
                     </plan>
                  </base_plan>
                  <base_plan base_plan_id="500" sell_mode="online" title="New Festival">
                     <plan plan_start_date="2021-08-15T18:00:00" plan_end_date="2021-08-15T23:00:00"
                           plan_id="600" sell_from="2021-06-01T00:00:00" sell_to="2021-08-15T17:00:00" sold_out="false">
                        <zone zone_id="1" capacity="500" price="45.00" name="General" numbered="false" />
                     </plan>
                  </base_plan>
               </output>
            </planList>
            """;

    @MockBean
    private ProviderClientPort providerClient;

    @Autowired
    private EventSyncService syncService;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    @Test
    void shouldOnlyPersistOnlineEvents() throws Exception {
        when(providerClient.fetchEvents()).thenReturn(Optional.of(parseXml(PROVIDER_XML)));

        syncService.syncEvents();

        List<EventEntity> all = eventRepository.findAll();
        assertEquals(1, all.size(), "Only online events should be persisted");
        assertEquals("Camela en concierto", all.get(0).getTitle());
        assertEquals("291", all.get(0).getBasePlanId());
    }

    @Test
    void shouldComputeMinAndMaxPricesFromZones() throws Exception {
        when(providerClient.fetchEvents()).thenReturn(Optional.of(parseXml(PROVIDER_XML)));

        syncService.syncEvents();

        EventEntity event = eventRepository.findAll().get(0);
        assertEquals(0, event.getMinPrice().compareTo(new BigDecimal("15.00")),
                "Min price should be lowest zone price");
        assertEquals(0, event.getMaxPrice().compareTo(new BigDecimal("30.00")),
                "Max price should be highest zone price");
    }

    @Test
    void shouldUpdateExistingEventOnResync() throws Exception {
        // First sync
        when(providerClient.fetchEvents()).thenReturn(Optional.of(parseXml(PROVIDER_XML)));
        syncService.syncEvents();

        List<EventEntity> afterFirst = eventRepository.findAll();
        assertEquals(1, afterFirst.size());
        assertEquals("Camela en concierto", afterFirst.get(0).getTitle());

        // Second sync with updated data
        when(providerClient.fetchEvents()).thenReturn(Optional.of(parseXml(PROVIDER_XML_UPDATED)));
        syncService.syncEvents();

        List<EventEntity> afterSecond = eventRepository.findAll();
        assertEquals(1, afterSecond.size(), "Should update existing, not create duplicate");
        assertEquals("Camela en concierto - Updated", afterSecond.get(0).getTitle());
        assertEquals(0, afterSecond.get(0).getMinPrice().compareTo(new BigDecimal("10.00")),
                "Min price should be updated");
        assertEquals(0, afterSecond.get(0).getMaxPrice().compareTo(new BigDecimal("25.00")),
                "Max price should be updated");
    }

    @Test
    void shouldPreserveEventsNotInLatestResponse() throws Exception {
        // First sync: creates the event
        when(providerClient.fetchEvents()).thenReturn(Optional.of(parseXml(PROVIDER_XML)));
        syncService.syncEvents();
        assertEquals(1, eventRepository.findAll().size());

        // Second sync: provider returns new event alongside old one
        when(providerClient.fetchEvents()).thenReturn(Optional.of(parseXml(PROVIDER_XML_NEW_EVENT)));
        syncService.syncEvents();

        // Both events should exist
        List<EventEntity> all = eventRepository.findAll();
        assertEquals(2, all.size(), "New events should be added while preserving existing ones");
    }

    @Test
    void shouldNotPersistAnythingWhenProviderFails() {
        when(providerClient.fetchEvents()).thenReturn(Optional.empty());

        syncService.syncEvents();

        assertEquals(0, eventRepository.findAll().size(),
                "No events should be persisted when provider fails");
    }

    @Test
    void shouldParseDateTimesCorrectly() throws Exception {
        when(providerClient.fetchEvents()).thenReturn(Optional.of(parseXml(PROVIDER_XML)));

        syncService.syncEvents();

        EventEntity event = eventRepository.findAll().get(0);
        assertNotNull(event.getPlanStartDate());
        assertNotNull(event.getPlanEndDate());
        assertEquals(2021, event.getPlanStartDate().getYear());
        assertEquals(6, event.getPlanStartDate().getMonthValue());
        assertEquals(30, event.getPlanStartDate().getDayOfMonth());
        assertEquals(21, event.getPlanStartDate().getHour());
        assertEquals(0, event.getPlanStartDate().getMinute());
    }

    private PlanList parseXml(String xml) throws Exception {
        JAXBContext context = JAXBContext.newInstance(PlanList.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (PlanList) unmarshaller.unmarshal(new StringReader(xml));
    }
}
