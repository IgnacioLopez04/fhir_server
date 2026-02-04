package com.serverfhir.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * On startup, pings the TF_Back /health endpoint in the background to wake it up
 * when this FHIR server is deployed on Render (cold start).
 */
@Component
public class WakeUpBackendRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(WakeUpBackendRunner.class);

    @Value("${tfback.url}")
    private String tfBackUrl;

    @Override
    public void run(ApplicationArguments args) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "wake-up-backend");
            t.setDaemon(true);
            return t;
        });
        executor.execute(() -> {
            try {
                String healthUrl = tfBackUrl.endsWith("/") ? tfBackUrl + "health" : tfBackUrl + "/health";
                SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(5000);
                factory.setReadTimeout(5000);
                RestTemplate rest = new RestTemplate(factory);
                rest.getForEntity(healthUrl, String.class);
                logger.debug("Wake-up request to backend succeeded: {}", healthUrl);
            } catch (Exception e) {
                logger.debug("Wake-up request to backend failed (backend may be starting): {}", e.getMessage());
            }
        });
        executor.shutdown();
    }
}
