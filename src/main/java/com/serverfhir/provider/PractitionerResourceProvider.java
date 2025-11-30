package com.serverfhir.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r5.model.Practitioner;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.Identifier;
import org.hl7.fhir.r5.model.ContactPoint;
import org.hl7.fhir.r5.model.HumanName;
import org.hl7.fhir.r5.model.DateType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PractitionerResourceProvider implements IResourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(PractitionerResourceProvider.class);
    private static final String BASE_URL = "http://localhost:3000/api";

    @Override
    public Class<Practitioner> getResourceType() {
        return Practitioner.class;
    }

    @Search
    public List<Practitioner> searchPractitioners(RequestDetails requestDetails) {
        logger.info("Buscando todos los usuarios (Practitioners)");
        
        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(
                BASE_URL + "/user/",
                HttpMethod.GET,
                entity,
                List.class
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> usersData = (List<Map<String, Object>>) response.getBody();
            List<Practitioner> practitioners = new ArrayList<>();

            if (usersData != null) {
                for (Map<String, Object> userData : usersData) {
                    Practitioner practitioner = mapUserToPractitioner(userData);
                    practitioners.add(practitioner);
                }
                logger.info("Se encontraron " + practitioners.size() + " usuarios");
            }

            return practitioners;

        } catch (Exception e) {
            logger.error("Error al obtener la lista de usuarios: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                "No se pudieron obtener los usuarios: " + e.getMessage()
            );
        }
    }

    @Read
    public Practitioner readPractitioner(@IdParam IdType id, RequestDetails requestDetails) {
        String hashId = id.getIdPart();
        logger.info("Buscando usuario con hash_id: " + hashId);

        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(
                BASE_URL + "/user/",
                HttpMethod.GET,
                entity,
                List.class
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> usersData = (List<Map<String, Object>>) response.getBody();

            if (usersData != null) {
                for (Map<String, Object> userData : usersData) {
                    String userHashId = (String) userData.get("hash_id");
                    if (hashId.equals(userHashId)) {
                        Practitioner practitioner = mapUserToPractitioner(userData);
                        logger.info("Usuario encontrado: " + hashId);
                        return practitioner;
                    }
                }
            }

            logger.warn("Usuario no encontrado con hash_id: " + hashId);
            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException(
                "Usuario no encontrado: " + hashId
            );

        } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error al obtener el usuario: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                "No se pudo obtener el usuario: " + e.getMessage()
            );
        }
    }

    @Update
    public MethodOutcome updatePractitioner(@IdParam IdType id, @ResourceParam Practitioner practitioner, RequestDetails requestDetails) {
        String hashId = id.getIdPart();
        boolean active = practitioner.getActive();
        logger.info("Actualizando estado del usuario con hash_id: " + hashId + ", active: " + active);

        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response;

            if (active) {
                // Activar usuario
                logger.info("Activando usuario: " + hashId);
                response = restTemplate.exchange(
                    BASE_URL + "/user/activate/" + hashId,
                    HttpMethod.PUT,
                    entity,
                    String.class
                );
            } else {
                // Bloquear usuario
                logger.info("Bloqueando usuario: " + hashId);
                response = restTemplate.exchange(
                    BASE_URL + "/user/" + hashId,
                    HttpMethod.DELETE,
                    entity,
                    String.class
                );
            }

            logger.info("Usuario actualizado exitosamente: " + hashId);
            MethodOutcome outcome = new MethodOutcome();
            outcome.setId(new IdType("Practitioner", hashId));
            return outcome;

        } catch (Exception e) {
            logger.error("Error al actualizar el usuario: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                "No se pudo actualizar el usuario: " + e.getMessage()
            );
        }
    }

    /**
     * Mapea los datos del usuario del backend al recurso FHIR Practitioner
     */
    private Practitioner mapUserToPractitioner(Map<String, Object> userData) {
        Practitioner practitioner = new Practitioner();

        // hash_id como ID principal
        String hashId = (String) userData.get("hash_id");
        if (hashId != null) {
            practitioner.setId(hashId);
        }

        // DNI como identificador
        if (userData.get("dni_usuario") != null) {
            Identifier dniIdentifier = practitioner.addIdentifier();
            dniIdentifier.setSystem("http://mi-servidor.com/fhir/dni");
            dniIdentifier.setValue(userData.get("dni_usuario").toString());
        }

        // Nombre y apellido
        HumanName name = practitioner.addName();
        if (userData.get("nombre") != null) {
            name.addGiven(userData.get("nombre").toString());
        }
        if (userData.get("apellido") != null) {
            name.setFamily(userData.get("apellido").toString());
        }

        // Email como telecom
        if (userData.get("email") != null) {
            ContactPoint emailContact = practitioner.addTelecom();
            emailContact.setSystem(ContactPoint.ContactPointSystem.EMAIL);
            emailContact.setValue(userData.get("email").toString());
        }

        // Fecha de nacimiento
        if (userData.get("fecha_nacimiento") != null) {
            try {
                Object fechaObj = userData.get("fecha_nacimiento");
                if (fechaObj instanceof java.sql.Date) {
                    java.sql.Date sqlDate = (java.sql.Date) fechaObj;
                    Date utilDate = new Date(sqlDate.getTime());
                    practitioner.setBirthDateElement(new DateType(utilDate));
                } else if (fechaObj instanceof java.sql.Timestamp) {
                    java.sql.Timestamp timestamp = (java.sql.Timestamp) fechaObj;
                    Date utilDate = new Date(timestamp.getTime());
                    practitioner.setBirthDateElement(new DateType(utilDate));
                } else {
                    // Intentar parsear como string
                    String fechaNacimiento = fechaObj.toString();
                    practitioner.setBirthDateElement(new DateType(fechaNacimiento));
                }
            } catch (Exception e) {
                logger.warn("Error al procesar fecha de nacimiento: " + e.getMessage());
            }
        }

        // Estado activo (invertido: active = !inactivo)
        if (userData.get("inactivo") != null) {
            Object inactivoObj = userData.get("inactivo");
            boolean inactivo = false;
            if (inactivoObj instanceof Boolean) {
                inactivo = (Boolean) inactivoObj;
            } else if (inactivoObj instanceof Number) {
                inactivo = ((Number) inactivoObj).intValue() != 0;
            } else if (inactivoObj instanceof String) {
                inactivo = Boolean.parseBoolean((String) inactivoObj);
            }
            practitioner.setActive(!inactivo);
        } else {
            // Por defecto, si no se especifica, se considera activo
            practitioner.setActive(true);
        }

        // id_tipo_usuario como extensión personalizada
        if (userData.get("id_tipo_usuario") != null) {
            Extension tipoUsuarioExtension = new Extension();
            tipoUsuarioExtension.setUrl("http://mi-servidor/fhir/StructureDefinition/id-tipo-usuario");
            tipoUsuarioExtension.setValue(new StringType(userData.get("id_tipo_usuario").toString()));
            practitioner.addExtension(tipoUsuarioExtension);
        }

        return practitioner;
    }
}

