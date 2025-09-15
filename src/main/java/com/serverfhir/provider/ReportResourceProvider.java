package com.serverfhir.provider;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r5.model.DiagnosticReport;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.ResourceType;
import org.hl7.fhir.r5.model.Extension;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ReportResourceProvider implements IResourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(ReportResourceProvider.class);
    private static final String BASE_URL = "http://localhost:3000/api";

    @Override
    public Class<DiagnosticReport> getResourceType() {
        return DiagnosticReport.class;
    }

    @Create
    public MethodOutcome createReport(@ResourceParam DiagnosticReport diagnosticReport, RequestDetails requestDetails) {
        // Validación de token ya se hace en el interceptor
        RestTemplate restTemplate = new RestTemplate();

        // Obtener el token del contexto de la petición
        String token = requestDetails.getHeader("Authorization");

        // Procesar los datos del DiagnosticReport
        Map<String, Object> payload = new HashMap<>();

        // ID del usuario (obligatorio)
        if (diagnosticReport.hasSubject() && diagnosticReport.getSubject().hasReference()) {
            // Extraer DNI del paciente de la referencia o extensión
            String patientDni = extractPatientDni(diagnosticReport);
            payload.put("patientDni", patientDni);
        }

        // ID del usuario - se obtiene del token o se puede pasar como extensión
        String userId = extractUserId(diagnosticReport);
        payload.put("userId", userId);

        // Título del reporte
        if (diagnosticReport.hasCode() && diagnosticReport.getCode().hasText()) {
            payload.put("tittle", diagnosticReport.getCode().getText());
        } else {
            payload.put("tittle", "Reporte de Diagnóstico"); // Valor por defecto
        }

        // Contenido del reporte
        if (diagnosticReport.hasConclusion()) {
            payload.put("text", diagnosticReport.getConclusion());
        } else {
            payload.put("text", ""); // Valor vacío si no hay conclusión
        }

        // Tipo de reporte - se puede mapear desde el código o usar extensión
        String reportType = extractReportType(diagnosticReport);
        payload.put("reportType", reportType);

        // ID de especialidad - se puede obtener de extensiones o usar valor por defecto
        String specialityId = extractSpecialityId(diagnosticReport);
        payload.put("specialityId", specialityId);

        // ID de historia clínica - se puede obtener de extensiones o usar null
        String ehrId = extractEhrId(diagnosticReport);
        payload.put("ehrId", ehrId);

        // Log del payload final
        logger.info("Payload final para crear reporte: " + payload.toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Agregar el token de autorización a los headers
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", token);
        } else {
            throw new RuntimeException("No se pudo crear el reporte. No se encontró el accessToken.");
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            logger.info("Enviando petición al backend: " + BASE_URL + "/report/create");
            logger.info("Payload: " + payload.toString());
            
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                BASE_URL + "/report/create", 
                request, 
                Map.class
            );
            
            logger.info("Respuesta recibida del backend - Status: " + response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                String reportId = "unknown";
                
                if (responseBody != null) {
                    if (responseBody.containsKey("id_informe")) {
                        reportId = responseBody.get("id_informe").toString();
                    } else if (responseBody.containsKey("id")) {
                        reportId = responseBody.get("id").toString();
                    } else {
                        // Si la respuesta es solo un número (el ID directo)
                        reportId = responseBody.toString();
                    }
                }
                
                MethodOutcome outcome = new MethodOutcome();
                outcome.setId(new IdType(ResourceType.DiagnosticReport.name(), reportId));
                outcome.setCreated(true);
                
                logger.info("Reporte creado exitosamente con ID: " + reportId);
                logger.info("Respuesta del backend: " + responseBody);
                return outcome;
            } else {
                logger.error("Error en la API externa: código " + response.getStatusCode());
                throw new RuntimeException("Error en la API externa: código " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Error al crear el reporte: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                "No se pudo crear el reporte: " + e.getMessage()
            );
        }
    }

    @Read
    public DiagnosticReport readReport(@IdParam IdType id, RequestDetails requestDetails) {
        // Implementación para leer un reporte específico
        // Por ahora retornamos un reporte básico
        DiagnosticReport report = new DiagnosticReport();
        report.setId(id.getIdPart());
        report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
        
        CodeableConcept code = new CodeableConcept();
        code.setText("Reporte de Diagnóstico");
        report.setCode(code);
        
        return report;
    }

    @Search
    public List<DiagnosticReport> searchReports(RequestDetails requestDetails) {
        // Implementación para buscar reportes
        // Por ahora retornamos una lista vacía
        return new ArrayList<>();
    }

    /**
     * Extrae el DNI del paciente de las extensiones o referencias
     */
    private String extractPatientDni(DiagnosticReport report) {
        if (report.hasExtension()) {
            for (Extension extension : report.getExtension()) {
                if (extension.getUrl().contains("patient-dni")) {
                    return extension.getValue().toString();
                }
            }
        }
        return null; // Se puede manejar como null en el backend
    }

    /**
     * Extrae el ID del usuario de las extensiones
     */
    private String extractUserId(DiagnosticReport report) {
        if (report.hasExtension()) {
            for (Extension extension : report.getExtension()) {
                if (extension.getUrl().contains("user-id")) {
                    return extension.getValue().toString();
                }
            }
        }
        return null; // Se puede manejar como null en el backend
    }

    /**
     * Extrae el tipo de reporte de las extensiones o códigos
     */
    private String extractReportType(DiagnosticReport report) {
        if (report.hasExtension()) {
            for (Extension extension : report.getExtension()) {
                if (extension.getUrl().contains("report-type")) {
                    return extension.getValue().toString();
                }
            }
        }
        return "1"; // Valor por defecto para tipo de reporte
    }

    /**
     * Extrae el ID de especialidad de las extensiones
     */
    private String extractSpecialityId(DiagnosticReport report) {
        if (report.hasExtension()) {
            for (Extension extension : report.getExtension()) {
                if (extension.getUrl().contains("speciality-id")) {
                    return extension.getValue().toString();
                }
            }
        }
        return null; // Se puede manejar como null en el backend
    }

    /**
     * Extrae el ID de historia clínica de las extensiones
     */
    private String extractEhrId(DiagnosticReport report) {
        if (report.hasExtension()) {
            for (Extension extension : report.getExtension()) {
                if (extension.getUrl().contains("ehr-id")) {
                    return extension.getValue().toString();
                }
            }
        }
        return null; // Se puede manejar como null en el backend
    }
}
