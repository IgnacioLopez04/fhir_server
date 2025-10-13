# Configuración CORS del Servidor FHIR

## Problema Identificado

El error de CORS que estás experimentando:
```
Access to XMLHttpRequest at 'http://localhost:8080/fhir/abm/provincias' 
from origin 'http://localhost:8081' has been blocked by CORS policy.
Response to preflight request doesn't pass access control check: 
No 'Access-Control-Allow-Origin' header is present on the requested resource.
```

## Solución Implementada

### 1. Configuración CORS en Spring Security
Se actualizó `SecurityConfig.java` para manejar CORS correctamente:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList("http://localhost:8081", "http://localhost:3000"));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/fhir/**", configuration);
    return source;
}
```

### 2. Configuración CORS en WebMvc
Se actualizó `CorsConfig.java` para ser más específico:

```java
registry.addMapping("/fhir/**")
    .allowedOrigins("http://localhost:8081", "http://localhost:3000")
    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
    .allowedHeaders("*")
    .exposedHeaders("Authorization", "Content-Type")
    .allowCredentials(true)
    .maxAge(3600);
```

### 3. Interceptor CORS Personalizado en FHIR
Se implementó un interceptor CORS personalizado en `FhirServerConfig.java`:

```java
this.registerInterceptor(new InterceptorAdapter() {
    @Override
    public boolean incomingRequestPreProcessed(HttpServletRequest request, HttpServletResponse response) {
        // Permitir múltiples orígenes
        String origin = request.getHeader("Origin");
        if (origin != null && (origin.equals("http://localhost:8081") || origin.equals("http://localhost:3000"))) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        }
        
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Max-Age", "3600");

        // Manejo de preflight (OPTIONS)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return false;
        }

        return true;
    }
});
```

## Orígenes Permitidos

- `http://localhost:8081` - Frontend Vue.js
- `http://localhost:3000` - Backend Node.js

## Headers Permitidos

- `Authorization` - Para autenticación JWT
- `Content-Type` - Para especificar el tipo de contenido
- Todos los demás headers (`*`)

## Métodos HTTP Permitidos

- `GET` - Consultas
- `POST` - Creación de recursos
- `PUT` - Actualización de recursos
- `DELETE` - Eliminación de recursos
- `OPTIONS` - Preflight CORS

## Configuración de Credenciales

- `allowCredentials: true` - Permite el envío de cookies y headers de autenticación
- `maxAge: 3600` - Cachea la respuesta preflight por 1 hora

## Verificación de la Configuración

### 1. Reiniciar el Servidor
Después de hacer los cambios, reinicia el servidor FHIR.

### 2. Probar con curl
```bash
# Test preflight OPTIONS
curl -X OPTIONS \
  -H "Origin: http://localhost:8081" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: Authorization" \
  -v http://localhost:8080/fhir/abm/provincias

# Test GET request
curl -H "Origin: http://localhost:8081" \
  -H "Authorization: Bearer {token}" \
  -v http://localhost:8080/fhir/abm/provincias
```

### 3. Verificar Headers de Respuesta
La respuesta debe incluir:
```
Access-Control-Allow-Origin: http://localhost:8081
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
```

## Solución de Problemas Comunes

### 1. Error de Preflight
Si sigues teniendo problemas con OPTIONS:
- Verifica que el interceptor CORS se ejecute antes de la autenticación
- Asegúrate de que OPTIONS esté permitido en Spring Security

### 2. Headers de Autorización
Si hay problemas con el header Authorization:
- Verifica que `Access-Control-Allow-Headers` incluya `Authorization`
- Asegúrate de que `allowCredentials` esté en `true`

### 3. Múltiples Orígenes
Si necesitas agregar más orígenes:
- Actualiza tanto `SecurityConfig` como `CorsConfig`
- Agrega el nuevo origen en el interceptor personalizado

## Notas Importantes

- **Orden de Configuración**: CORS debe configurarse antes que Spring Security
- **Interceptores FHIR**: El interceptor personalizado se ejecuta en cada petición FHIR
- **Cache Preflight**: El header `Access-Control-Max-Age` mejora el rendimiento
- **Credenciales**: Es necesario para enviar tokens JWT en headers personalizados
