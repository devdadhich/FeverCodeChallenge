package com.fever.challenge.client;

import com.fever.challenge.client.ProviderXmlModels.PlanList;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.StringReader;
import java.util.Optional;

/**
 * Client responsible for fetching and parsing event data from the external provider.
 * Includes retry logic to handle transient failures.
 */
@Component
public class ProviderClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderClient.class);

    private final RestTemplate restTemplate;
    private final String providerUrl;
    private final int maxRetries;
    private final long retryDelayMs;
    private final JAXBContext jaxbContext;

    public ProviderClient(
            RestTemplate providerRestTemplate,
            @Value("${provider.url}") String providerUrl,
            @Value("${provider.max-retries}") int maxRetries,
            @Value("${provider.retry-delay-ms}") long retryDelayMs) throws JAXBException {

        this.restTemplate = providerRestTemplate;
        this.providerUrl = providerUrl;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.jaxbContext = JAXBContext.newInstance(PlanList.class);
    }

    /**
     * Fetches events from the provider with retry logic.
     * Returns empty if all attempts fail.
     */
    public Optional<PlanList> fetchEvents() {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(providerUrl, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    PlanList planList = parseXml(response.getBody());
                    log.info("Successfully fetched events from provider (attempt {})", attempt);
                    return Optional.of(planList);
                }

                log.warn("Provider returned non-success status: {} (attempt {})",
                        response.getStatusCode(), attempt);

            } catch (RestClientException e) {
                log.warn("Failed to fetch from provider (attempt {}/{}): {}",
                        attempt, maxRetries, e.getMessage());
            } catch (JAXBException e) {
                log.error("Failed to parse provider XML response (attempt {}): {}",
                        attempt, e.getMessage());
            }

            if (attempt < maxRetries) {
                sleep(retryDelayMs);
            }
        }

        log.error("All {} attempts to fetch from provider failed", maxRetries);
        return Optional.empty();
    }

    private PlanList parseXml(String xml) throws JAXBException {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return (PlanList) unmarshaller.unmarshal(new StringReader(xml));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
