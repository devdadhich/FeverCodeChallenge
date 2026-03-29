package com.fever.challenge.client;

import com.fever.challenge.client.ProviderXmlModels.PlanList;

import java.util.Optional;

/**
 * Port for fetching event data from the external provider.
 * Abstracted as an interface to decouple sync logic from HTTP/retry details
 * and to enable straightforward testing.
 */
public interface ProviderClientPort {

    /**
     * Fetches event plans from the provider.
     * Returns empty if the provider is unavailable or all retries fail.
     */
    Optional<PlanList> fetchEvents();
}
