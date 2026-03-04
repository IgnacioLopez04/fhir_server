package com.serverfhir.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    /**
     * Registra un evento de auditoría como log operacional.
     * No realiza ninguna operación de base de datos para respetar
     * la arquitectura donde este servicio no accede directamente a la BD.
     */
    public void saveAuditEvent(
            String userEmail,
            String method,
            String path,
            String ipAddress,
            String resourceType,
            String patientHashId,
            String action
    ) {
        Map<String, Object> event = new HashMap<>();
        event.put("user_email", userEmail);
        event.put("service", "fhir_server");
        event.put("http_method", method);
        event.put("path", path);
        event.put("ip_address", ipAddress);
        event.put("resource_type", resourceType);
        event.put("patient_hash_id", patientHashId);
        event.put("action", action);

        logger.info("FHIR_AUDIT_EVENT {}", event);
    }
}

