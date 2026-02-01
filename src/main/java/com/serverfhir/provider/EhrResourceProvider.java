package com.serverfhir.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r5.model.DiagnosticReport;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.DateTimeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleType;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

@Component
public class EhrResourceProvider implements IResourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(EhrResourceProvider.class);

    @Value("${tfback.url}")
    private String tfBackUrl;

    @Value("${tfback.api.path}")
    private String tfBackApiPath;

    private String buildBackendUrl(String path) {
        return tfBackUrl + tfBackApiPath + path;
    }
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Class<DiagnosticReport> getResourceType() {
        return DiagnosticReport.class;
    }

    // ========== HISTORIA FISIATRICA RESOURCES ==========

    @Search
    public List<DiagnosticReport> searchHistoriaFisiatrica(
        @RequiredParam(name = "patient") StringParam patientId,
        RequestDetails requestDetails) {

        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            logger.info("patientId", patientId.getValue());

            // Llamar al endpoint del backend para obtener la historia fisiatrica
            String backendUrl = buildBackendUrl("/ehr/hc-fisiatric/" + patientId.getValue());
            logger.info("Llamando al backend con URL: " + backendUrl);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                backendUrl,
                HttpMethod.GET,
                entity,
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            Map<String, Object> hcFisiatrica = response.getBody();
            if (hcFisiatrica == null) {
                return new ArrayList<>();
            }

            // Convertir la historia fisiatrica a un DiagnosticReport FHIR
            DiagnosticReport diagnosticReport = convertToDiagnosticReport(hcFisiatrica, patientId.getValue());
            
            List<DiagnosticReport> reports = new ArrayList<>();
            reports.add(diagnosticReport);
            return reports;

        } catch (Exception e) {
            logger.error("Error en searchHistoriaFisiatrica: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // Custom operation to get historia by patient via a distinct endpoint:
    // GET /fhir/DiagnosticReport/$get-historia?patient={hashId}
    @Operation(name = "$get-historia", idempotent = true, type = DiagnosticReport.class)
    public Bundle getHistoriaOperation(
        @OperationParam(name = "patient") StringType patientId,
        RequestDetails requestDetails
    ) {
        StringParam sp = (patientId != null && patientId.hasValue())
            ? new StringParam(patientId.getValue())
            : null;
        List<DiagnosticReport> reports = searchHistoriaFisiatrica(sp, requestDetails);
        Bundle bundle = new Bundle();
        bundle.setType(BundleType.SEARCHSET);
        for (DiagnosticReport dr : reports) {
            bundle.addEntry().setResource(dr);
        }
        return bundle;
    }

    @Read
    public DiagnosticReport readHistoriaFisiatrica(@IdParam IdType id, RequestDetails requestDetails) {
        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                buildBackendUrl("/ehr/hc-fisiatric"), 
                HttpMethod.POST, 
                entity, 
                (Class<Map<String, Object>>) (Class<?>) Map.class,
                Map.of("ehrHashId", id.getIdPart())
            );

            Map<String, Object> hcFisiatrica = response.getBody();
            if (hcFisiatrica == null) {
                throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException("Historia Fisiatrica no encontrada: " + id.getIdPart());
            }

            return convertToDiagnosticReport(hcFisiatrica, id.getIdPart());

        } catch (Exception e) {
            if (e instanceof ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException) {
                throw e;
            }
            logger.error("Error al obtener historia fisiatrica: " + e.getMessage());
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException("Error interno del servidor");
        }
    }

    @Create
    public MethodOutcome createHistoriaFisiatrica(
        @ResourceParam DiagnosticReport diagnosticReport,
        RequestDetails requestDetails) {
        
        try {
            logger.info("Creando nueva historia fisiatrica...");
            
            // Extraer el ID del paciente de la referencia del subject
            String patientId = extractPatientId(diagnosticReport.getSubject());
            if (patientId == null) {
                throw new IllegalArgumentException("No se pudo extraer el ID del paciente");
            }
            
            // Transformar el recurso FHIR a formato del backend
            Map<String, Object> backendData;
            try {
                backendData = transformToBackendFormat(diagnosticReport, patientId);
            } catch (JsonProcessingException e) {
                logger.error("Error al transformar datos a JSON: " + e.getMessage());
                throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException("Error al procesar datos JSON");
            }
            
            // Enviar al backend
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            logger.info("Enviando datos al backend: " + backendData);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(backendData, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                buildBackendUrl("/ehr/hc-fisiatric"),
                HttpMethod.POST,
                entity,
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Historia fisiatrica creada exitosamente en backend");
                
                // Asignar un ID al recurso FHIR si no lo tiene
                if (diagnosticReport.getId() == null || diagnosticReport.getId().isEmpty()) {
                    diagnosticReport.setId(IdType.newRandomUuid());
                }
                
                // Crear MethodOutcome con el recurso creado
                MethodOutcome outcome = new MethodOutcome();
                outcome.setResource(diagnosticReport);
                outcome.setCreated(true);
                outcome.setId(diagnosticReport.getIdElement());
                
                return outcome;
            } else {
                logger.error("Error al crear historia fisiatrica en backend: " + response.getStatusCode());
                throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException("Error al crear historia en backend");
            }
            
        } catch (Exception e) {
            logger.error("Error al crear historia fisiatrica: " + e.getMessage());
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException("Error interno del servidor: " + e.getMessage());
        }
    }

    // Custom operation to explicitly create historia via a distinct endpoint:
    // POST /fhir/DiagnosticReport/$create-historia
    @Operation(name = "$create-historia", type = DiagnosticReport.class)
    public MethodOutcome createHistoriaOperation(
        @ResourceParam DiagnosticReport diagnosticReport,
        RequestDetails requestDetails
    ) {
        return createHistoriaFisiatrica(diagnosticReport, requestDetails);
    }


    /**
     * Convierte los datos de la historia fisiatrica de la base de datos a un DiagnosticReport FHIR
     */
    private DiagnosticReport convertToDiagnosticReport(Map<String, Object> hcFisiatrica, String patientId) {
        DiagnosticReport report = new DiagnosticReport();
        
        // ID del recurso
        report.setId(hcFisiatrica.get("id_hc_fisiatrica").toString());
        
        // Estado del reporte
        report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
        
        // Código del reporte (Historia Fisiatrica)
        CodeableConcept code = new CodeableConcept();
        code.setText("Historia Clínica Fisiátrica");
        code.addCoding()
            .setSystem("http://loinc.org")
            .setCode("11450-4")
            .setDisplay("Problem List Reported");
        report.setCode(code);
        
        // Fecha de creación
        if (hcFisiatrica.get("fecha_creacion") != null) {
            report.setEffective(new DateTimeType(hcFisiatrica.get("fecha_creacion").toString()));
        }
        
        // Referencia al paciente
        Reference subject = new Reference();
        subject.setReference("Patient/" + patientId);
        report.setSubject(subject);
        
        // Conclusión (resumen de la historia fisiatrica)
        StringBuilder conclusion = new StringBuilder();
        conclusion.append("HISTORIA CLÍNICA FISIÁTRICA\n\n");
        
        // Evaluación y Consulta
        if (hcFisiatrica.get("evaluacion_consulta") != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> evaluacionConsulta = objectMapper.readValue(
                    hcFisiatrica.get("evaluacion_consulta").toString(), 
                    Map.class
                );
                
                if (evaluacionConsulta.get("derivadosPor") != null && !evaluacionConsulta.get("derivadosPor").toString().isEmpty()) {
                    conclusion.append("DERIVADOS POR:\n");
                    conclusion.append(evaluacionConsulta.get("derivadosPor").toString()).append("\n\n");
                }
                
                if (evaluacionConsulta.get("medicacionActual") != null && !evaluacionConsulta.get("medicacionActual").toString().isEmpty()) {
            conclusion.append("MEDICACIÓN ACTUAL:\n");
                    conclusion.append(evaluacionConsulta.get("medicacionActual").toString()).append("\n\n");
                }
                
                if (evaluacionConsulta.get("antecedentesCuadro") != null && !evaluacionConsulta.get("antecedentesCuadro").toString().isEmpty()) {
                    conclusion.append("ANTECEDENTES DEL CUADRO ACTUAL:\n");
                    conclusion.append(evaluacionConsulta.get("antecedentesCuadro").toString()).append("\n\n");
                }
                
                if (evaluacionConsulta.get("estudiosRealizados") != null && !evaluacionConsulta.get("estudiosRealizados").toString().isEmpty()) {
            conclusion.append("ESTUDIOS REALIZADOS:\n");
                    conclusion.append(evaluacionConsulta.get("estudiosRealizados").toString()).append("\n\n");
                }
            } catch (Exception e) {
                logger.warn("Error parseando evaluacion_consulta: " + e.getMessage());
            }
        }
        
        // Datos fisiológicos (JSON)
        if (hcFisiatrica.get("fisiologico") != null) {
            conclusion.append("DATOS FISIOLÓGICOS:\n");
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> fisiologico = objectMapper.readValue(
                    hcFisiatrica.get("fisiologico").toString(), 
                    Map.class
                );
                appendJsonData(conclusion, fisiologico);
            } catch (Exception e) {
                conclusion.append(hcFisiatrica.get("fisiologico").toString()).append("\n");
            }
            conclusion.append("\n");
        }
        
        // Anamnesis sistémica (JSON)
        if (hcFisiatrica.get("anamnesis_sistemica") != null) {
            conclusion.append("ANAMNESIS SISTÉMICA:\n");
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> anamnesis = objectMapper.readValue(
                    hcFisiatrica.get("anamnesis_sistemica").toString(), 
                    Map.class
                );
                appendJsonData(conclusion, anamnesis);
            } catch (Exception e) {
                conclusion.append(hcFisiatrica.get("anamnesis_sistemica").toString()).append("\n");
            }
            conclusion.append("\n");
        }
        
        // Examen físico (JSON)
        if (hcFisiatrica.get("examen_fisico") != null) {
            conclusion.append("EXAMEN FÍSICO:\n");
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> examen = objectMapper.readValue(
                    hcFisiatrica.get("examen_fisico").toString(), 
                    Map.class
                );
                appendJsonData(conclusion, examen);
            } catch (Exception e) {
                conclusion.append(hcFisiatrica.get("examen_fisico").toString()).append("\n");
            }
            conclusion.append("\n");
        }
        
        // Diagnóstico funcional
        if (hcFisiatrica.get("diagnostico_funcional") != null) {
            conclusion.append("DIAGNÓSTICO FUNCIONAL:\n");
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> diagnostico = objectMapper.readValue(
                    hcFisiatrica.get("diagnostico_funcional").toString(), 
                    Map.class
                );
                appendJsonData(conclusion, diagnostico);
            } catch (Exception e) {
                conclusion.append(hcFisiatrica.get("diagnostico_funcional").toString()).append("\n");
            }
            conclusion.append("\n");
        }
        
        // Conducta a seguir
        if (hcFisiatrica.get("conducta_seguir") != null) {
            conclusion.append("CONDUCTA A SEGUIR:\n");
            conclusion.append(hcFisiatrica.get("conducta_seguir").toString()).append("\n");
        }
        
        report.setConclusion(conclusion.toString());
        
        // Extensiones personalizadas
        List<Extension> extensions = new ArrayList<>();
        
        // Extensión para el tipo de historia
        Extension tipoExtension = new Extension();
        tipoExtension.setUrl("http://mi-servidor.com/fhir/StructureDefinition/historia-tipo");
        tipoExtension.setValue(new StringType("fisiatrica"));
        extensions.add(tipoExtension);
        
        // Mapear campos de evaluacion_consulta a extensiones FHIR
        logger.info("hcFisiatrica.get('evaluacion_consulta')", hcFisiatrica.get("evaluacion_consulta"));
        if (hcFisiatrica.get("evaluacion_consulta") != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> evaluacionConsulta = objectMapper.readValue(
                    hcFisiatrica.get("evaluacion_consulta").toString(), 
                    Map.class
                );
                
                // Derivados por
                if (evaluacionConsulta.get("derivadosPor") != null) {
                    Extension ext = new Extension();
                    ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/derivados-por");
                    ext.setValue(new StringType(evaluacionConsulta.get("derivadosPor").toString()));
                    extensions.add(ext);
                }
                
                // Medicación actual
                if (evaluacionConsulta.get("medicacionActual") != null) {
                    Extension ext = new Extension();
                    ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/medicacion-actual");
                    ext.setValue(new StringType(evaluacionConsulta.get("medicacionActual").toString()));
                    extensions.add(ext);
                }
                
                // Antecedentes del cuadro
                if (evaluacionConsulta.get("antecedentesCuadro") != null) {
                    Extension ext = new Extension();
                    ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/antecedentes-cuadro");
                    ext.setValue(new StringType(evaluacionConsulta.get("antecedentesCuadro").toString()));
                    extensions.add(ext);
                }
                
                // Estudios realizados
                if (evaluacionConsulta.get("estudiosRealizados") != null) {
                    Extension ext = new Extension();
                    ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/estudios-realizados");
                    ext.setValue(new StringType(evaluacionConsulta.get("estudiosRealizados").toString()));
                    extensions.add(ext);
                }
            } catch (Exception e) {
                logger.warn("Error parseando evaluacion_consulta para extensiones: " + e.getMessage());
            }
        }
        
        // Mapear campos de antecedentes
        if (hcFisiatrica.get("antecedentes") != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> antecedentes = objectMapper.readValue(
                    hcFisiatrica.get("antecedentes").toString(), 
                    Map.class
                );
                
                // Antecedentes hereditarios
                if (antecedentes.get("hereditarios") != null) {
                    Extension ext = new Extension();
                    ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/antecedentes-hereditarios");
                    ext.setValue(new StringType(antecedentes.get("hereditarios").toString()));
                    extensions.add(ext);
                }
                
                // Antecedentes patológicos
                if (antecedentes.get("patologicos") != null) {
                    Extension ext = new Extension();
                    ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/antecedentes-patologicos");
                    ext.setValue(new StringType(antecedentes.get("patologicos").toString()));
                    extensions.add(ext);
                }
                
                // Antecedentes quirúrgicos
                if (antecedentes.get("quirurgicos") != null) {
                    Extension ext = new Extension();
                    ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/antecedentes-quirurgicos");
                    ext.setValue(new StringType(antecedentes.get("quirurgicos").toString()));
                    extensions.add(ext);
                }
                
                // Antecedentes metabólicos
                if (antecedentes.get("metabolicos") != null) {
                    Extension ext = new Extension();
                    ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/antecedentes-metabolicos");
                    ext.setValue(new StringType(antecedentes.get("metabolicos").toString()));
                    extensions.add(ext);
                }
                
                // Antecedentes inmunológicos
                if (antecedentes.get("inmunologicos") != null) {
                    Extension ext = new Extension();
                    ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/antecedentes-inmunologicos");
                    ext.setValue(new StringType(antecedentes.get("inmunologicos").toString()));
                    extensions.add(ext);
                }
                
                // Fisiológicos (dentro de antecedentes)
                if (antecedentes.get("fisiologico") != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fisiologicos = (Map<String, Object>) antecedentes.get("fisiologico");
                    
                    if (fisiologicos.get("dormir") != null) {
                        Extension ext = new Extension();
                        ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/fisiologicos-dormir");
                        ext.setValue(new StringType(fisiologicos.get("dormir").toString()));
                        extensions.add(ext);
                    }
                    
                    if (fisiologicos.get("alimentacion") != null) {
                        Extension ext = new Extension();
                        ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/fisiologicos-alimentacion");
                        ext.setValue(new StringType(fisiologicos.get("alimentacion").toString()));
                        extensions.add(ext);
                    }
                    
                    if (fisiologicos.get("catarsis") != null) {
                        Extension ext = new Extension();
                        ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/fisiologicos-catarsis");
                        ext.setValue(new StringType(fisiologicos.get("catarsis").toString()));
                        extensions.add(ext);
                    }
                    
                    if (fisiologicos.get("diuresis") != null) {
                        Extension ext = new Extension();
                        ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/fisiologicos-diuresis");
                        ext.setValue(new StringType(fisiologicos.get("diuresis").toString()));
                        extensions.add(ext);
                    }
                    
                    if (fisiologicos.get("periodoMenstrual") != null) {
                        Extension ext = new Extension();
                        ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/fisiologicos-periodo-menstrual");
                        ext.setValue(new StringType(fisiologicos.get("periodoMenstrual").toString()));
                        extensions.add(ext);
                    }
                    
                    if (fisiologicos.get("sexualidad") != null) {
                        Extension ext = new Extension();
                        ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/fisiologicos-sexualidad");
                        ext.setValue(new StringType(fisiologicos.get("sexualidad").toString()));
                        extensions.add(ext);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error parseando antecedentes para extensiones: " + e.getMessage());
            }
        }
        
        // Mapear campos de diagnostico_funcional
        if (hcFisiatrica.get("diagnostico_funcional") != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> diagnosticoFuncional = objectMapper.readValue(
                    hcFisiatrica.get("diagnostico_funcional").toString(), 
                    Map.class
                );
                
                // Diagnóstico funcional
                if (diagnosticoFuncional.get("diagnosticoFuncional") != null) {
                    Extension ext = new Extension();
                    ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/diagnostico-funcional");
                    ext.setValue(new StringType(diagnosticoFuncional.get("diagnosticoFuncional").toString()));
                    extensions.add(ext);
                }
                
                // Conducta a seguir
                if (diagnosticoFuncional.get("conductaSeguir") != null) {
                    Extension ext = new Extension();
                    ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/conducta-objetivos");
                    ext.setValue(new StringType(diagnosticoFuncional.get("conductaSeguir").toString()));
                    extensions.add(ext);
                }
                
                // Objetivos familia
                if (diagnosticoFuncional.get("objetivosFamilia") != null) {
                    Extension ext = new Extension();
                    ext.setUrl("http://mi-servidor.com/fhir/StructureDefinition/objetivos-familia");
                    ext.setValue(new StringType(diagnosticoFuncional.get("objetivosFamilia").toString()));
                    extensions.add(ext);
                }
            } catch (Exception e) {
                logger.warn("Error parseando diagnostico_funcional para extensiones: " + e.getMessage());
            }
        }
        
        report.setExtension(extensions);
        
        return report;
    }
    
    /**
     * Ayuda a formatear datos JSON en el texto de conclusión
     */
    private void appendJsonData(StringBuilder conclusion, Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey().replaceAll("_", " ").toUpperCase();
            Object value = entry.getValue();
            if (value != null && !value.toString().trim().isEmpty()) {
                conclusion.append("- ").append(key).append(": ").append(value.toString()).append("\n");
            }
        }
    }
    
    /**
     * Extrae el ID del paciente de la referencia del subject
     */
    private String extractPatientId(Reference subject) {
        if (subject == null || subject.getReference() == null) {
            return null;
        }
        
        String reference = subject.getReference();
        if (reference.startsWith("Patient/")) {
            return reference.substring("Patient/".length());
        }
        
        return null;
    }
    
    /**
     * Transforma un recurso FHIR DiagnosticReport al formato esperado por el backend
     */
    private Map<String, Object> transformToBackendFormat(DiagnosticReport diagnosticReport, String patientId) throws JsonProcessingException {
        Map<String, Object> backendData = new java.util.HashMap<>();
        
        // Estructura básica esperada por el backend
        backendData.put("hash_id", patientId);
        
        Map<String, Object> hcFisiatric = new java.util.HashMap<>();
        
        // Extraer datos de las extensiones
        Map<String, String> extensionData = extractExtensionData(diagnosticReport);
        
        // 1. EVALUACIÓN Y CONSULTA
        Map<String, Object> evaluacionConsulta = new java.util.HashMap<>();
        evaluacionConsulta.put("derivadosPor", extensionData.getOrDefault("derivados-por", ""));
        evaluacionConsulta.put("medicacionActual", extensionData.getOrDefault("medicacion-actual", ""));
        evaluacionConsulta.put("antecedentesCuadro", extensionData.getOrDefault("antecedentes-cuadro", ""));
        evaluacionConsulta.put("estudiosRealizados", extensionData.getOrDefault("estudios-realizados", ""));
        hcFisiatric.put("evaluacionConsulta", evaluacionConsulta);
        
        // 2. ANTECEDENTES
        Map<String, Object> antecedentes = new java.util.HashMap<>();
        antecedentes.put("hereditarios", extensionData.getOrDefault("antecedentes-hereditarios", ""));
        antecedentes.put("patologicos", extensionData.getOrDefault("antecedentes-patologicos", ""));
        antecedentes.put("quirurgicos", extensionData.getOrDefault("antecedentes-quirurgicos", ""));
        antecedentes.put("metabolicos", extensionData.getOrDefault("antecedentes-metabolicos", ""));
        antecedentes.put("inmunologicos", extensionData.getOrDefault("antecedentes-inmunologicos", ""));
        antecedentes.put("cuadro", extensionData.getOrDefault("antecedentes-cuadro", ""));
        hcFisiatric.put("antecedentes", antecedentes);
        
        // 3. FISIOLÓGICOS (dentro de antecedentes)
        Map<String, Object> fisiologicos = new java.util.HashMap<>();
        fisiologicos.put("dormir", extensionData.getOrDefault("fisiologicos-dormir", ""));
        fisiologicos.put("alimentacion", extensionData.getOrDefault("fisiologicos-alimentacion", ""));
        fisiologicos.put("catarsis", extensionData.getOrDefault("fisiologicos-catarsis", ""));
        fisiologicos.put("diuresis", extensionData.getOrDefault("fisiologicos-diuresis", ""));
        fisiologicos.put("periodoMenstrual", extensionData.getOrDefault("fisiologicos-periodo-menstrual", ""));
        fisiologicos.put("sexualidad", extensionData.getOrDefault("fisiologicos-sexualidad", ""));
        antecedentes.put("fisiologico", fisiologicos);
        
        // 4. ANAMNESIS SISTÉMICA
        Map<String, Object> anamnesisSistemica = new java.util.HashMap<>();
        anamnesisSistemica.put("comunicacion", extensionData.getOrDefault("anamnesis-comunicacion", ""));
        anamnesisSistemica.put("motricidad", extensionData.getOrDefault("anamnesis-motricidad", ""));
        anamnesisSistemica.put("vidaDiaria", extensionData.getOrDefault("anamnesis-vida-diaria", ""));
        hcFisiatric.put("anamnesisSistemica", anamnesisSistemica);
        
        // 5. EXAMEN FÍSICO
        Map<String, Object> examenFisico = buildExamenFisicoFromExtensions(extensionData);
        hcFisiatric.put("examenFisico", examenFisico);
        
        // 6. DIAGNÓSTICO FUNCIONAL
        Map<String, Object> diagnosticoFuncional = new java.util.HashMap<>();
        diagnosticoFuncional.put("diagnosticoFuncional", extensionData.getOrDefault("diagnostico-funcional", ""));
        diagnosticoFuncional.put("conductaSeguir", extensionData.getOrDefault("conducta-objetivos", ""));
        diagnosticoFuncional.put("objetivosFamilia", extensionData.getOrDefault("objetivos-familia", ""));
        
        hcFisiatric.put("diagnosticoFuncional", diagnosticoFuncional);
        
        backendData.put("hc_fisiatric", hcFisiatric);
        
        return backendData;
    }
    
    /**
     * Extrae datos de las extensiones del recurso FHIR
     */
    private Map<String, String> extractExtensionData(DiagnosticReport diagnosticReport) {
        Map<String, String> extensionData = new java.util.HashMap<>();
        
        if (diagnosticReport.getExtension() != null) {
            for (Extension extension : diagnosticReport.getExtension()) {
                String url = extension.getUrl();
                if (url != null && extension.getValue() instanceof StringType) {
                    String key = url.substring(url.lastIndexOf("/") + 1);
                    String value = ((StringType) extension.getValue()).getValue();
                    extensionData.put(key, value);
                }
            }
        }
        
        return extensionData;
    }
    
    /**
     * Construye la estructura del examen físico desde las extensiones
     */
    private Map<String, Object> buildExamenFisicoFromExtensions(Map<String, String> extensionData) {
        Map<String, Object> examenFisico = new java.util.HashMap<>();
        
        // General
        Map<String, Object> general = new java.util.HashMap<>();
        general.put("actitud", extensionData.getOrDefault("examen-actitud", ""));
        general.put("comunicacionCodigos", extensionData.getOrDefault("examen-comunicacion-codigos", ""));
        general.put("pielFaneras", extensionData.getOrDefault("examen-piel-faneras", ""));
        examenFisico.put("general", general);
        
        // Cabeza y sentidos
        Map<String, Object> cabezaSentidos = new java.util.HashMap<>();
        cabezaSentidos.put("cabeza", extensionData.getOrDefault("examen-cabeza", ""));
        cabezaSentidos.put("ojos", extensionData.getOrDefault("examen-ojos", ""));
        cabezaSentidos.put("movimientosAnormales", extensionData.getOrDefault("examen-movimientos-anormales", ""));
        cabezaSentidos.put("estrabismo", extensionData.getOrDefault("examen-estrabismo", ""));
        cabezaSentidos.put("orejas", extensionData.getOrDefault("examen-orejas", ""));
        cabezaSentidos.put("audicion", extensionData.getOrDefault("examen-audicion", ""));
        cabezaSentidos.put("boca", extensionData.getOrDefault("examen-boca", ""));
        cabezaSentidos.put("labios", extensionData.getOrDefault("examen-labios", ""));
        cabezaSentidos.put("lengua", extensionData.getOrDefault("examen-lengua", ""));
        cabezaSentidos.put("denticion", extensionData.getOrDefault("examen-denticion", ""));
        cabezaSentidos.put("mordida", extensionData.getOrDefault("examen-mordida", ""));
        cabezaSentidos.put("paladarVelo", extensionData.getOrDefault("examen-paladar-velo", ""));
        cabezaSentidos.put("maxilares", extensionData.getOrDefault("examen-maxilares", ""));
        examenFisico.put("cabezaSentidos", cabezaSentidos);
        
        // Tronco y extremidades
        Map<String, Object> troncoExtremidades = new java.util.HashMap<>();
        troncoExtremidades.put("torax", extensionData.getOrDefault("examen-torax", ""));
        troncoExtremidades.put("abdomen", extensionData.getOrDefault("examen-abdomen", ""));
        troncoExtremidades.put("columnaVertebral", extensionData.getOrDefault("examen-columna-vertebral", ""));
        troncoExtremidades.put("pelvis", extensionData.getOrDefault("examen-pelvis", ""));
        troncoExtremidades.put("caderas", extensionData.getOrDefault("examen-caderas", ""));
        troncoExtremidades.put("mmii", extensionData.getOrDefault("examen-mmii", ""));
        troncoExtremidades.put("pies", extensionData.getOrDefault("examen-pies", ""));
        troncoExtremidades.put("mmss", extensionData.getOrDefault("examen-mmss", ""));
        troncoExtremidades.put("manos", extensionData.getOrDefault("examen-manos", ""));
        troncoExtremidades.put("lateralidad", extensionData.getOrDefault("examen-lateralidad", ""));
        examenFisico.put("troncoExtremidades", troncoExtremidades);
        
        // Sistema y actividades
        Map<String, Object> sistemaActividades = new java.util.HashMap<>();
        sistemaActividades.put("apRespiratorio", extensionData.getOrDefault("examen-ap-respiratorio", ""));
        sistemaActividades.put("apCardiovascular", extensionData.getOrDefault("examen-ap-cardiovascular", ""));
        sistemaActividades.put("apDigestivo", extensionData.getOrDefault("examen-ap-digestivo", ""));
        sistemaActividades.put("actividadRefleja", extensionData.getOrDefault("examen-actividad-refleja", ""));
        sistemaActividades.put("actividadSensoperceptual", extensionData.getOrDefault("examen-actividad-sensoperceptual", ""));
        sistemaActividades.put("reaccionesPosturales", extensionData.getOrDefault("examen-reacciones-posturales", ""));
        sistemaActividades.put("desplazamientoMarcha", extensionData.getOrDefault("examen-desplazamiento-marcha", ""));
        sistemaActividades.put("etapaDesarrollo", extensionData.getOrDefault("examen-etapa-desarrollo", ""));
        examenFisico.put("sistemaActividades", sistemaActividades);
        
        return examenFisico;
    }
}
