package com.serverfhir.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Filtro de rate limiting por IP para todas las rutas /fhir/**.
 * Limita el número de requests por minuto desde una misma IP.
 */
public class FhirRateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(FhirRateLimitFilter.class);

    private static final long ONE_MINUTE_MILLIS = 60_000L;

    private final RateLimitProperties properties;
    private final Map<String, RequestBucket> buckets = new ConcurrentHashMap<>();

    public FhirRateLimitFilter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        if (!uri.startsWith("/fhir")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        int limitPerMinute = Math.max(1, properties.getRequestsPerMinute());

        long now = Instant.now().toEpochMilli();
        RequestBucket bucket = buckets.computeIfAbsent(clientIp, ip -> new RequestBucket(now));

        boolean allowed;
        synchronized (bucket) {
            long elapsed = now - bucket.windowStartMillis;
            if (elapsed > ONE_MINUTE_MILLIS) {
                bucket.windowStartMillis = now;
                bucket.counter.set(0);
            }
            int current = bucket.counter.incrementAndGet();
            allowed = current <= limitPerMinute;
        }

        if (!allowed) {
            logger.warn("Rate limit excedido para IP {} en URI {}", clientIp, uri);
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String body = """
            {
              "resourceType": "OperationOutcome",
              "issue": [
                {
                  "severity": "error",
                  "code": "throttled",
                  "diagnostics": "Too many requests - rate limit exceeded for this client."
                }
              ]
            }
            """;
        response.getWriter().write(body);
        response.flushBuffer();
    }

    private static class RequestBucket {
        private long windowStartMillis;
        private final AtomicInteger counter = new AtomicInteger(0);

        private RequestBucket(long windowStartMillis) {
            this.windowStartMillis = windowStartMillis;
        }
    }
}

