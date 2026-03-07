package com.serverfhir.config;

import com.serverfhir.interceptor.FhirAuthInterceptor;
import com.serverfhir.interceptor.FhirAuditInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private FhirAuthInterceptor fhirAuthInterceptor;

    @Autowired
    private FhirAuditInterceptor fhirAuditInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(fhirAuditInterceptor)
                .addPathPatterns("/fhir/**")
                .excludePathPatterns("/fhir/metadata");

        registry.addInterceptor(fhirAuthInterceptor)
                .addPathPatterns("/fhir/**")
                .excludePathPatterns("/fhir/metadata");
    }
} 