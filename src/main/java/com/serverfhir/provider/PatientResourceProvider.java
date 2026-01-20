package com.serverfhir.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.r5.model.Patient;
import org.hl7.fhir.r5.model.IdType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.Identifier;
import org.hl7.fhir.r5.model.BooleanType;
import org.hl7.fhir.r5.model.ContactPoint;
import java.util.Date;
import java.text.SimpleDateFormat;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r5.model.ResourceType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class PatientResourceProvider implements IResourceProvider{

    private static final Logger logger = LoggerFactory.getLogger(PatientResourceProvider.class);

    @Override
    public Class<Patient> getResourceType(){
        return Patient.class;
    }

    @Read
    public Patient read(@IdParam IdType id, RequestDetails requestDetails) {
        // Validación de token ya se hace en el interceptor
        String hashId = id.getIdPart();
        RestTemplate restTemplate = new RestTemplate();
    
        // Obtener el token del contexto de la petición
        String token = requestDetails.getHeader("Authorization");
        
        String url = "http://localhost:3000/api/patient/{hash_id}";
        Map<String, String> params = new HashMap<>();
        params.put("hash_id", hashId);
    
        // Crear headers con el token de autorización
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", token);
        }
    
        try {
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class, params);
            Map data = response.getBody();
    
            // Validar que data no sea null
            if (data == null || data.isEmpty()) {
                logger.warn("Paciente no encontrado: " + hashId);
                throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException("Paciente no encontrado: " + hashId);
            }
    
            Patient patient = new Patient();
            patient.setId(hashId);
            patient.addIdentifier().setValue(hashId);
    
            // Agregar DNI como identificador separado si existe
            if (data.get("dni_paciente") != null) {
                patient.addIdentifier()
                    .setSystem("http://mi-servidor.com/fhir/dni")
                    .setValue(String.valueOf(data.get("dni_paciente")));
            }
    
            // Validar que nombre y apellido existan antes de usarlos
            String nombre = (String) data.get("nombre");
            String apellido = (String) data.get("apellido");
            
            if (nombre != null && apellido != null) {
                patient.addName()
                        .setFamily(apellido)
                        .addGiven(nombre);
            }
    
            // Fecha de nacimiento (campo estándar FHIR)
            if (data.get("fecha_nacimiento") != null) {
                try {
                    Object fechaObj = data.get("fecha_nacimiento");
                    if (fechaObj instanceof Date) {
                        patient.setBirthDate((Date) fechaObj);
                    } else if (fechaObj instanceof Long) {
                        // Timestamp de PostgreSQL
                        Date fecha = new Date((Long) fechaObj);
                        patient.setBirthDate(fecha);
                    } else if (fechaObj instanceof String) {
                        // Intentar parsear como string
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                            Date fecha = sdf.parse((String) fechaObj);
                            patient.setBirthDate(fecha);
                        } catch (Exception e) {
                            logger.warn("No se pudo parsear fecha_nacimiento como string: " + fechaObj);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error al procesar fecha_nacimiento: " + e.getMessage());
                }
            }
    
            // Teléfono (campo estándar FHIR)
            if (data.get("telefono") != null) {
                try {
                    String telefono = String.valueOf(data.get("telefono"));
                    patient.addTelecom()
                        .setSystem(ContactPoint.ContactPointSystem.PHONE)
                        .setValue(telefono);
                } catch (Exception e) {
                    logger.warn("Error al procesar telefono: " + e.getMessage());
                }
            }
    
            // Estado activo/inactivo (campo estándar FHIR)
            Boolean inactivo = false;
            if (data.get("inactivo") != null) {
                try {
                    if (data.get("inactivo") instanceof Boolean) {
                        inactivo = (Boolean) data.get("inactivo");
                    } else if (data.get("inactivo") instanceof String) {
                        inactivo = Boolean.parseBoolean((String) data.get("inactivo"));
                    }
                    patient.setActive(!inactivo);
                } catch (Exception e) {
                    logger.warn("Error al procesar inactivo: " + e.getMessage());
                }
            } else {
                patient.setActive(true); // Por defecto activo
            }
    
            // Hash ID como extensión personalizada
            if (data.get("hash_id") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/hash-id",
                        new StringType(String.valueOf(data.get("hash_id"))))
                );
            }
            
            // Hash ID EHR - verificar tanto hash_id_ehr como hash_id_EHR (mayúsculas)
            Object hashIdEhr = data.get("hash_id_ehr");
            if (hashIdEhr == null) {
                hashIdEhr = data.get("hash_id_EHR"); // Intentar con mayúsculas
            }
            if (hashIdEhr != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/hash-id-ehr",
                        new StringType(String.valueOf(hashIdEhr)))
                );
            }
    
            // Prestación como extensión personalizada
            if (data.get("prestacion") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/prestacion",
                        new StringType(String.valueOf(data.get("prestacion"))))
                );
            }
            
            // ID Prestación como extensión personalizada (para match con dropdown)
            if (data.get("id_prestacion") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/id_prestacion",
                        new StringType(String.valueOf(data.get("id_prestacion"))))
                );
            }
            
            // Extensiones para campos de domicilio
            if (data.get("calle") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/calle",
                        new StringType(String.valueOf(data.get("calle"))))
                );
            }
            
            if (data.get("barrio") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/barrio",
                        new StringType(String.valueOf(data.get("barrio"))))
                );
            }
            
            if (data.get("id_ciudad") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/id_ciudad",
                        new StringType(String.valueOf(data.get("id_ciudad"))))
                );
            }
            
            if (data.get("piso_departamento") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/piso_departamento",
                        new StringType(String.valueOf(data.get("piso_departamento"))))
                );
            }
            
            // Número de domicilio (buscar tanto numero como numero_calle)
            Object numero = data.get("numero");
            if (numero == null) {
                numero = data.get("numero_calle"); // Intentar con nombre de columna de BD
            }
            if (numero != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/numero",
                        new StringType(String.valueOf(numero)))
                );
            }
            
            // ID Provincia
            if (data.get("id_provincia") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/id_provincia",
                        new StringType(String.valueOf(data.get("id_provincia"))))
                );
            }
            
            // Con quien vive
            // Buscar tanto con_quien_vive como vive_con (nombre de columna en BD)
            Object conQuienVive = data.get("con_quien_vive");
            if (conQuienVive == null) {
                conQuienVive = data.get("vive_con"); // Intentar con nombre de columna de BD
            }
            if (conQuienVive != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/con_quien_vive",
                        new StringType(String.valueOf(conQuienVive)))
                );
            }
            
            // ID Mutual
            if (data.get("id_mutual") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/id_mutual",
                        new StringType(String.valueOf(data.get("id_mutual"))))
                );
            }
            
            // Número de afiliado
            if (data.get("numero_afiliado") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/numero_afiliado",
                        new StringType(String.valueOf(data.get("numero_afiliado"))))
                );
            }
            
            // Ocupación actual
            if (data.get("ocupacion_actual") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/ocupacion_actual",
                        new StringType(String.valueOf(data.get("ocupacion_actual"))))
                );
            }
            
            // Ocupación anterior
            if (data.get("ocupacion_anterior") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor.com/fhir/StructureDefinition/ocupacion_anterior",
                        new StringType(String.valueOf(data.get("ocupacion_anterior"))))
                );
            }
            
            // Inactivo como extensión BooleanType
            patient.addExtension(
                new Extension("http://mi-servidor.com/fhir/StructureDefinition/inactivo",
                    new BooleanType(inactivo))
            );
            
            // Tutores (si existen, serializar como JSON)
            if (data.get("tutores") != null) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    String tutoresJson = mapper.writeValueAsString(data.get("tutores"));
                    patient.addExtension(
                        new Extension("http://mi-servidor.com/fhir/StructureDefinition/tutores",
                            new StringType(tutoresJson))
                    );
                } catch (Exception e) {
                    logger.warn("Error al serializar tutores: " + e.getMessage());
                }
            }
    
            return patient;
    
        } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error al obtener el paciente: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException("Paciente no encontrado: " + hashId);
        }
    }

    @Search
    public List<Patient> searchPatients(RequestDetails requestDetails) {
        // Validación de token ya se hace en el interceptor
        RestTemplate restTemplate = new RestTemplate();

        // Obtener el token del contexto de la petición
        String token = requestDetails.getHeader("Authorization");

        String url = "http://localhost:3000/api/patient";
        
        // Crear headers con el token de autorización
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", token);
        }

        try {
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(
                url, 
                org.springframework.http.HttpMethod.GET, 
                entity, 
                List.class
            );
            
            @SuppressWarnings("unchecked")
            List<Map> patientsData = (List<Map>) response.getBody();
            List<Patient> patients = new ArrayList<>();

            if (patientsData != null) {
                for (Map data : patientsData) {
                    Patient patient = new Patient();
                    
                    // ID del paciente (hash_id)
                    String hashId = (String) data.get("hash_id");
                    if (hashId != null) {
                        patient.setId(hashId);
                        patient.addIdentifier().setValue(hashId);
                    }

                    // Agregar DNI como identificador separado si existe
                    if (data.get("dni_paciente") != null) {
                        patient.addIdentifier()
                            .setSystem("http://mi-servidor.com/fhir/dni")
                            .setValue((String) data.get("dni_paciente"));
                    }

                     // Nombre y apellido
                     if (data.get("nombre") != null && data.get("apellido") != null) {
                         patient.addName()
                                 .setFamily((String) data.get("apellido"))
                                 .addGiven((String) data.get("nombre"));
                     }

                     // Hash ID como extensión personalizada
                     if (data.get("hash_id") != null) {
                         patient.addExtension(
                             new Extension("http://mi-servidor.com/fhir/StructureDefinition/hash-id",
                                 new StringType(String.valueOf(data.get("hash_id"))))
                         );
                     }

                     // Prestación como extensión personalizada
                     if (data.get("prestacion") != null) {
                         patient.addExtension(
                             new Extension("http://mi-servidor.com/fhir/StructureDefinition/prestacion",
                                 new StringType(String.valueOf(data.get("prestacion"))))
                         );
                     }

                     if (data.get("ocupacion_actual") != null) {
                         patient.addExtension(
                             new Extension("http://mi-servidor.com/fhir/StructureDefinition/ocupacion_actual",
                                 new StringType(String.valueOf(data.get("ocupacion_actual"))))
                         );
                     }
                     if (data.get("ocupacion_anterior") != null) {
                         patient.addExtension(
                             new Extension("http://mi-servidor.com/fhir/StructureDefinition/ocupacion_anterior",
                                 new StringType(String.valueOf(data.get("ocupacion_anterior"))))
                         );
                     }
                     if (data.get("id_mutual") != null) {
                         patient.addExtension(
                             new Extension("http://mi-servidor.com/fhir/StructureDefinition/id_mutual",
                                 new StringType(String.valueOf(data.get("id_mutual"))))
                         );
                     }
                     if (data.get("numero_afiliado") != null) {
                         patient.addExtension(
                             new Extension("http://mi-servidor.com/fhir/StructureDefinition/numero_afiliado",
                                 new StringType(String.valueOf(data.get("numero_afiliado"))))
                         );
                     }

                    patients.add(patient);
                }
            }

            return patients;

        } catch (Exception e) {
            logger.error("Error al obtener la lista de pacientes: " + e.getMessage(), e);
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException(
                "No se pudieron obtener los pacientes: " + e.getMessage()
            );
        }
    }

    @Create
    public MethodOutcome createPatient(@ResourceParam Patient patient, RequestDetails requestDetails) {
        // Validación de token ya se hace en el interceptor
        RestTemplate restTemplate = new RestTemplate();

        // Obtener el token del contexto de la petición
        String token = requestDetails.getHeader("Authorization");

        // Procesar el nombre del paciente
        String nombre = "";
        String segundoNombre = "";
        if (patient.hasName() && !patient.getNameFirstRep().getGiven().isEmpty()) {
            nombre = patient.getNameFirstRep().getGiven().get(0).getValue();
            if (patient.getNameFirstRep().getGiven().size() > 1) {
                segundoNombre = patient.getNameFirstRep().getGiven().get(1).getValue();
            }
        }
        String dni = null;

        for (Identifier identifier : patient.getIdentifier()) {
            if ("http://mi-servidor.com/fhir/dni".equals(identifier.getSystem())) {
                dni = identifier.getValue();
                break;
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("dni_paciente", dni);
        payload.put("nombre_paciente", nombre + " " + segundoNombre);
        payload.put("apellido_paciente", patient.getNameFirstRep().getFamily());
        
        // Procesar fecha de nacimiento
        if (patient.hasBirthDate()) {
            String fechaNacimiento = patient.getBirthDate().toString();
            payload.put("fecha_nacimiento", fechaNacimiento);
        }

        // Procesar teléfono
        if (patient.hasTelecom()) {
            String telefono = patient.getTelecomFirstRep().getValue().replaceAll("-", "");
            try {
                payload.put("telefono", Long.parseLong(telefono));
            } catch (NumberFormatException e) {
                logger.warn("No se pudo convertir el teléfono a número: " + telefono);
                payload.put("telefono", telefono);
            }
        }

        // Procesar extensiones para los nuevos campos
        processExtensions(patient, payload);

        // Procesar tutores si existen
        if (patient.hasExtension()) {
            List<Map<String, Object>> tutores = new ArrayList<>();
            for (Extension extension : patient.getExtension()) {
                if (extension.getUrl().contains("tutores")) {
                    // La extensión de tutores contiene un JSON stringificado
                    try {
                        String tutoresJson = extension.getValue().toString();
                        logger.info("JSON de tutores recibido: " + tutoresJson);
                        
                        // Parsear el JSON de tutores
                        ObjectMapper mapper = new ObjectMapper();
                        List<Map<String, Object>> tutoresList = mapper.readValue(tutoresJson, List.class);
                        payload.put("tutores", tutoresList);
                        logger.info("Tutores procesados exitosamente: " + tutoresList.size());
                        logger.info("Datos de tutores: " + tutoresList.toString());
                    } catch (Exception e) {
                        logger.error("Error al procesar tutores: " + e.getMessage(), e);
                        payload.put("tutores", new ArrayList<>());
                    }
                    break;
                }
            }
        }
        
        // Log del payload final
        logger.info("Payload final enviado al backend: " + payload.toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Agregar el token de autorización a los headers
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", token);
        }else{
            throw new RuntimeException("No se pudo crear el paciente. No se encontró el accessToken.");
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity("http://localhost:3000/api/patient", request, Void.class);

            if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
                MethodOutcome outcome = new MethodOutcome();
                outcome.setId(new IdType(ResourceType.Patient.name(), patient.getIdElement().getIdPart()));
                return outcome;
            } else {
                throw new RuntimeException("Error en la API externa: código " + response.getStatusCode());
            }

        } catch (Exception e) {
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException("No se pudo crear el paciente: " + e.getMessage());
        }
    }

    @Update
    public MethodOutcome updatePatient(@IdParam IdType id, @ResourceParam Patient patient, RequestDetails requestDetails) {
        String hashId = id.getIdPart();
        String token = requestDetails.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("No se pudo actualizar el paciente. No se encontró el accessToken.");
        }

        String dni = null;
        for (Identifier identifier : patient.getIdentifier()) {
            if ("http://mi-servidor.com/fhir/dni".equals(identifier.getSystem())) {
                dni = identifier.getValue();
                break;
            }
        }

        String nombre = "";
        String segundoNombre = "";
        if (patient.hasName() && !patient.getNameFirstRep().getGiven().isEmpty()) {
            nombre = patient.getNameFirstRep().getGiven().get(0).getValue();
            if (patient.getNameFirstRep().getGiven().size() > 1) {
                segundoNombre = patient.getNameFirstRep().getGiven().get(1).getValue();
            }
        }
        String apellido = patient.hasName() ? patient.getNameFirstRep().getFamily() : "";
        if (apellido == null) apellido = "";

        Map<String, Object> payload = new HashMap<>();
        payload.put("dni_paciente", dni);
        payload.put("nombre_paciente", nombre + " " + segundoNombre);
        payload.put("apellido_paciente", apellido);

        if (patient.hasBirthDate()) {
            payload.put("fecha_nacimiento", patient.getBirthDate().toString());
        }
        if (patient.hasTelecom()) {
            String telefono = patient.getTelecomFirstRep().getValue().replaceAll("-", "");
            try {
                payload.put("telefono", Long.parseLong(telefono));
            } catch (NumberFormatException e) {
                payload.put("telefono", telefono);
            }
        }

        processExtensions(patient, payload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token);

        try {
            ResponseEntity<Void> response = new RestTemplate().exchange(
                "http://localhost:3000/api/patient/" + hashId,
                HttpMethod.PUT,
                new HttpEntity<>(payload, headers),
                Void.class
            );
            if (response.getStatusCode().is2xxSuccessful()) {
                MethodOutcome outcome = new MethodOutcome();
                outcome.setId(new IdType(ResourceType.Patient.name(), hashId));
                return outcome;
            }
            throw new RuntimeException("Error en la API externa: código " + response.getStatusCode());
        } catch (Exception e) {
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException("No se pudo actualizar el paciente: " + e.getMessage());
        }
    }

    private void processExtensions(Patient patient, Map<String, Object> payload) {
        if (patient.hasExtension()) {
            for (Extension extension : patient.getExtension()) {
                String url = extension.getUrl();
                if (url.contains("id_ciudad")) {
                    payload.put("id_ciudad", extension.getValue().toString());
                } else if (url.contains("barrio")) {
                    payload.put("barrio", extension.getValue().toString());
                } else if (url.contains("calle")) {
                    payload.put("calle", extension.getValue().toString());
                } else if (url.contains("numero")) {
                    payload.put("numero_calle", extension.getValue().toString());
                } else if (url.contains("id_prestacion")) {
                    payload.put("id_prestacion", extension.getValue().toString());
                } else if (url.contains("piso_departamento")) {
                    payload.put("piso_departamento", extension.getValue().toString());
                } else if (url.contains("con_quien_vive")) {
                    payload.put("vive_con", extension.getValue().toString());
                } else if (url.contains("id_mutual")) {
                    payload.put("id_mutual", extension.getValue().toString());
                } else if (url.contains("numero_afiliado")) {
                    payload.put("numero_afiliado", extension.getValue().toString());
                } else if (url.contains("ocupacion_actual")) {
                    payload.put("ocupacion_actual", extension.getValue().toString());
                } else if (url.contains("ocupacion_anterior")) {
                    payload.put("ocupacion_anterior", extension.getValue().toString());
                }
            }
        }
    }


}
