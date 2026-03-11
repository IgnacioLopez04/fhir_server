package com.serverfhir.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    private final RestTemplate restTemplate;

    @Value("${tfback.url:http://localhost:3000}")
    private String tfbackUrl;

    @Value("${tfback.api.path:/api}")
    private String tfbackApiPath;

    @Value("${tfback.audit.secret:}")
    private String tfbackAuditSecret;

    public AuditLogService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Registra un evento de auditoría en log y lo envía a TF_Back para persistir en audit_log.
     * El envío a TF_Back es asíncrono y no bloquea la respuesta FHIR.
     */
    public void saveAuditEvent(
            String userEmail,
            String method,
            String path,
            String ipAddress,
            String userAgent,
            String resourceType,
            String patientHashId,
            String action,
            Integer statusCode
    ) {
        Map<String, Object> event = new HashMap<>();
        event.put("user_email", userEmail);
        event.put("service", "fhir_server");
        event.put("http_method", method);
        event.put("path", path);
        event.put("ip_address", ipAddress);
        event.put("user_agent", userAgent);
        event.put("resource_type", resourceType);
        event.put("patient_hash_id", patientHashId);
        event.put("action", action);
        event.put("status_code", statusCode);

        logger.info("FHIR_AUDIT_EVENT {}", event);

        if (tfbackAuditSecret == null || tfbackAuditSecret.isEmpty()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", null);
        payload.put("user_email", userEmail);
        payload.put("user_role", null);
        payload.put("ip_address", ipAddress);
        payload.put("user_agent", userAgent);
        payload.put("service", "fhir_server");
        payload.put("http_method", method);
        payload.put("path", path);
        payload.put("status_code", statusCode);
        payload.put("resource_type", resourceType);
        payload.put("patient_hash_id", patientHashId);
        payload.put("action", action);
        payload.put("metadata", null);

        String auditUrl = tfbackUrl.replaceAll("/$", "")
            + (tfbackApiPath.startsWith("/") ? tfbackApiPath : "/" + tfbackApiPath)
            + "/internal/audit";
        CompletableFuture.runAsync(() -> sendToTfBack(auditUrl, payload));
    }

    private void sendToTfBack(String url, Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Api-Key", tfbackAuditSecret);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(url, request, Void.class);
        } catch (Exception e) {
            logger.warn("Error enviando evento de auditoría a TF_Back: {}", e.getMessage());
        }
    }
}
