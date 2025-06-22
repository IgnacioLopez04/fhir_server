package com.serverfhir.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.r5.model.Patient;
import org.hl7.fhir.r5.model.IdType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.Map;
import org.hl7.fhir.r5.model.ContactPoint;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.StringType;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r5.model.ResourceType;
import org.hl7.fhir.r5.model.IdType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Component
public class PatientResourceProvider implements IResourceProvider{

    @Override
    public Class<Patient> getResourceType(){
        return Patient.class;
    }

    @Read
    public Patient read(@IdParam IdType id) {
        String dni = id.getIdPart();
        RestTemplate restTemplate = new RestTemplate();

        String url = "http://localhost:3000/api/patient/{dni}";
        Map<String, String> params = new HashMap<>();
        params.put("dni", dni);

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class, params);
            Map data = response.getBody();

            Patient patient = new Patient();
            patient.setId(dni);
            patient.addIdentifier().setValue(dni);

            patient.addName()
                    .setFamily((String) data.get("apellido"))
                    .addGiven((String) data.get("nombre"));

            // Fecha de nacimiento
            if (data.get("fecha_nacimiento") != null) {
                patient.setBirthDate(java.sql.Date.valueOf(((String) data.get("fecha_nacimiento")).substring(0, 10)));
            }

            // Teléfono
            if (data.get("telefono") != null) {
                patient.addTelecom()
                        .setSystem(ContactPoint.ContactPointSystem.PHONE)
                        .setValue((String) data.get("telefono"));
            }

            // Falta agregar la direccion del paciente, pero no viene en el llamado al back

            // Extensiones personalizadas
            addExtension(patient, "id_barrio", data);
            addExtension(patient, "id_calle", data);
            addExtension(patient, "numero_calle", data);
            addExtension(patient, "id_codigo_postal", data);

            return patient;

        } catch (Exception e) {
            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException("Paciente no encontrado: " + dni);
        }
    }

    private void addExtension(Patient patient, String field, Map data) {
        if (data.get(field) != null) {
            patient.addExtension(
                    new Extension("http://mi-servidor/fhir/StructureDefinition/" + field,
                            new StringType(String.valueOf(data.get(field))))
            );
        }
    }

    @Create
    public MethodOutcome createPatient(@ResourceParam Patient patient) {
        RestTemplate restTemplate = new RestTemplate();

        String nombre = !patient.getNameFirstRep().getGiven().isEmpty()
                ? patient.getNameFirstRep().getGiven().get(0).getValue()
                : "";
        String segundoNombre = patient.getNameFirstRep().getGiven().size() > 1
                ? patient.getNameFirstRep().getGiven().get(1).getValue()
                : "";

        Map<String, Object> payload = new HashMap<>();
        payload.put("dni_paciente", patient.getIdElement().getIdPart());
        payload.put("nombre_paciente", nombre + " " + segundoNombre);
        payload.put("apellido_paciente", patient.getNameFirstRep().getFamily());
        payload.put("fecha_nacimiento", patient.getBirthDate());

        if (patient.hasTelecom()) {
            String telefono = patient.getTelecomFirstRep().getValue().replaceAll("-", "");
            payload.put("telefono", Integer.parseInt(telefono));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

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

}
