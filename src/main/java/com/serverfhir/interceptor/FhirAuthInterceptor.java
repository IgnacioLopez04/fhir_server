package com.serverfhir.interceptor;

import com.serverfhir.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class FhirAuthInterceptor implements HandlerInterceptor {


    @Autowired
    private JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        
        // Solo validar en endpoints FHIR (excluyendo metadata)
        String requestURI = request.getRequestURI();    
        if (requestURI.startsWith("/fhir/") && !requestURI.equals("/fhir/metadata")) {
            
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader == null || authHeader.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Authorization header is required\"}");
                return false;
            }
            
            String token = authHeader;
            
            if (!jwtService.validateToken(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Invalid or expired token\"}");
                return false;
            }
        }
        
        return true;
    }
} 