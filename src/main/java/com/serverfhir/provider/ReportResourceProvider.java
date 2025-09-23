package com.serverfhir.provider;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r5.model.DiagnosticReport;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.ResourceType;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.Attachment;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.model.DateTimeType;
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
        // Verificar si es un anexo basado en las extensiones
        boolean isAnnex = false;
        if (diagnosticReport.hasExtension()) {
            for (Extension extension : diagnosticReport.getExtension()) {
                if (extension.getUrl().contains("is-annex") &&
                        (extension.getValue() instanceof org.hl7.fhir.r5.model.BooleanType) &&
                        ((org.hl7.fhir.r5.model.BooleanType) extension.getValue()).getValue()) {
                    isAnnex = true;
                    break;
                }
            }
        }

        if (isAnnex) {
            // Es un anexo, procesar como tal
            return createAnnex(diagnosticReport, requestDetails);
        } else {
            // Es un reporte normal, procesar como tal
            return createNormalReport(diagnosticReport, requestDetails);
        }
    }

    private MethodOutcome createNormalReport(DiagnosticReport diagnosticReport, RequestDetails requestDetails) {
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

    private MethodOutcome createAnnex(DiagnosticReport diagnosticReport, RequestDetails requestDetails) {
        RestTemplate restTemplate = new RestTemplate();
        String token = requestDetails.getHeader("Authorization");

        // Obtener el ID del reporte padre desde la referencia del subject
        String reportId = null;
        if (diagnosticReport.hasSubject() && diagnosticReport.getSubject().hasReference()) {
            String reference = diagnosticReport.getSubject().getReference();
            logger.info("Referencia del subject: " + reference);

            if (reference.startsWith("DiagnosticReport/")) {
                reportId = reference.replace("DiagnosticReport/", "");
                logger.info("ReportId extraído: " + reportId);
            }
        }

        // También intentar extraer desde extensiones como fallback
        if (reportId == null && diagnosticReport.hasExtension()) {
            for (Extension extension : diagnosticReport.getExtension()) {
                if (extension.getUrl().contains("report-hash-id")) {
                    reportId = extension.getValue().toString();
                    logger.info("ReportId extraído desde extensión: " + reportId);
                    break;
                }
            }
        }

        if (reportId == null || reportId.isEmpty() || "undefined".equals(reportId)) {
            logger.error("No se pudo determinar el ID del reporte padre para el anexo");
            logger.error("Subject reference: "
                    + (diagnosticReport.hasSubject() ? diagnosticReport.getSubject().getReference() : "null"));
            throw new RuntimeException(
                    "No se pudo determinar el ID del reporte padre para el anexo. Verifique que la referencia del subject sea correcta.");
        }

        // Obtener el ID del usuario del token o extensiones
        String userId = extractUserId(diagnosticReport);

        // Contenido del anexo
        String text = "";
        if (diagnosticReport.hasConclusion()) {
            text = diagnosticReport.getConclusion();
        }

        // Crear payload para el anexo
        Map<String, Object> payload = new HashMap<>();
        payload.put("reportHashId", reportId);
        payload.put("userId", userId);
        payload.put("text", text);

        logger.info("Payload final para crear anexo: " + payload.toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", token);
        } else {
            throw new RuntimeException("No se pudo crear el anexo. No se encontró el accessToken.");
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            String url = BASE_URL + "/report/" + reportId + "/createAnnex";
            logger.info("Enviando petición al backend: " + url);
            logger.info("Payload: " + payload.toString());

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url,
                    request,
                    Map.class);

            logger.info("Respuesta recibida del backend - Status: " + response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                String annexId = "unknown";

                if (responseBody != null) {
                    if (responseBody.containsKey("id_anexo")) {
                        annexId = responseBody.get("id_anexo").toString();
                    } else if (responseBody.containsKey("id")) {
                        annexId = responseBody.get("id").toString();
                    } else {
                        annexId = responseBody.toString();
                    }
                }

                MethodOutcome outcome = new MethodOutcome();
                outcome.setId(new IdType(ResourceType.DiagnosticReport.name(), annexId));
                outcome.setCreated(true);

                logger.info("Anexo creado exitosamente con ID: " + annexId);
                logger.info("Respuesta del backend: " + responseBody);
                return outcome;
            } else {
                logger.error("Error en la API externa: código " + response.getStatusCode());
                throw new RuntimeException("Error en la API externa: código " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Error al crear el anexo: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                    "No se pudo crear el anexo: " + e.getMessage());
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
    public List<DiagnosticReport> searchReports(
            @OptionalParam(name = "patient") StringParam patientHashId,
            RequestDetails requestDetails) {

        logger.info("Buscando reportes para paciente con hashId: " +
                (patientHashId != null ? patientHashId.getValue() : "null"));

        if (patientHashId == null || patientHashId.getValue() == null || patientHashId.getValue().isEmpty()) {
            logger.warn("No se proporcionó hashId del paciente");
            return new ArrayList<>();
        }

        RestTemplate restTemplate = new RestTemplate();

        // Obtener el token del contexto de la petición
        String token = requestDetails.getHeader("Authorization");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Agregar el token de autorización a los headers
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", token);
        } else {
            logger.error("No se encontró el token de autorización");
            throw new RuntimeException("No se pudo consultar los reportes. No se encontró el accessToken.");
        }

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            String url = BASE_URL + "/report/all/" + patientHashId.getValue();
            logger.info("Consultando reportes en: " + url);

            @SuppressWarnings("rawtypes")
            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    request,
                    List.class);

            logger.info("Respuesta recibida del backend - Status: " + response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> reportsData = (List<Map<String, Object>>) response.getBody();

                List<DiagnosticReport> diagnosticReports = new ArrayList<>();

                for (Map<String, Object> reportData : reportsData) {
                    DiagnosticReport diagnosticReport = convertToDiagnosticReport(reportData, patientHashId.getValue());
                    diagnosticReports.add(diagnosticReport);
                }

                logger.info("Se encontraron " + diagnosticReports.size() + " reportes para el paciente");
                return diagnosticReports;
            } else {
                logger.warn("No se encontraron reportes o error en la respuesta: " + response.getStatusCode());
                return new ArrayList<>();
            }

        } catch (Exception e) {
            logger.error("Error al consultar los reportes: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                    "No se pudieron consultar los reportes: " + e.getMessage());
        }
    }

    @Search
    public List<DiagnosticReport> searchReportAnnexes(
            @OptionalParam(name = "annex") StringParam reportHashId,
            RequestDetails requestDetails) {

        logger.info("Buscando anexos para reporte con hashId: " +
                (reportHashId != null ? reportHashId.getValue() : "null"));

        if (reportHashId == null || reportHashId.getValue() == null || reportHashId.getValue().isEmpty()) {
            logger.warn("No se proporcionó hashId del reporte");
            return new ArrayList<>();
        }

        RestTemplate restTemplate = new RestTemplate();

        // Obtener el token del contexto de la petición
        String token = requestDetails.getHeader("Authorization");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Agregar el token de autorización a los headers
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", token);
        } else {
            logger.error("No se encontró el token de autorización");
            throw new RuntimeException("No se pudo consultar los anexos. No se encontró el accessToken.");
        }

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            String url = BASE_URL + "/report/" + reportHashId.getValue() + "/annexes";
            logger.info("Consultando anexos en: " + url);

            @SuppressWarnings("rawtypes")
            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    request,
                    List.class);

            logger.info("Respuesta recibida del backend - Status: " + response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> annexesData = (List<Map<String, Object>>) response.getBody();

                List<DiagnosticReport> diagnosticReports = new ArrayList<>();

                for (Map<String, Object> annexData : annexesData) {
                    DiagnosticReport diagnosticReport = convertAnnexToDiagnosticReport(annexData,
                            reportHashId.getValue());
                    diagnosticReports.add(diagnosticReport);
                }

                logger.info("Se encontraron " + diagnosticReports.size() + " anexos para el reporte");
                return diagnosticReports;
            } else {
                logger.warn("No se encontraron anexos o error en la respuesta: " + response.getStatusCode());
                return new ArrayList<>();
            }

        } catch (Exception e) {
            logger.error("Error al consultar los anexos: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                    "No se pudieron consultar los anexos: " + e.getMessage());
        }
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

    /**
     * Convierte los datos del backend al formato FHIR R5 DiagnosticReport
     */
    private DiagnosticReport convertToDiagnosticReport(Map<String, Object> reportData, String patientHashId) {
        DiagnosticReport diagnosticReport = new DiagnosticReport();

        // Log para debuggear los datos recibidos
        logger.info("Convirtiendo reporte con datos: " + reportData.toString());

        // Extraer el objeto report del wrapper
        Map<String, Object> report = null;
        if (reportData.containsKey("report") && reportData.get("report") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> reportMap = (Map<String, Object>) reportData.get("report");
            report = reportMap;
        } else {
            // Si no hay wrapper, usar los datos directamente
            report = reportData;
        }

        // Establecer ID
        if (report != null && report.containsKey("id_informe") && report.get("id_informe") != null) {
            diagnosticReport.setId(report.get("id_informe").toString());
        } else {
            // Generar un ID temporal si no hay ID del backend
            String tempId = "temp-" + System.currentTimeMillis() + "-" + Math.random();
            diagnosticReport.setId(tempId);
            logger.warn("No se encontró id_informe en los datos del backend, usando ID temporal: " + tempId);
        }

        // Establecer estado
        diagnosticReport.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);

        // Establecer código (título del reporte)
        CodeableConcept code = new CodeableConcept();
        if (report != null && report.containsKey("titulo") && report.get("titulo") != null) {
            code.setText(report.get("titulo").toString());
        } else {
            code.setText("Reporte de Diagnóstico");
        }
        diagnosticReport.setCode(code);

        // Establecer conclusión (contenido del reporte)
        if (report != null && report.containsKey("reporte") && report.get("reporte") != null) {
            diagnosticReport.setConclusion(report.get("reporte").toString());
        }

        // Establecer fecha de creación
        if (report != null && report.containsKey("fecha_creacion") && report.get("fecha_creacion") != null) {
            try {
                String fechaStr = report.get("fecha_creacion").toString();
                // Convertir timestamp a DateTimeType
                DateTimeType effectiveDateTime = new DateTimeType(fechaStr);
                diagnosticReport.setEffective(effectiveDateTime);
            } catch (Exception e) {
                logger.warn("Error al convertir fecha de creación: " + e.getMessage());
            }
        }

        // Establecer referencia al paciente usando el hashId
        if (patientHashId != null && !patientHashId.isEmpty()) {
            Reference subject = new Reference();
            subject.setReference("Patient/" + patientHashId);
            diagnosticReport.setSubject(subject);
        }

        // Agregar extensiones con información adicional
        if (report != null) {
            addExtensions(diagnosticReport, report);
        }

        // NO procesar anexos aquí - se obtendrán por separado
        // Los anexos se obtendrán mediante un endpoint específico

        return diagnosticReport;
    }

    /**
     * Convierte un anexo del backend al formato FHIR R5 DiagnosticReport
     */
    private DiagnosticReport convertAnnexToDiagnosticReport(Map<String, Object> annexData, String reportHashId) {
        DiagnosticReport diagnosticReport = new DiagnosticReport();

        // Establecer ID del anexo
        if (annexData.containsKey("hash_id") && annexData.get("hash_id") != null) {
            diagnosticReport.setId(annexData.get("hash_id").toString());
        } else {
            String tempId = "annex-temp-" + System.currentTimeMillis() + "-" + Math.random();
            diagnosticReport.setId(tempId);
        }

        // Establecer estado
        diagnosticReport.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);

        // Establecer código (título del anexo)
        CodeableConcept code = new CodeableConcept();
        if (annexData.containsKey("fecha_creacion") && annexData.get("fecha_creacion") != null) {
            code.setText("Comentario - " + annexData.get("fecha_creacion").toString());
        } else {
            code.setText("Comentario");
        }
        diagnosticReport.setCode(code);

        // Establecer conclusión (contenido del anexo)
        if (annexData.containsKey("reporte") && annexData.get("reporte") != null) {
            diagnosticReport.setConclusion(annexData.get("reporte").toString());
        }

        // Establecer fecha de creación
        if (annexData.containsKey("fecha_creacion") && annexData.get("fecha_creacion") != null) {
            try {
                String fechaStr = annexData.get("fecha_creacion").toString();
                DateTimeType effectiveDateTime = new DateTimeType(fechaStr);
                diagnosticReport.setEffective(effectiveDateTime);
            } catch (Exception e) {
                logger.warn("Error al convertir fecha de creación del anexo: " + e.getMessage());
            }
        }

        // Establecer referencia al reporte padre
        Reference subject = new Reference();
        subject.setReference("DiagnosticReport/" + reportHashId);
        diagnosticReport.setSubject(subject);

        // Agregar extensión para indicar que es un anexo
        Extension annexExtension = new Extension();
        annexExtension.setUrl("http://example.org/fhir/StructureDefinition/is-annex");
        annexExtension.setValue(new org.hl7.fhir.r5.model.BooleanType(true));
        diagnosticReport.addExtension(annexExtension);

        // Agregar extensiones con información adicional del anexo
        addAnnexExtensions(diagnosticReport, annexData);

        return diagnosticReport;
    }

    /**
     * Agrega extensiones al DiagnosticReport con información adicional del backend
     */
    private void addExtensions(DiagnosticReport diagnosticReport, Map<String, Object> report) {
        // HashId del reporte (nuevo campo)
        if (report.containsKey("hash_id") && report.get("hash_id") != null) {
            logger.info("HashId del reporte: " + report.get("hash_id").toString());
            Extension hashIdExtension = new Extension();
            hashIdExtension.setUrl("http://example.org/fhir/StructureDefinition/report-hash-id");
            hashIdExtension.setValue(new org.hl7.fhir.r5.model.StringType(report.get("hash_id").toString()));
            diagnosticReport.addExtension(hashIdExtension);
        }

        // Información del usuario
        if (report.containsKey("nombre_usuario") && report.get("nombre_usuario") != null) {
            Extension userNameExtension = new Extension();
            userNameExtension.setUrl("http://example.org/fhir/StructureDefinition/user-name");
            userNameExtension.setValue(new org.hl7.fhir.r5.model.StringType(report.get("nombre_usuario").toString()));
            diagnosticReport.addExtension(userNameExtension);
        }

        if (report.containsKey("apellido_usuario") && report.get("apellido_usuario") != null) {
            Extension userLastNameExtension = new Extension();
            userLastNameExtension
                    .setValue(new org.hl7.fhir.r5.model.StringType(report.get("apellido_usuario").toString()));
            diagnosticReport.addExtension(userLastNameExtension);
        }

        if (report.containsKey("dni_usuario") && report.get("dni_usuario") != null) {
            Extension userDniExtension = new Extension();
            userDniExtension.setUrl("http://example.org/fhir/StructureDefinition/user-dni");
            userDniExtension.setValue(new org.hl7.fhir.r5.model.StringType(report.get("dni_usuario").toString()));
            diagnosticReport.addExtension(userDniExtension);
        }

        // Información del tipo de informe
        if (report.containsKey("nombre_tipo_informe") && report.get("nombre_tipo_informe") != null) {
            Extension reportTypeNameExtension = new Extension();
            reportTypeNameExtension.setUrl("http://example.org/fhir/StructureDefinition/report-type-name");
            reportTypeNameExtension
                    .setValue(new org.hl7.fhir.r5.model.StringType(report.get("nombre_tipo_informe").toString()));
            diagnosticReport.addExtension(reportTypeNameExtension);
        }

        // Campos legacy para compatibilidad (si existen)
        if (report.containsKey("id_usuario") && report.get("id_usuario") != null) {
            Extension userExtension = new Extension();
            userExtension.setUrl("http://example.org/fhir/StructureDefinition/user-id");
            userExtension.setValue(new org.hl7.fhir.r5.model.StringType(report.get("id_usuario").toString()));
            diagnosticReport.addExtension(userExtension);
        }

        if (report.containsKey("id_tipo_informe") && report.get("id_tipo_informe") != null) {
            Extension reportTypeExtension = new Extension();
            reportTypeExtension.setUrl("http://example.org/fhir/StructureDefinition/report-type-id");
            reportTypeExtension
                    .setValue(new org.hl7.fhir.r5.model.StringType(report.get("id_tipo_informe").toString()));
            diagnosticReport.addExtension(reportTypeExtension);
        }

        if (report.containsKey("id_historia_clinica") && report.get("id_historia_clinica") != null) {
            Extension ehrExtension = new Extension();
            ehrExtension.setUrl("http://example.org/fhir/StructureDefinition/ehr-id");
            ehrExtension.setValue(new org.hl7.fhir.r5.model.StringType(report.get("id_historia_clinica").toString()));
            diagnosticReport.addExtension(ehrExtension);
        }
    }

    /**
     * Agrega extensiones específicas para anexos
     */
    private void addAnnexExtensions(DiagnosticReport diagnosticReport, Map<String, Object> annexData) {
        // ID del anexo
        if (annexData.containsKey("id_anexo") && annexData.get("id_anexo") != null) {
            Extension annexIdExtension = new Extension();
            annexIdExtension.setUrl("http://example.org/fhir/StructureDefinition/annex-id");
            annexIdExtension.setValue(new org.hl7.fhir.r5.model.StringType(annexData.get("id_anexo").toString()));
            diagnosticReport.addExtension(annexIdExtension);
        }

        // ID del informe padre
        if (annexData.containsKey("id_informe") && annexData.get("id_informe") != null) {
            Extension reportIdExtension = new Extension();
            reportIdExtension.setUrl("http://example.org/fhir/StructureDefinition/report-type-id");
            reportIdExtension.setValue(new org.hl7.fhir.r5.model.StringType(annexData.get("id_informe").toString()));
            diagnosticReport.addExtension(reportIdExtension);
        }

        // ID del usuario
        if (annexData.containsKey("id_usuario") && annexData.get("id_usuario") != null) {
            Extension userExtension = new Extension();
            userExtension.setUrl("http://example.org/fhir/StructureDefinition/user-id");
            userExtension.setValue(new org.hl7.fhir.r5.model.StringType(annexData.get("id_usuario").toString()));
            diagnosticReport.addExtension(userExtension);
        }

        // Hash ID del anexo
        if (annexData.containsKey("hash_id") && annexData.get("hash_id") != null) {
            Extension hashIdExtension = new Extension();
            hashIdExtension.setUrl("http://example.org/fhir/StructureDefinition/report-hash-id");
            hashIdExtension.setValue(new org.hl7.fhir.r5.model.StringType(annexData.get("hash_id").toString()));
            diagnosticReport.addExtension(hashIdExtension);
        }

        // Información del usuario
        if (annexData.containsKey("nombre_usuario") && annexData.get("nombre_usuario") != null) {
            Extension userNameExtension = new Extension();
            userNameExtension.setUrl("http://example.org/fhir/StructureDefinition/user-name");
            userNameExtension
                    .setValue(new org.hl7.fhir.r5.model.StringType(annexData.get("nombre_usuario").toString()));
            diagnosticReport.addExtension(userNameExtension);
        }

        if (annexData.containsKey("apellido_usuario") && annexData.get("apellido_usuario") != null) {
            Extension userLastNameExtension = new Extension();
            userLastNameExtension.setUrl("http://example.org/fhir/StructureDefinition/user-lastname");
            userLastNameExtension
                    .setValue(new org.hl7.fhir.r5.model.StringType(annexData.get("apellido_usuario").toString()));
            diagnosticReport.addExtension(userLastNameExtension);
        }
    }
}
