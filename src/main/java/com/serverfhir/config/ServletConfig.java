package com.serverfhir.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServletConfig {

    @Bean
    public ServletRegistrationBean<FhirServerConfig> fhirServletRegistration(FhirServerConfig fhirServerConfig) {
        ServletRegistrationBean<FhirServerConfig> registration = new ServletRegistrationBean<>(fhirServerConfig, "/fhir/*");
        registration.setName("fhirServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
