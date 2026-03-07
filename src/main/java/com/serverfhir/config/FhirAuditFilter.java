package com.serverfhir.config;

import com.serverfhir.service.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que registra auditoría para cada petición a /fhir/* (excepto /fhir/metadata).
 * Las peticiones FHIR van al servlet HAPI y no pasan por los interceptores de Spring MVC,
 * por eso el audit debe hacerse aquí como Filter.
 */
public class FhirAuditFilter extends OncePerRequestFilter {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    private final AuditLogService auditLogService;

    public FhirAuditFilter(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isEmpty()) {
            int comma = forwarded.indexOf(',');
            String resolved = comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
            return normalizeLoopback(resolved);
        }
        String realIp = request.getHeader(X_REAL_IP);
        if (realIp != null && !realIp.isEmpty()) {
            return normalizeLoopback(realIp.trim());
        }
        return normalizeLoopback(request.getRemoteAddr());
    }

    /** Normaliza direcciones loopback a "127.0.0.1" para consistencia en el audit. */
    private static String normalizeLoopback(String ip) {
        if (ip == null) return ip;
        String s = ip.trim();
        if ("127.0.0.1".equals(s) || "::1".equals(s)
                || "0:0:0:0:0:0:0:1".equalsIgnoreCase(s)) {
            return "127.0.0.1";
        }
        return ip;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        if (!requestURI.startsWith("/fhir/") || requestURI.equals("/fhir/metadata")) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = "anonymous";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            email = auth.getPrincipal().toString();
        }

        String method = request.getMethod();
        String action =
            "GET".equals(method) ? "READ" :
            "POST".equals(method) ? "CREATE" :
            ("PUT".equals(method) || "PATCH".equals(method)) ? "UPDATE" :
            "DELETE".equals(method) ? "DELETE" : null;

        String resourceType = null;
        String patientHashId = null;
        if (requestURI.contains("DiagnosticReport")) {
            resourceType = "DiagnosticReport";
        } else if (requestURI.startsWith("/fhir/Patient")) {
            resourceType = "Patient";
            String[] segments = requestURI.split("/");
            if (segments.length >= 4) {
                patientHashId = segments[3];
            }
        }

        String userAgent = request.getHeader("User-Agent");
        String ipAddress = resolveClientIp(request);

        StatusCapturingResponseWrapper wrappedResponse = new StatusCapturingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            int statusCode = wrappedResponse.getCapturedStatus();
            auditLogService.saveAuditEvent(
                email,
                method,
                requestURI,
                ipAddress,
                userAgent,
                resourceType,
                patientHashId,
                action,
                statusCode
            );
        }
    }

    /**
     * Wrapper que captura el status code enviado para poder auditarlo después del chain.
     */
    private static class StatusCapturingResponseWrapper extends jakarta.servlet.http.HttpServletResponseWrapper {
        private int capturedStatus = 200;

        public StatusCapturingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.capturedStatus = sc;
            super.setStatus(sc);
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.capturedStatus = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.capturedStatus = sc;
            super.sendError(sc, msg);
        }

        public int getCapturedStatus() {
            return capturedStatus;
        }
    }
}
