package com.serverfhir.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import org.hl7.fhir.r5.model.Location;
import org.hl7.fhir.r5.model.Organization;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AbmResourceProvider implements IResourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(AbmResourceProvider.class);
    private static final String BASE_URL = "http://localhost:3000/api";

    @Override
    public Class<Location> getResourceType() {
        return Location.class;
    }

    // ========== LOCATION RESOURCES (Provincias y Ciudades) ==========

    @Search
    public List<Location> searchProvinces(@RequiredParam(name = "_type") StringParam type,RequestDetails requestDetails) {
        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(
                BASE_URL + "/abm/provincias", 
                HttpMethod.GET, 
                entity, 
                List.class
            );

            List<Map<String, Object>> provincias = response.getBody();
            List<Location> locations = new ArrayList<>();

            for (Map<String, Object> provincia : provincias) {
                Location location = new Location();
                location.setId(provincia.get("id_provincia").toString());
                location.setName(provincia.get("nombre").toString());
                location.setStatus(Location.LocationStatus.ACTIVE);
                
                // Tipo de ubicación: provincia
                CodeableConcept locationType = new CodeableConcept();
                locationType.addCoding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-RoleCode")
                    .setCode("PROV")
                    .setDisplay("Provincia");
                location.addType(locationType);

                locations.add(location);
            }

            return locations;

        } catch (Exception e) {
            logger.error("Error al obtener provincias: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Search
    public List<Location> searchCities(RequestDetails requestDetails) {
        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(
                BASE_URL + "/abm/ciudades", 
                HttpMethod.GET, 
                entity, 
                List.class
            );

            List<Map<String, Object>> ciudades = response.getBody();
            List<Location> locations = new ArrayList<>();

            for (Map<String, Object> ciudad : ciudades) {
                Location location = new Location();
                location.setId(ciudad.get("id_ciudad").toString());
                location.setName(ciudad.get("nombre").toString());
                location.setStatus(Location.LocationStatus.ACTIVE);
                
                // Tipo de ubicación: ciudad
                CodeableConcept type = new CodeableConcept();
                type.addCoding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-RoleCode")
                    .setCode("CITY")
                    .setDisplay("Ciudad");
                location.addType(type);

                // Si tiene provincia, establecer la referencia
                if (ciudad.get("id_provincia") != null) {
                    Reference partOf = new Reference();
                    partOf.setReference("Location/" + ciudad.get("id_provincia"));
                    location.setPartOf(partOf);
                }

                locations.add(location);
            }

            return locations;

        } catch (Exception e) {
            logger.error("Error al obtener ciudades: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Search
    public List<Location> searchCitiesByProvince(@RequiredParam(name = "provincia") StringParam provinciaId,RequestDetails requestDetails) {
        try {
            String token = requestDetails.getHeader("Authorization");
            
            if (provinciaId == null || provinciaId.isEmpty()) {
                logger.warn("searchCitiesByProvince: provincia parameter is missing");
                return new ArrayList<>();
            }
            String provinciaIdValue = provinciaId.getValue();
            
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(
                BASE_URL + "/abm/ciudades/" + provinciaIdValue, 
                HttpMethod.GET, 
                entity, 
                List.class
            );

            List<Map<String, Object>> todasLasCiudades = response.getBody();
            List<Location> ciudadesDeProvincia = new ArrayList<>();

            for (Map<String, Object> ciudad : todasLasCiudades) {
                
                Location location = new Location();
                location.setId(ciudad.get("id_ciudad").toString());
                location.setName(ciudad.get("nombre").toString());
                location.setStatus(Location.LocationStatus.ACTIVE);
                
                // Tipo de ubicación: ciudad
                CodeableConcept type = new CodeableConcept();
                type.addCoding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-RoleCode")
                    .setCode("CITY")
                    .setDisplay("Ciudad");
                location.addType(type);

                // Establecer referencia a la provincia
                Reference partOf = new Reference();
                partOf.setReference("Location/" + ciudad.get("id_provincia"));
                location.setPartOf(partOf);

                ciudadesDeProvincia.add(location);
            }

            logger.info("searchCitiesByProvince: Found {} cities for province {}", ciudadesDeProvincia.size(), provinciaId);
            return ciudadesDeProvincia;

        } catch (Exception e) {
            logger.error("Error al obtener ciudades por provincia: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Read
    public Location readProvince(@IdParam IdType id, RequestDetails requestDetails) {
        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(
                BASE_URL + "/abm/provincias", 
                HttpMethod.GET, 
                entity, 
                List.class
            );

            List<Map<String, Object>> provincias = response.getBody();
            
            for (Map<String, Object> provincia : provincias) {
                if (provincia.get("id_provincia").toString().equals(id.getIdPart())) {
                    Location location = new Location();
                    location.setId(provincia.get("id_provincia").toString());
                    location.setName(provincia.get("nombre").toString());
                    location.setStatus(Location.LocationStatus.ACTIVE);
                    
                    CodeableConcept type = new CodeableConcept();
                    type.addCoding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-RoleCode")
                        .setCode("PROV")
                        .setDisplay("Provincia");
                    location.addType(type);

                    return location;
                }
            }

            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException("Provincia no encontrada: " + id.getIdPart());

        } catch (Exception e) {
            if (e instanceof ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException) {
                throw e;
            }
            logger.error("Error al obtener provincia: " + e.getMessage());
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException("Error interno del servidor");
        }
    }

    @Read
    public Location readCity(@IdParam IdType id, RequestDetails requestDetails) {
        try {
            String token = requestDetails.getHeader("Authorization");
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", token);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(
                BASE_URL + "/abm/ciudades", 
                HttpMethod.GET, 
                entity, 
                List.class
            );

            List<Map<String, Object>> ciudades = response.getBody();
            
            for (Map<String, Object> ciudad : ciudades) {
                if (ciudad.get("id_ciudad").toString().equals(id.getIdPart())) {
                    Location location = new Location();
                    location.setId(ciudad.get("id_ciudad").toString());
                    location.setName(ciudad.get("nombre").toString());
                    location.setStatus(Location.LocationStatus.ACTIVE);
                    
                    CodeableConcept type = new CodeableConcept();
                    type.addCoding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-RoleCode")
                        .setCode("CITY")
                        .setDisplay("Ciudad");
                    location.addType(type);

                    if (ciudad.get("id_provincia") != null) {
                        Reference partOf = new Reference();
                        partOf.setReference("Location/" + ciudad.get("id_provincia"));
                        location.setPartOf(partOf);
                    }

                    return location;
                }
            }

            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException("Ciudad no encontrada: " + id.getIdPart());

        } catch (Exception e) {
            if (e instanceof ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException) {
                throw e;
            }
            logger.error("Error al obtener ciudad: " + e.getMessage());
            throw new ca.uhn.fhir.rest.server.exceptions.InternalErrorException("Error interno del servidor");
        }
    }
}
