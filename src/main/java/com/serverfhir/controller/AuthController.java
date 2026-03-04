package com.serverfhir.controller;

import com.serverfhir.service.JwtService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private JwtService jwtService;

    @Value("${tfback.url}")
    private String tfBackUrl;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        logger.info("POST /auth/login recibido");
        try {
            String credential = loginRequest.get("credential");
            
            if (credential == null || credential.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Credential is required"));
            }

            // Llamar al servidor TF_Back para autenticación
            RestTemplate restTemplate = new RestTemplate();
            String fullTfBackUrl = tfBackUrl + "/auth/login";
            
            // Enviar el credential directamente como body
            Map<String, String> requestBody = Map.of("credential", credential);
            
            // Configurar headers para JSON
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(fullTfBackUrl, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();

                String authorizationHeader = response.getHeaders().getFirst("Authorization");
                String accessToken = null;

                if (authorizationHeader != null && !authorizationHeader.isEmpty()) {
                    accessToken = authorizationHeader;
                } else {
                    accessToken = (String) responseBody.get("access_token");
                }

                String refreshToken = (String) responseBody.get("refresh_token");

                if (accessToken != null) {
                    if (jwtService.validateToken(accessToken)) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("user", responseBody.get("user"));
                        result.put("access_token", accessToken);
                        result.put("message", "Login successful");

                        HttpHeaders responseHeaders = new HttpHeaders();

                        if (refreshToken != null && !refreshToken.isEmpty()) {
                            ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                                .httpOnly(true)
                                .secure(cookieSecure)
                                .path("/auth")
                                .maxAge(Duration.ofDays(7))
                                .sameSite("Strict")
                                .build();
                            responseHeaders.add(HttpHeaders.SET_COOKIE, cookie.toString());
                        }

                        return new ResponseEntity<>(result, responseHeaders, HttpStatus.OK);
                    } else {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Invalid token"));
                    }
                } else {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "No access token received"));
                }
                
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication failed"));
            }
            
        } catch (Exception e) {
            logger.error("Error en /auth/login", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Authorization header with Bearer token is required"));
            }
            
            String token = authHeader.substring(7);
            
            if (jwtService.validateToken(token)) {
                Claims claims = jwtService.getClaimsFromToken(token);
                
                Map<String, Object> result = new HashMap<>();
                result.put("valid", true);
                result.put("user", Map.of(
                    "id_usuario", claims.get("id_usuario"),
                    "email", claims.getSubject(),
                    "id_tipo_usuario", claims.get("id_tipo_usuario")
                ));
                
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
            }
            
        } catch (Exception e) {
            logger.error("Error en /auth/validate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(value = "refresh_token", required = false) String refreshToken) {
        logger.info("POST /auth/refresh recibido");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "No refresh token cookie present"));
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String fullTfBackUrl = tfBackUrl + "/auth/refresh";

            Map<String, String> requestBody = Map.of("refresh_token", refreshToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(fullTfBackUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();

                String accessToken = (String) responseBody.get("access_token");
                String newRefreshToken = (String) responseBody.get("refresh_token");

                if (accessToken == null) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "No access token received from backend"));
                }

                Map<String, Object> result = new HashMap<>();
                result.put("user", responseBody.get("user"));
                result.put("access_token", accessToken);

                HttpHeaders responseHeaders = new HttpHeaders();

                if (newRefreshToken != null && !newRefreshToken.isEmpty()) {
                    ResponseCookie cookie = ResponseCookie.from("refresh_token", newRefreshToken)
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .path("/auth")
                        .maxAge(Duration.ofDays(7))
                        .sameSite("Strict")
                        .build();
                    responseHeaders.add(HttpHeaders.SET_COOKIE, cookie.toString());
                }

                return new ResponseEntity<>(result, responseHeaders, HttpStatus.OK);
            } else {
                return ResponseEntity.status(response.getStatusCode())
                    .body(Map.of("error", "Refresh failed"));
            }
        } catch (Exception e) {
            logger.error("Error en /auth/refresh", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = "refresh_token", required = false) String refreshToken) {
        logger.info("POST /auth/logout recibido");
        try {
            if (refreshToken != null && !refreshToken.isEmpty()) {
                RestTemplate restTemplate = new RestTemplate();
                String fullTfBackUrl = tfBackUrl + "/auth/logout";

                Map<String, String> requestBody = Map.of("refresh_token", refreshToken);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

                restTemplate.postForEntity(fullTfBackUrl, request, Map.class);
            }

            HttpHeaders responseHeaders = new HttpHeaders();
            ResponseCookie clearCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
            responseHeaders.add(HttpHeaders.SET_COOKIE, clearCookie.toString());

            return new ResponseEntity<>(Map.of("message", "Logout successful"), responseHeaders, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error en /auth/logout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok(Map.of(
            "message", "Auth endpoint is working",
            "timestamp", System.currentTimeMillis()
        ));
    }
} 