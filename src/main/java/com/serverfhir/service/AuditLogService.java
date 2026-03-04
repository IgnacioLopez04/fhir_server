package com.serverfhir.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    private final DataSource dataSource;

    public AuditLogService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void saveAuditEvent(
            String userEmail,
            String method,
            String path,
            String ipAddress,
            String resourceType,
            String patientHashId,
            String action
    ) {
        String sql = "INSERT INTO audit_log (" +
                "user_email, service, http_method, path, ip_address, " +
                "resource_type, patient_hash_id, action, metadata" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)";

        String metadataJson = "{}";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, userEmail);
            statement.setString(2, "fhir_server");
            statement.setString(3, method);
            statement.setString(4, path);
            statement.setString(5, ipAddress);
            statement.setString(6, resourceType);
            statement.setString(7, patientHashId);
            statement.setString(8, action);
            statement.setString(9, metadataJson);

            statement.executeUpdate();
        } catch (Exception e) {
            logger.warn("Error insertando evento de auditoria en audit_log", e);
        }
    }
}


