package com.serverfhir.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import org.hl7.fhir.r5.model.Organization;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.springframework.stereotype.Component;
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

@Component
public class OrganizationResourceProvider implements IResourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationResourceProvider.class);
    private static final String BASE_URL = "http://localhost:3000/api";

    @Override
    public Class<Organization> getResourceType() {
        return Organization.class;
    }

    // ========== ORGANIZATION RESOURCES (Mutuales y Prestaciones) ==========

    @Search
    public List<Organization> searchOrganizations(
        @RequiredParam(name = "_type") StringParam type,
        RequestDetails requestDetails) {

        String tipo = type.getValue();
        String endpoint = null;

        if ("insurance".equalsIgnoreCase(tipo)) {
            endpoint = "/abm/mutuales";
        } else if ("program".equalsIgnoreCase(tipo)) {
            endpoint = "/abm/prestaciones";
        } else {
            return new ArrayList<>(); // si no coincide, no devuelve nada
        }

        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(
                BASE_URL + endpoint,
                HttpMethod.GET,
                entity,
                List.class
            );

            List<Map<String, Object>> results = response.getBody();
            List<Organization> organizations = new ArrayList<>();

            for (Map<String, Object> row : results) {
                Organization org = new Organization();

                if ("insurance".equalsIgnoreCase(tipo)) {
                    org.setId(row.get("id_mutual").toString());
                    org.setName(row.get("nombre").toString());

                    CodeableConcept t = new CodeableConcept();
                    t.addCoding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/organization-type")
                        .setCode("INS")
                        .setDisplay("Insurance Company");
                    org.addType(t);

                } else if ("program".equalsIgnoreCase(tipo)) {
                    org.setId(row.get("id_prestacion").toString());
                    org.setName(row.get("nombre").toString());

                    CodeableConcept t = new CodeableConcept();
                    t.addCoding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/organization-type")
                        .setCode("PROG")
                        .setDisplay("Program");
                    org.addType(t);
                }

                org.setActive(true);
                organizations.add(org);
            }

            return organizations;

        } catch (Exception e) {
            logger.error("Error en searchOrganizations: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Read
    public Organization readMutual(@IdParam IdType id, RequestDetails requestDetails) {
        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(
                BASE_URL + "/abm/mutuales", 
                HttpMethod.GET, 
                entity, 
                List.class
            );

            List<Map<String, Object>> mutuales = response.getBody();
            
            for (Map<String, Object> mutual : mutuales) {
                if (mutual.get("id_mutual").toString().equals(id.getIdPart())) {
                    Organization organization = new Organization();
                    organization.setId(mutual.get("id_mutual").toString());
                    organization.setName(mutual.get("nombre").toString());
                    organization.setActive(true);
                    
                    CodeableConcept type = new CodeableConcept();
                    type.addCoding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/organization-type")
                        .setCode("INS")
                        .setDisplay("Insurance Company");
                    organization.addType(type);

                    return organization;
                }
            }

            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException("Mutual no encontrada: " + id.getIdPart());

        } catch (Exception e) {
            if (e instanceof ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException) {
                throw e;
            }
            logger.error("Error al obtener mutual: " + e.getMessage());
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException("Error interno del servidor");
        }
    }

    @Read
    public Organization readService(@IdParam IdType id, RequestDetails requestDetails) {
        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(
                BASE_URL + "/abm/prestaciones", 
                HttpMethod.GET, 
                entity, 
                List.class
            );

            List<Map<String, Object>> prestaciones = response.getBody();
            
            for (Map<String, Object> prestacion : prestaciones) {
                if (prestacion.get("id_prestacion").toString().equals(id.getIdPart())) {
                    Organization organization = new Organization();
                    organization.setId(prestacion.get("id_prestacion").toString());
                    organization.setName(prestacion.get("nombre").toString());
                    organization.setActive(true);
                    
                    CodeableConcept type = new CodeableConcept();
                    type.addCoding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/organization-type")
                        .setCode("PROG")
                        .setDisplay("Program");
                    organization.addType(type);

                    return organization;
                }
            }

            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException("Prestación no encontrada: " + id.getIdPart());

        } catch (Exception e) {
            if (e instanceof ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException) {
                throw e;
            }
            logger.error("Error al obtener prestación: " + e.getMessage());
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException("Error interno del servidor");
        }
    }
}
