package com.serverfhir.fhir_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.serverfhir")
public class FhirServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(FhirServerApplication.class, args);
	}

}
