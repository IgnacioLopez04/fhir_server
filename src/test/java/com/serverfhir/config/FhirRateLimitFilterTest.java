package com.serverfhir.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FhirRateLimitFilterTest {

    @Test
    void shouldReturn429AfterExceedingLimitForSameIp() throws ServletException, IOException {
        RateLimitProperties props = mock(RateLimitProperties.class);
        when(props.isEnabled()).thenReturn(true);
        when(props.getRequestsPerMinute()).thenReturn(2);
        when(props.getBurst()).thenReturn(0);

        FhirRateLimitFilter filter = new FhirRateLimitFilter(props);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/fhir/Patient");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        // Primeras dos peticiones deberían pasar
        filter.doFilterInternal(request, response, chain);
        filter.doFilterInternal(request, response, chain);

        // Tercera petición debe ser bloqueada con 429
        doAnswer(invocation -> null).when(response).setStatus(anyInt());

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(eq(429));
    }
}

