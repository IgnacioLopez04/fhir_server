package com.serverfhir.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RateLimitProperties {

    @Value("${ratelimit.enabled:true}")
    private boolean enabled;

    @Value("${ratelimit.fhir.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${ratelimit.fhir.burst:20}")
    private int burst;

    public boolean isEnabled() {
        return enabled;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public int getBurst() {
        return burst;
    }
}

