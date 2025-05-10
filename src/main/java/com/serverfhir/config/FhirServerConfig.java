package com.serverfhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.serverfhir.provider.PatientResourceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;


@Component
public class FhirServerConfig extends RestfulServer{
    @Autowired
    private PatientResourceProvider patientResourceProvider;

    @Override
    protected void initialize() {
        // Interceptor CORS manual para HAPI FHIR
        this.registerInterceptor(new InterceptorAdapter() {
            @Override
            public boolean incomingRequestPreProcessed(HttpServletRequest request, HttpServletResponse response) {
                response.setHeader("Access-Control-Allow-Origin", "http://localhost:5173");
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
                response.setHeader("Access-Control-Allow-Credentials", "true");

                // Manejo de preflight (OPTIONS)
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    return false; // corta el flujo de HAPI y responde directo
                }

                return true;
            }
        });
        setFhirContext(FhirContext.forR5());
        setDefaultPrettyPrint(true);
        setDefaultResponseEncoding(EncodingEnum.JSON);
        setResourceProviders(List.of(patientResourceProvider));
    }
}
