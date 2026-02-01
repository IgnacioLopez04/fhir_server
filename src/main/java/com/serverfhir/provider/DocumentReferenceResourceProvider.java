package com.serverfhir.provider;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r5.model.DocumentReference;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.ResourceType;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleType;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.DocumentReference.DocumentReferenceContentComponent;
import org.hl7.fhir.r5.model.DocumentReference.DocumentReferenceStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DocumentReferenceResourceProvider implements IResourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(DocumentReferenceResourceProvider.class);

    @Value("${tfback.url}")
    private String tfBackUrl;

    @Value("${tfback.api.path}")
    private String tfBackApiPath;

    private String buildBackendUrl(String path) {
        return tfBackUrl + tfBackApiPath + path;
    }

    @Override
    public Class<DocumentReference> getResourceType() {
        return DocumentReference.class;
    }

    @Create
    public MethodOutcome createDocumentReference(@ResourceParam DocumentReference documentReference, RequestDetails requestDetails) {
        // Crear DocumentReference normal
        return createNormalDocumentReference(documentReference, requestDetails);
    }

    // Custom operation to get files by patient:
    // GET /fhir/DocumentReference/$get-files?patient={hashId}
    @Operation(name = "$get-files", idempotent = true, type = DocumentReference.class)
    public Bundle getFilesOperation(
            @OperationParam(name = "patient") StringType patientHashId,
            @OperationParam(name = "fileType") StringType fileType,
            RequestDetails requestDetails) {
        StringParam sp = (patientHashId != null && patientHashId.hasValue())
                ? new StringParam(patientHashId.getValue())
                : null;
        StringParam fileTypeParam = (fileType != null && fileType.hasValue())
                ? new StringParam(fileType.getValue())
                : null;
        List<DocumentReference> files = searchFiles(sp, fileTypeParam, requestDetails);
        Bundle bundle = new Bundle();
        bundle.setType(BundleType.SEARCHSET);
        for (DocumentReference dr : files) {
            bundle.addEntry().setResource(dr);
        }
        return bundle;
    }

    @Read
    public DocumentReference readDocumentReference(@IdParam IdType id, RequestDetails requestDetails) {
        // Implementación para leer un DocumentReference específico
        DocumentReference documentReference = new DocumentReference();
        documentReference.setId(id.getIdPart());
        documentReference.setStatus(DocumentReferenceStatus.CURRENT);
        
        CodeableConcept type = new CodeableConcept();
        type.setText("Archivo");
        documentReference.setType(type);
        
        return documentReference;
    }

    @Search
    public List<DocumentReference> searchFiles(
            @OptionalParam(name = "patient") StringParam patientHashId,
            @OptionalParam(name = "fileType") StringParam fileType,
            RequestDetails requestDetails) {

        logger.info("Buscando archivos para paciente con hashId: " +
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
            throw new RuntimeException("No se pudo consultar los archivos. No se encontró el accessToken.");
        }

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            String url = buildBackendUrl("/file") + "?hash_id=" + patientHashId.getValue();
            if (fileType != null && fileType.getValue() != null && !fileType.getValue().isEmpty()) {
                url += "&fileType=" + fileType.getValue();
            }
            
            logger.info("Consultando archivos en: " + url);

            @SuppressWarnings("rawtypes")
            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    request,
                    List.class);

            logger.info("Respuesta recibida del backend - Status: " + response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> filesData = (List<Map<String, Object>>) response.getBody();

                List<DocumentReference> documentReferences = new ArrayList<>();

                for (Map<String, Object> fileData : filesData) {
                    DocumentReference documentReference = convertToDocumentReference(fileData, patientHashId.getValue());
                    documentReferences.add(documentReference);
                }

                logger.info("Se encontraron " + documentReferences.size() + " archivos para el paciente");
                return documentReferences;
            } else {
                logger.warn("No se encontraron archivos o error en la respuesta: " + response.getStatusCode());
                return new ArrayList<>();
            }

        } catch (Exception e) {
            logger.error("Error al consultar los archivos: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                    "No se pudieron consultar los archivos: " + e.getMessage());
        }
    }

    private MethodOutcome createNormalDocumentReference(DocumentReference documentReference, RequestDetails requestDetails) {
        // Implementación para crear DocumentReference normal (sin archivos)
        // Por ahora, solo retornamos un resultado exitoso
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType(ResourceType.DocumentReference.name(), "doc-" + System.currentTimeMillis()));
        outcome.setCreated(true);
        return outcome;
    }

    /**
     * Convierte los datos del backend al formato FHIR R5 DocumentReference
     */
    private DocumentReference convertToDocumentReference(Map<String, Object> fileData, String patientHashId) {
        DocumentReference documentReference = new DocumentReference();

        // Establecer ID
        if (fileData.containsKey("id") && fileData.get("id") != null) {
            documentReference.setId(fileData.get("id").toString());
        } else {
            String tempId = "file-" + System.currentTimeMillis() + "-" + Math.random();
            documentReference.setId(tempId);
        }

        // Establecer estado
        documentReference.setStatus(DocumentReferenceStatus.CURRENT);

        // Establecer tipo de documento
        CodeableConcept type = new CodeableConcept();
        if (fileData.containsKey("type") && fileData.get("type") != null) {
            type.setText(fileData.get("type").toString());
        } else {
            type.setText("Archivo");
        }
        documentReference.setType(type);

        // Establecer contenido del documento
        if (fileData.containsKey("url") && fileData.get("url") != null) {
            DocumentReferenceContentComponent content = new DocumentReferenceContentComponent();
            content.getAttachment().setUrl(fileData.get("url").toString());
            documentReference.addContent(content);
        }

        // Establecer nombre del archivo
        if (fileData.containsKey("name") && fileData.get("name") != null) {
            documentReference.setDescription(fileData.get("name").toString());
        }

        // Establecer referencia al paciente
        if (patientHashId != null && !patientHashId.isEmpty()) {
            Reference subject = new Reference();
            subject.setReference("Patient/" + patientHashId);
            documentReference.setSubject(subject);
        }

        // Agregar extensiones con información adicional
        addExtensions(documentReference, fileData);

        return documentReference;
    }

    /**
     * Agrega extensiones al DocumentReference con información adicional del backend
     */
    private void addExtensions(DocumentReference documentReference, Map<String, Object> fileData) {
        // Tipo de archivo
        if (fileData.containsKey("type") && fileData.get("type") != null) {
            Extension typeExtension = new Extension();
            typeExtension.setUrl("http://example.org/fhir/StructureDefinition/file-type");
            typeExtension.setValue(new StringType(fileData.get("type").toString()));
            documentReference.addExtension(typeExtension);
        }

        // URL del archivo
        if (fileData.containsKey("url") && fileData.get("url") != null) {
            Extension urlExtension = new Extension();
            urlExtension.setUrl("http://example.org/fhir/StructureDefinition/file-url");
            urlExtension.setValue(new StringType(fileData.get("url").toString()));
            documentReference.addExtension(urlExtension);
        }

        // Nombre del archivo
        if (fileData.containsKey("name") && fileData.get("name") != null) {
            Extension nameExtension = new Extension();
            nameExtension.setUrl("http://example.org/fhir/StructureDefinition/file-name");
            nameExtension.setValue(new StringType(fileData.get("name").toString()));
            documentReference.addExtension(nameExtension);
        }

        // Título del archivo
        if (fileData.containsKey("titulo") && fileData.get("titulo") != null) {
            Extension titleExtension = new Extension();
            titleExtension.setUrl("http://example.org/fhir/StructureDefinition/file-title");
            titleExtension.setValue(new StringType(fileData.get("titulo").toString()));
            documentReference.addExtension(titleExtension);
            // También usar como description si no hay description
            if (documentReference.getDescription() == null || documentReference.getDescription().isEmpty()) {
                documentReference.setDescription(fileData.get("titulo").toString());
            }
        }

        // Descripción del archivo
        if (fileData.containsKey("descripcion") && fileData.get("descripcion") != null) {
            Extension descriptionExtension = new Extension();
            descriptionExtension.setUrl("http://example.org/fhir/StructureDefinition/file-description");
            descriptionExtension.setValue(new StringType(fileData.get("descripcion").toString()));
            documentReference.addExtension(descriptionExtension);
        }
    }

}
