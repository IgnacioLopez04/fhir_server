package com.serverfhir.interceptor;

import com.serverfhir.service.AuditLogService;
import com.serverfhir.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class FhirAuditInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(FhirAuditInterceptor.class);

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuditLogService auditLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestURI = request.getRequestURI();

        if (requestURI.startsWith("/fhir/") && !requestURI.equals("/fhir/metadata")) {
            String authHeader = request.getHeader("Authorization");
            String token = authHeader != null && !authHeader.isEmpty() ? authHeader : null;

            String email = null;
            if (token != null) {
                var claims = jwtService.getClaimsFromToken(token);
                if (claims != null) {
                    Object emailClaim = claims.get("email");
                    email = emailClaim != null ? emailClaim.toString() : null;
                }
            }

            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("type", "audit");
            auditEvent.put("service", "fhir_server");
            auditEvent.put("timestamp", Instant.now().toString());
            auditEvent.put("user_email", email != null ? email : "anonymous");
            auditEvent.put("method", request.getMethod());
            auditEvent.put("path", requestURI);
            auditEvent.put("ip", request.getRemoteAddr());

            logger.info(auditEvent.toString());

            String method = request.getMethod();
            String action =
                "GET".equals(method) ? "READ" :
                "POST".equals(method) ? "CREATE" :
                ("PUT".equals(method) || "PATCH".equals(method)) ? "UPDATE" :
                "DELETE".equals(method) ? "DELETE" :
                null;

            String resourceType = null;
            String patientHashId = null;

            if (requestURI.startsWith("/fhir/Patient")) {
                resourceType = "Patient";
            }

            auditLogService.saveAuditEvent(
                email != null ? email : "anonymous",
                method,
                requestURI,
                request.getRemoteAddr(),
                resourceType,
                patientHashId,
                action
            );
        }

        return true;
    }
}
