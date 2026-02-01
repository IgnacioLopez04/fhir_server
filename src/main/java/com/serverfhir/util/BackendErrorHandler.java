package com.serverfhir.util;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

/**
 * Componente reutilizable para manejar y propagar errores HTTP del backend
 * hacia excepciones FHIR apropiadas.
 */
public class BackendErrorHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(BackendErrorHandler.class);

    /**
     * Maneja excepciones HTTP del backend y las convierte en excepciones FHIR apropiadas.
     * 
     * @param e La excepción HTTP del RestTemplate
     * @throws BaseServerResponseException La excepción FHIR apropiada con el mensaje del backend
     */
    public static void handleHttpException(HttpStatusCodeException e) throws BaseServerResponseException {
        String errorMessage = extractErrorMessage(e);
        HttpStatusCode statusCode = e.getStatusCode();
        
        logger.error("Error del backend - Código: {}, Mensaje: {}", statusCode, errorMessage);
        
        BaseServerResponseException fhirException = mapToFhirException(statusCode, errorMessage);
        throw fhirException;
    }

    /**
     * Extrae el mensaje de error del body de la respuesta HTTP.
     * Intenta parsear el body como JSON y buscar el campo "message".
     * 
     * @param e La excepción HTTP
     * @return El mensaje extraído o un mensaje por defecto
     */
    private static String extractErrorMessage(HttpStatusCodeException e) {
        String responseBody = e.getResponseBodyAsString();
        
        if (responseBody == null || responseBody.isEmpty()) {
            return e.getMessage();
        }

        try {
            // Intentar parsear como JSON
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            
            // Buscar el campo "message" en el JSON
            if (jsonNode.has("message") && jsonNode.get("message").isTextual()) {
                return jsonNode.get("message").asText();
            }
            
            // Si no hay "message", intentar buscar "error"
            if (jsonNode.has("error") && jsonNode.get("error").isTextual()) {
                return jsonNode.get("error").asText();
            }
            
            // Si el JSON es un objeto simple, intentar obtener su valor como string
            if (jsonNode.isTextual()) {
                return jsonNode.asText();
            }
            
        } catch (IOException ex) {
            logger.debug("No se pudo parsear el body como JSON: {}", responseBody);
        }

        // Si no se pudo extraer un mensaje del JSON, usar el mensaje de la excepción
        return e.getMessage();
    }

    /**
     * Mapea un código de estado HTTP a una excepción FHIR apropiada.
     * 
     * @param statusCode El código de estado HTTP
     * @param message El mensaje de error
     * @return La excepción FHIR apropiada
     */
    private static BaseServerResponseException mapToFhirException(HttpStatusCode statusCode, String message) {
        // Convertir HttpStatusCode a HttpStatus si es posible
        HttpStatus status = null;
        if (statusCode instanceof HttpStatus) {
            status = (HttpStatus) statusCode;
        } else {
            // Intentar obtener HttpStatus por valor numérico
            try {
                status = HttpStatus.valueOf(statusCode.value());
            } catch (IllegalArgumentException ex) {
                // Código de estado no estándar, usar el valor numérico directamente
                logger.debug("Código de estado HTTP no estándar: {}", statusCode.value());
            }
        }
        
        if (status != null && status.is4xxClientError()) {
            switch (status) {
                case BAD_REQUEST: // 400
                    return new InvalidRequestException(message != null ? message : "Solicitud inválida");
                    
                case UNAUTHORIZED: // 401
                    return new AuthenticationException(message != null ? message : "No autorizado");
                    
                case FORBIDDEN: // 403
                    return new AuthenticationException(message != null ? message : "Acceso prohibido");
                    
                case NOT_FOUND: // 404
                    return new ResourceNotFoundException(message != null ? message : "Recurso no encontrado");
                    
                case CONFLICT: // 409
                    return new InvalidRequestException(message != null ? message : "Conflicto en la solicitud");
                    
                default:
                    // Otros errores 4xx
                    return new InvalidRequestException(
                        message != null ? message : "Error del cliente: " + statusCode
                    );
            }
        } else if (status != null && status.is5xxServerError()) {
            // Errores 5xx
            return new InternalErrorException(
                message != null ? message : "Error interno del servidor: " + statusCode
            );
        } else {
            // Determinar por valor numérico si no se pudo convertir a HttpStatus
            int statusValue = statusCode.value();
            if (statusValue >= 400 && statusValue < 500) {
                return new InvalidRequestException(
                    message != null ? message : "Error del cliente: " + statusCode
                );
            } else if (statusValue >= 500) {
                return new InternalErrorException(
                    message != null ? message : "Error interno del servidor: " + statusCode
                );
            } else {
                // Códigos no estándar
                return new InternalErrorException(
                    message != null ? message : "Error desconocido: " + statusCode
                );
            }
        }
    }
}
