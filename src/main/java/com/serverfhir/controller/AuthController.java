package com.serverfhir.controller;

import com.serverfhir.service.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private JwtService jwtService;

    @Value("${tfback.url}")
    private String tfBackUrl;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        
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
                
                // Extraer el token del header Authorization
                String authorizationHeader = response.getHeaders().getFirst("Authorization");
                String accessToken = null;
                
                if (authorizationHeader != null && !authorizationHeader.isEmpty()) {
                    accessToken = authorizationHeader;
                } else {
                    // Fallback: buscar el token en el body de la respuesta
                    accessToken = (String) responseBody.get("access_token");
                }
                
                if (accessToken != null) {
                    // Validar el token con la misma clave secreta
                    if (jwtService.validateToken(accessToken)) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("user", responseBody.get("user"));
                        result.put("access_token", accessToken);
                        result.put("message", "Login successful");
                        
                        return ResponseEntity.ok(result);
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // En JWT stateless, el logout se maneja en el cliente
        // Aquí podríamos implementar una blacklist de tokens si es necesario
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok(Map.of(
            "message", "Auth endpoint is working",
            "timestamp", System.currentTimeMillis()
        ));
    }
} 