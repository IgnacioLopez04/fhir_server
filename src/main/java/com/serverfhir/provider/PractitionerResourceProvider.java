package com.serverfhir.provider;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
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
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.Enumerations;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import com.serverfhir.util.BackendErrorHandler;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
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

    // Custom operation to get user types:
    // GET /fhir/Practitioner/$get-user-types
    @Operation(name = "$get-user-types", idempotent = true, type = Practitioner.class)
    public ValueSet getUserTypesOperation(RequestDetails requestDetails) {
        logger.info("Obteniendo lista de tipos de usuarios");
        
        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            } else {
                logger.error("No se encontró el token de autorización");
                throw new RuntimeException("No se pudo consultar los tipos de usuarios. No se encontró el accessToken.");
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = BASE_URL + "/user/type";
            
            logger.info("Consultando tipos de usuarios en: " + url);

            @SuppressWarnings("rawtypes")
            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    List.class);

            logger.info("Respuesta recibida del backend - Status: " + response.getStatusCode());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> userTypesData = (List<Map<String, Object>>) response.getBody();
                
                ValueSet valueSet = convertToValueSet(userTypesData);
                logger.info("Se encontraron " + userTypesData.size() + " tipos de usuarios");
                return valueSet;
            } else {
                logger.warn("No se encontraron tipos de usuarios o error en la respuesta: " + response.getStatusCode());
                return convertToValueSet(new ArrayList<>());
            }

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            BackendErrorHandler.handleHttpException(e);
            return null; // Nunca se ejecutará, pero necesario para compilación
        } catch (Exception e) {
            logger.error("Error al consultar los tipos de usuarios: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                    "No se pudieron consultar los tipos de usuarios: " + e.getMessage());
        }
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

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            BackendErrorHandler.handleHttpException(e);
            return null; // Nunca se ejecutará, pero necesario para compilación
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
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            BackendErrorHandler.handleHttpException(e);
            return null; // Nunca se ejecutará, pero necesario para compilación
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

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            BackendErrorHandler.handleHttpException(e);
            return null; // Nunca se ejecutará, pero necesario para compilación
        } catch (Exception e) {
            logger.error("Error al actualizar el usuario: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                "No se pudo actualizar el usuario: " + e.getMessage()
            );
        }
    }

    @Create
    public MethodOutcome createPractitioner(@ResourceParam Practitioner practitioner, RequestDetails requestDetails) {
        logger.info("Creando nuevo usuario (Practitioner)");

        try {
            // Extraer datos del recurso Practitioner FHIR
            String dni = null;
            String nombre = null;
            String apellido = null;
            String email = null;
            String fechaNacimiento = null;
            String idTipoUsuario = null;

            // Extraer DNI de identifiers
            if (practitioner.hasIdentifier()) {
                for (Identifier identifier : practitioner.getIdentifier()) {
                    if ("http://mi-servidor.com/fhir/dni".equals(identifier.getSystem())) {
                        dni = identifier.getValue();
                        break;
                    }
                }
            }

            // Extraer nombre y apellido
            if (practitioner.hasName() && practitioner.getName().size() > 0) {
                HumanName name = practitioner.getNameFirstRep();
                if (name.hasGiven()) {
                    nombre = name.getGivenAsSingleString();
                }
                if (name.hasFamily()) {
                    apellido = name.getFamily();
                }
            }

            // Extraer email de telecom
            if (practitioner.hasTelecom()) {
                for (ContactPoint telecom : practitioner.getTelecom()) {
                    if (telecom.getSystem() == ContactPoint.ContactPointSystem.EMAIL) {
                        email = telecom.getValue();
                        break;
                    }
                }
            }

            // Extraer fecha de nacimiento
            if (practitioner.hasBirthDateElement()) {
                fechaNacimiento = practitioner.getBirthDateElement().getValueAsString();
            }

            // Extraer id_tipo_usuario de extensiones
            Extension tipoUsuarioExt = practitioner.getExtensionByUrl(
                "http://mi-servidor/fhir/StructureDefinition/id-tipo-usuario"
            );
            if (tipoUsuarioExt != null && tipoUsuarioExt.hasValue()) {
                if (tipoUsuarioExt.getValue() instanceof StringType) {
                    idTipoUsuario = ((StringType) tipoUsuarioExt.getValue()).getValue();
                } else {
                    idTipoUsuario = tipoUsuarioExt.getValue().toString();
                }
            }

            // Validar campos requeridos
            if (dni == null || dni.isEmpty()) {
                throw new IllegalArgumentException("El DNI es obligatorio para crear un usuario");
            }
            if (fechaNacimiento == null || fechaNacimiento.isEmpty()) {
                throw new IllegalArgumentException("La fecha de nacimiento es obligatoria para crear un usuario");
            }

            // Construir payload para el backend
            Map<String, Object> userData = new HashMap<>();
            if (dni != null) {
                userData.put("dni_usuario", dni);
            }
            if (nombre != null) {
                userData.put("nombre_usuario", nombre);
            }
            if (apellido != null) {
                userData.put("apellido_usuario", apellido);
            }
            if (email != null) {
                userData.put("email", email);
            }
            if (fechaNacimiento != null) {
                userData.put("fecha_nacimiento", fechaNacimiento);
            }
            if (idTipoUsuario != null) {
                userData.put("id_tipo_usuario", idTipoUsuario);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("user", userData);

            // Preparar headers y hacer POST al backend
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            logger.info("Enviando datos al backend para crear usuario: " + payload);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                BASE_URL + "/user/create",
                HttpMethod.POST,
                entity,
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            // Manejar respuesta del backend
            String hashId = null;
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                
                // Si el usuario ya existía, el backend retorna el usuario existente
                if (responseBody != null && responseBody.containsKey("hash_id")) {
                    hashId = responseBody.get("hash_id").toString();
                } else if (responseBody != null && responseBody.containsKey("message")) {
                    // Si se creó exitosamente, necesitamos obtener el hash_id
                    // El backend genera el hash_id con createHashId(dni + fecha_nacimiento)
                    // Podemos obtener el usuario recién creado consultando por DNI
                    logger.info("Usuario creado exitosamente, obteniendo hash_id...");
                    
                    // Consultar el usuario por DNI para obtener el hash_id
                    HttpEntity<String> getEntity = new HttpEntity<>(headers);
                    ResponseEntity<List> getUserResponse = restTemplate.exchange(
                        BASE_URL + "/user/",
                        HttpMethod.GET,
                        getEntity,
                        List.class
                    );
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> usersData = (List<Map<String, Object>>) getUserResponse.getBody();
                    if (usersData != null) {
                        for (Map<String, Object> userDataFromList : usersData) {
                            String userDni = userDataFromList.get("dni_usuario") != null 
                                ? userDataFromList.get("dni_usuario").toString() 
                                : null;
                            if (dni.equals(userDni)) {
                                hashId = (String) userDataFromList.get("hash_id");
                                break;
                            }
                        }
                    }
                }
                
                if (hashId == null) {
                    logger.warn("No se pudo obtener el hash_id del usuario creado");
                    throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                        "Usuario creado pero no se pudo obtener el hash_id"
                    );
                }

                logger.info("Usuario creado exitosamente con hash_id: " + hashId);
                MethodOutcome outcome = new MethodOutcome();
                outcome.setId(new IdType("Practitioner", hashId));
                outcome.setCreated(true);
                return outcome;
            } else {
                // Este caso no debería ocurrir normalmente porque RestTemplate lanza excepciones
                // para códigos de error, pero el compilador requiere que todos los caminos retornen
                throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                    "Error inesperado: código de estado no exitoso sin excepción"
                );
            }

        } catch (IllegalArgumentException e) {
            logger.error("Error de validación al crear usuario: " + e.getMessage());
            throw new ca.uhn.fhir.rest.server.exceptions.InvalidRequestException(e.getMessage());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            BackendErrorHandler.handleHttpException(e);
            // handleHttpException siempre lanza una excepción, este código nunca se ejecuta
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                "Error al procesar respuesta del backend"
            );
        } catch (Exception e) {
            logger.error("Error al crear el usuario: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                "No se pudo crear el usuario: " + e.getMessage()
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

    /**
     * Convierte los datos de tipos de usuarios del backend al formato FHIR R5 ValueSet
     */
    private ValueSet convertToValueSet(List<Map<String, Object>> userTypesData) {
        ValueSet valueSet = new ValueSet();
        
        // Metadatos del ValueSet
        valueSet.setId("user-types");
        valueSet.setUrl("http://mi-servidor/fhir/ValueSet/user-types");
        valueSet.setVersion("1.0.0");
        valueSet.setName("UserTypes");
        valueSet.setTitle("Tipos de Usuarios");
        valueSet.setStatus(Enumerations.PublicationStatus.ACTIVE);
        valueSet.setExperimental(false);
        valueSet.setDateElement(new DateTimeType(new Date()));
        
        // Descripción
        valueSet.setDescription("Lista de tipos de usuarios disponibles en el sistema");
        
        // Composición del ValueSet
        ValueSet.ValueSetComposeComponent compose = valueSet.getCompose();
        ValueSet.ConceptSetComponent include = compose.addInclude();
        include.setSystem("http://mi-servidor/fhir/CodeSystem/user-types");
        
        // Agregar cada tipo de usuario como concepto
        for (Map<String, Object> userTypeData : userTypesData) {
            ValueSet.ConceptReferenceComponent concept = include.addConcept();
            
            // Obtener el ID del tipo de usuario
            Object idObj = userTypeData.get("id_tipo_usuario");
            if (idObj != null) {
                concept.setCode(idObj.toString());
            }
            
            // Obtener el nombre/descripción del tipo de usuario
            // Intentar diferentes campos posibles: nombre, descripcion, tipo, etc.
            String display = null;
            if (userTypeData.containsKey("nombre")) {
                display = userTypeData.get("nombre").toString();
            } else if (userTypeData.containsKey("descripcion")) {
                display = userTypeData.get("descripcion").toString();
            } else if (userTypeData.containsKey("tipo")) {
                display = userTypeData.get("tipo").toString();
            } else if (userTypeData.containsKey("name")) {
                display = userTypeData.get("name").toString();
            }
            
            if (display != null && !display.isEmpty()) {
                concept.setDisplay(display);
            } else if (idObj != null) {
                // Si no hay display, usar el código como display
                concept.setDisplay("Tipo de Usuario " + idObj.toString());
            }
        }
        
        return valueSet;
    }
}
