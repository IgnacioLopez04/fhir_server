package com.serverfhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.serverfhir.provider.PatientResourceProvider;
import com.serverfhir.provider.AbmResourceProvider;
import com.serverfhir.provider.OrganizationResourceProvider;
import com.serverfhir.provider.ReportResourceProvider;
import com.serverfhir.provider.EhrResourceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class FhirServerConfig extends RestfulServer{
    @Autowired
    private PatientResourceProvider patientResourceProvider;
    
    @Autowired
    private AbmResourceProvider abmResourceProvider;
    
    @Autowired
    private OrganizationResourceProvider organizationResourceProvider;

    @Autowired
    private ReportResourceProvider reportResourceProvider;

    @Autowired
    private EhrResourceProvider ehrResourceProvider;

    @Override
    protected void initialize() {
        // Configuración básica de FHIR
        setFhirContext(FhirContext.forR5());
        setDefaultPrettyPrint(true);
        setDefaultResponseEncoding(EncodingEnum.JSON);
        
        // Registrar proveedores de recursos
        setResourceProviders(List.of(patientResourceProvider, abmResourceProvider, organizationResourceProvider,
                reportResourceProvider, ehrResourceProvider));
    }
}
