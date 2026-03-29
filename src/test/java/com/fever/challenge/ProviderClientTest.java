package com.fever.challenge;

import com.fever.challenge.client.ProviderClient;
import com.fever.challenge.client.ProviderXmlModels.PlanList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProviderClientTest {

    private static final String PROVIDER_URL = "http://test-provider/api/events";

    private static final String VALID_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <planList version="1.0">
               <output>
                  <base_plan base_plan_id="291" sell_mode="online" title="Test Event">
                     <plan plan_start_date="2021-06-30T21:00:00" plan_end_date="2021-06-30T22:00:00"
                           plan_id="291" sell_from="2020-07-01T00:00:00" sell_to="2021-06-30T20:00:00" sold_out="false">
                        <zone zone_id="40" capacity="243" price="20.00" name="Platea" numbered="true" />
                     </plan>
                  </base_plan>
               </output>
            </planList>
            """;

    private RestTemplate restTemplate;
    private ProviderClient client;

    @BeforeEach
    void setUp() throws Exception {
        restTemplate = Mockito.mock(RestTemplate.class);
        // maxRetries=3, retryDelayMs=0 (no delay in tests)
        client = new ProviderClient(restTemplate, PROVIDER_URL, 3, 0);
    }

    @Test
    void shouldParseValidXmlSuccessfully() {
        when(restTemplate.getForEntity(PROVIDER_URL, String.class))
                .thenReturn(new ResponseEntity<>(VALID_XML, HttpStatus.OK));

        Optional<PlanList> result = client.fetchEvents();

        assertTrue(result.isPresent());
        assertNotNull(result.get().getOutput());
        assertEquals(1, result.get().getOutput().getBasePlans().size());
        assertEquals("Test Event", result.get().getOutput().getBasePlans().get(0).getTitle());
    }

    @Test
    void shouldRetryOnNetworkFailureAndSucceedOnSecondAttempt() {
        when(restTemplate.getForEntity(PROVIDER_URL, String.class))
                .thenThrow(new RestClientException("Connection refused"))
                .thenReturn(new ResponseEntity<>(VALID_XML, HttpStatus.OK));

        Optional<PlanList> result = client.fetchEvents();

        assertTrue(result.isPresent());
        verify(restTemplate, times(2)).getForEntity(PROVIDER_URL, String.class);
    }

    @Test
    void shouldReturnEmptyAfterAllRetriesExhausted() {
        when(restTemplate.getForEntity(PROVIDER_URL, String.class))
                .thenThrow(new RestClientException("Connection refused"));

        Optional<PlanList> result = client.fetchEvents();

        assertTrue(result.isEmpty());
        verify(restTemplate, times(3)).getForEntity(PROVIDER_URL, String.class);
    }

    @Test
    void shouldReturnEmptyOnInvalidXml() {
        when(restTemplate.getForEntity(PROVIDER_URL, String.class))
                .thenReturn(new ResponseEntity<>("not valid xml <broken", HttpStatus.OK));

        Optional<PlanList> result = client.fetchEvents();

        // Invalid XML should cause JAXB parse failure, client retries but all fail
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyOnNon2xxStatus() {
        when(restTemplate.getForEntity(PROVIDER_URL, String.class))
                .thenReturn(new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR));

        Optional<PlanList> result = client.fetchEvents();

        assertTrue(result.isEmpty());
        verify(restTemplate, times(3)).getForEntity(PROVIDER_URL, String.class);
    }

    @Test
    void shouldReturnEmptyOnNullBody() {
        when(restTemplate.getForEntity(PROVIDER_URL, String.class))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        Optional<PlanList> result = client.fetchEvents();

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSucceedOnFirstAttemptWithoutUnnecessaryRetries() {
        when(restTemplate.getForEntity(PROVIDER_URL, String.class))
                .thenReturn(new ResponseEntity<>(VALID_XML, HttpStatus.OK));

        client.fetchEvents();

        verify(restTemplate, times(1)).getForEntity(PROVIDER_URL, String.class);
    }
}
