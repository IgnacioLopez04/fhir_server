package com.serverfhir.controller;

import com.serverfhir.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/file")
@CrossOrigin(origins = "*")
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private JwtService jwtService;

    @Value("${tfback.url}")
    private String tfBackUrl;

    @Value("${tfback.api.path}")
    private String tfBackApiPath;

    private String buildBackendUrl(String path) {
        return tfBackUrl + tfBackApiPath + path;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentReference") String documentReferenceJson,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        logger.info("Iniciando carga de archivo");
        
        try {
            // Validar token de autorización
            if (authHeader == null || authHeader.isEmpty()) {
                logger.error("No se encontró el token de autorización");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authorization header is required"));
            }

            String token = authHeader;
            if (!jwtService.validateToken(token)) {
                logger.error("Token inválido o expirado");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
            }

            // Validar que el archivo no esté vacío
            if (file == null || file.isEmpty()) {
                logger.error("No se encontró el archivo en la petición");
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "No se encontró el archivo para subir"));
            }

            logger.info("Archivo recibido: " + file.getOriginalFilename() + 
                       ", tamaño: " + file.getSize() + " bytes, tipo: " + file.getContentType());

            // Validar tipo de archivo
            String contentType = file.getContentType();
            List<String> allowedTypes = List.of("image/jpeg", "image/png", "application/pdf", "video/mp4");
            if (contentType == null || !allowedTypes.contains(contentType)) {
                logger.error("Tipo de archivo no permitido: " + contentType);
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Tipo de archivo no permitido. Tipos permitidos: image/jpeg, image/png, application/pdf, video/mp4"));
            }

            // Validar que el DocumentReference esté presente
            if (documentReferenceJson == null || documentReferenceJson.isEmpty()) {
                logger.error("No se encontró el DocumentReference en la petición");
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "No se encontró el DocumentReference en la petición"));
            }

            logger.info("DocumentReference recibido: " + documentReferenceJson);

            // Parsear el DocumentReference JSON
            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> docRefMap = objectMapper.readValue(documentReferenceJson, Map.class);

            // Extraer hashId, userId, reportId, título y descripción
            String hashId = extractHashId(docRefMap);
            String userId = extractUserId(docRefMap);
            String reportId = extractReportId(docRefMap);
            String titulo = extractTitle(docRefMap);
            String descripcion = extractDescription(docRefMap);

            logger.info("Metadatos extraídos - hashId: " + hashId + ", userId: " + userId + 
                       ", reportId: " + (reportId != null ? reportId : "null") +
                       ", titulo: " + (titulo != null ? titulo : "null") +
                       ", descripcion: " + (descripcion != null ? descripcion : "null"));

            // Validar que hashId y userId estén presentes
            if (hashId == null || hashId.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "El hashId del paciente es requerido en el DocumentReference"));
            }
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "El userId es requerido en las extensiones del DocumentReference"));
            }

            // Preparar el multipart request para el backend
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // Agregar el archivo (el backend espera files.files, así que usamos "files" como nombre)
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("files", fileResource);

            // Agregar los metadatos
            body.add("hash_id", hashId);
            body.add("userId", userId);
            if (reportId != null && !reportId.isEmpty()) {
                body.add("reportId", reportId);
            }
            if (titulo != null && !titulo.isEmpty()) {
                body.add("titulo", titulo);
            }
            if (descripcion != null && !descripcion.isEmpty()) {
                body.add("descripcion", descripcion);
            }

            // Configurar headers
            HttpHeaders headers = new HttpHeaders();
            // Spring establecerá automáticamente el Content-Type con el boundary correcto
            headers.set("Authorization", token);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Llamar al backend
            RestTemplate restTemplate = new RestTemplate();
            String backendUrl = buildBackendUrl("/file/upload") + "?hash_id=" + hashId;

            logger.info("Enviando archivo al backend: " + backendUrl);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                backendUrl,
                requestEntity,
                Map.class
            );

            logger.info("Respuesta del backend - Status: " + response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = response.getBody();

                if (responseBody != null && responseBody.containsKey("uploadedFiles")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> uploadedFiles = 
                        (List<Map<String, Object>>) responseBody.get("uploadedFiles");

                    if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
                        Map<String, Object> firstFile = uploadedFiles.get(0);
                        
                        // Construir respuesta en formato compatible
                        Map<String, Object> result = new HashMap<>();
                        result.put("success", true);
                        result.put("fileId", firstFile.get("fileId"));
                        result.put("fileUrl", firstFile.get("fileUrl"));
                        result.put("fileName", firstFile.get("fileName"));
                        result.put("fileType", firstFile.get("fileType"));
                        result.put("message", "Archivo subido exitosamente");

                        logger.info("Archivo subido exitosamente con ID: " + firstFile.get("fileId"));
                        return ResponseEntity.ok(result);
                    }
                }

                // Si no hay uploadedFiles, crear una respuesta básica
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Archivo subido exitosamente");
                logger.info("Archivo subido exitosamente");
                return ResponseEntity.ok(result);

            } else {
                logger.error("Error en la respuesta del backend: " + response.getStatusCode());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al subir el archivo al backend: " + response.getStatusCode()));
            }

        } catch (Exception e) {
            logger.error("Error inesperado al subir el archivo: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al procesar la carga del archivo: " + e.getMessage()));
        }
    }

    /**
     * Extrae el hashId del paciente del DocumentReference
     */
    private String extractHashId(Map<String, Object> docRefMap) {
        // Intentar desde subject.reference
        if (docRefMap.containsKey("subject")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> subject = (Map<String, Object>) docRefMap.get("subject");
            if (subject != null && subject.containsKey("reference")) {
                String reference = subject.get("reference").toString();
                if (reference.startsWith("Patient/")) {
                    return reference.substring("Patient/".length());
                }
            }
        }

        // Intentar desde extensiones
        if (docRefMap.containsKey("extension")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> extensions = 
                (List<Map<String, Object>>) docRefMap.get("extension");
            if (extensions != null) {
                for (Map<String, Object> ext : extensions) {
                    if (ext.containsKey("url") && 
                        ext.get("url").toString().contains("patient-hash-id")) {
                        if (ext.containsKey("valueString")) {
                            return ext.get("valueString").toString();
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Extrae el userId de las extensiones del DocumentReference
     */
    private String extractUserId(Map<String, Object> docRefMap) {
        if (docRefMap.containsKey("extension")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> extensions = 
                (List<Map<String, Object>>) docRefMap.get("extension");
            if (extensions != null) {
                for (Map<String, Object> ext : extensions) {
                    if (ext.containsKey("url") && 
                        ext.get("url").toString().contains("user-id")) {
                        if (ext.containsKey("valueString")) {
                            return ext.get("valueString").toString();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extrae el reportId de las extensiones del DocumentReference (opcional)
     */
    private String extractReportId(Map<String, Object> docRefMap) {
        if (docRefMap.containsKey("extension") && docRefMap.get("extension") != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> extensions = 
                (List<Map<String, Object>>) docRefMap.get("extension");
            if (extensions != null) {
                for (Map<String, Object> ext : extensions) {
                    if (ext.containsKey("url") && 
                        ext.get("url").toString().contains("report-id")) {
                        if (ext.containsKey("valueString")) {
                            return ext.get("valueString").toString();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extrae el título de las extensiones del DocumentReference o del campo description
     */
    private String extractTitle(Map<String, Object> docRefMap) {
        // Intentar desde extensiones
        if (docRefMap.containsKey("extension")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> extensions = 
                (List<Map<String, Object>>) docRefMap.get("extension");
            if (extensions != null) {
                for (Map<String, Object> ext : extensions) {
                    if (ext.containsKey("url") && 
                        ext.get("url").toString().contains("file-title")) {
                        if (ext.containsKey("valueString")) {
                            return ext.get("valueString").toString();
                        }
                    }
                }
            }
        }
        // Si no está en extensiones, intentar desde description
        if (docRefMap.containsKey("description")) {
            return docRefMap.get("description").toString();
        }
        return null;
    }

    /**
     * Extrae la descripción de las extensiones del DocumentReference
     */
    private String extractDescription(Map<String, Object> docRefMap) {
        if (docRefMap.containsKey("extension")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> extensions = 
                (List<Map<String, Object>>) docRefMap.get("extension");
            if (extensions != null) {
                for (Map<String, Object> ext : extensions) {
                    if (ext.containsKey("url") && 
                        ext.get("url").toString().contains("file-description")) {
                        if (ext.containsKey("valueString")) {
                            return ext.get("valueString").toString();
                        }
                    }
                }
            }
        }
        return null;
    }
}

