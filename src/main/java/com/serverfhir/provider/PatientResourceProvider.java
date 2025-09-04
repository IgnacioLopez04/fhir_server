package com.serverfhir.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
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
import org.hl7.fhir.r5.model.ContactPoint;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.Identifier;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r5.model.ResourceType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
        String dni = id.getIdPart();
        RestTemplate restTemplate = new RestTemplate();

        // Obtener el token del contexto de la petición
        String token = requestDetails.getHeader("Authorization");
        
        String url = "http://localhost:3000/api/patient/{dni}";
        Map<String, String> params = new HashMap<>();
        params.put("dni", dni);

        // Crear headers con el token de autorización
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", token);
        }

        try {
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class, params);
            Map data = response.getBody();

            Patient patient = new Patient();
            patient.setId(dni);
            patient.addIdentifier().setValue(dni);

            patient.addName()
                    .setFamily((String) data.get("apellido"))
                    .addGiven((String) data.get("nombre"));

            // Prestación como extensión personalizada
            if (data.get("prestacion") != null) {
                patient.addExtension(
                    new Extension("http://mi-servidor/fhir/StructureDefinition/prestacion",
                        new StringType(String.valueOf(data.get("prestacion"))))
                );
            }

            return patient;

        } catch (Exception e) {
            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException("Paciente no encontrado: " + dni);
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
                    
                                         // ID del paciente (DNI)
                     String dni = (String) data.get("dni_paciente");
                     if (dni != null) {
                         patient.setId(dni);
                         patient.addIdentifier().setValue(dni);
                     }

                     // Nombre y apellido
                     if (data.get("nombre") != null && data.get("apellido") != null) {
                         patient.addName()
                                 .setFamily((String) data.get("apellido"))
                                 .addGiven((String) data.get("nombre"));
                     }

                     // Prestación como extensión personalizada
                     if (data.get("prestacion") != null) {
                         patient.addExtension(
                             new Extension("http://mi-servidor/fhir/StructureDefinition/prestacion",
                                 new StringType(String.valueOf(data.get("prestacion"))))
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
                } else if (url.contains("id_prestacion")) {
                    payload.put("id_prestacion", extension.getValue().toString());
                } else if (url.contains("piso_departamento")) {
                    payload.put("piso_departamento", extension.getValue().toString());
                }
            }
        }
    }


}
