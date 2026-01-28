# Servidor FHIR - Historia Clínica Digital

Este proyecto implementa un **servidor FHIR** utilizando **Spring Boot** y **HAPI FHIR**. Su objetivo es actuar como un **API Gateway estándar FHIR** para exponer los recursos clínicos del sistema de historias clínicas digitales, manteniendo interoperabilidad y seguridad.

---

## 🧠 Arquitectura General

```
[ Frontend (React/Vue) ]
        |
        | (JWT en Authorization Header)
        ↓
[ Servidor FHIR (Spring Boot + HAPI) ]
        |
        |-- Valida el JWT localmente
        |-- Traduce recursos FHIR a formato interno
        |-- Reenvía solicitudes al Backend
        ↓
[ Backend (Node.js) ]
        |
        ↓
[ Base de Datos (PostgreSQL) ]
```

---

## ✅ Responsabilidades del Servidor FHIR

- Punto único de entrada para el frontend.
- Validación de tokens JWT emitidos por el backend.
- Traducción entre recursos FHIR y estructuras internas.
- Reenvío seguro y controlado de solicitudes al backend real.
- Exposición de endpoints compatibles con el estándar FHIR (R4).

---

## 📁 Estructura del Proyecto

```
src/
├── config/
│   └── com.serverfhir.config.FhirServerConfig.java       # Configuración general del servidor FHIR
├── provider/
│   └── PatientResourceProvider.java # Ejemplo de ResourceProvider para el recurso Patient
├── FhirServerApplication.java       # Clase principal
└── resources/
    └── application.properties
```

---

## 🌱 Configuración de entornos (local y producción)

El servidor FHIR se configura mediante archivos de propiedades y variables de entorno.

- `src/main/resources/application.properties`: configuración por defecto pensada para **desarrollo local**.
  - Usa `http://localhost:3000` como backend Node por defecto.
  - Puedes sobreescribir valores con variables de entorno sin tocar el archivo.
- `src/main/resources/application-prod.properties`: configuración pensada para **producción** (por ejemplo, en Render).
  - Se activa con el perfil `prod`.
  - Lee siempre las URLs y secretos desde variables de entorno.

### Variables de entorno importantes

- **Conexión con Backend TF_Back**
  - `TFBACK_URL`: URL base del backend (sin `/api`).
    - Local (default en `application.properties`): `http://localhost:3000`
    - Producción (ejemplo): `https://tf-back.onrender.com`
  - `TFBACK_API_PATH`: path base de la API del backend.
    - Default: `/api`

- **JWT**
  - `JWT_SECRET`: secreto usado para validar tokens.
    - En local, `application.properties` trae un valor por defecto para desarrollo.
    - En producción, **debe** configurarse vía variable de entorno.
  - `JWT_EXPIRATION`: tiempo de expiración en milisegundos.
    - Default: `28800000` (8 horas).

- **CORS**
  - `CORS_ALLOWED_ORIGINS`:
    - Local (default): `http://localhost:8081,http://localhost:3000`
    - Producción (ejemplo): `https://TU_FRONTEND.vercel.app`

- **Servidor**
  - `PORT` (en Render): puerto asignado por la plataforma.
  - `SERVER_PORT` (local): si quieres cambiar el `8080` por defecto.

### Cómo correr **localmente**

1. Asegurate de que el backend Node (`TF_Back`) está corriendo en `http://localhost:3000`.
2. Ejecuta:

```bash
./mvnw spring-boot:run
```

Esto usará `application.properties` (perfil por defecto).

### Cómo correr en **producción** (ejemplo Render)

1. Sube el proyecto a Render como **Web Service** Java.
2. Configura las variables de entorno mínimas:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `TFBACK_URL=https://TU_BACKEND.onrender.com`
   - `TFBACK_API_PATH=/api`
   - `JWT_SECRET=una_clave_larga_segura`
   - `CORS_ALLOWED_ORIGINS=https://TU_FRONTEND.vercel.app`
3. Render inyecta `PORT`, que es usado por `application-prod.properties`.

---

## 🔄 Comportamiento de actualización vs. desactivación de pacientes

- **Actualizar datos de paciente**
  - El frontend llama a: `PUT /fhir/Patient/{hashId}` con un recurso `Patient` completo.
  - El servidor FHIR traduce esto a: `PUT /api/patient/{hash_id}` en el backend Node, enviando un JSON con los campos mapeados.

- **Desactivar (borrado lógico) de paciente**
  - El frontend también usa `PUT /fhir/Patient/{hashId}`, pero enviando `active = false` (y la extensión `inactivo = true`).
  - El servidor FHIR detecta `active=false` y, en vez de hacer un PUT, llama a:
    - `DELETE /api/patient/delete/{hash_id}` en `TF_Back`.
  - El backend marca `inactivo = true` para ese `dni_paciente`, y los listados omiten pacientes inactivos.

Esto permite que el mismo endpoint FHIR (`PUT /fhir/Patient/{id}`) se use tanto para:
- **Editar datos** (active=true) → PUT al backend.
- **Desactivar** (active=false) → DELETE lógico al backend.

---

## 🚀 Endpoints disponibles (ejemplo)

- `GET /Patient/{id}` → Devuelve un recurso FHIR Patient con ID dado.

---

## 🔐 Seguridad

Este servidor espera que cada request incluya un token JWT en el header `Authorization`. El token es validado localmente para permitir o denegar el acceso.

```http
Authorization: Bearer eyJhbGciOi...
```

---

## 🧩 Tecnología usada

- Java 17+
- Spring Boot
- HAPI FHIR 6.x
- Maven

---

## 📌 Próximos pasos

- Implementar interceptor de validación JWT.
- Crear FhirMapper para conversión FHIR ↔ modelo interno.
- Conectar dinámicamente con backend real para lectura/escritura.

---

Desarrollado como parte del proyecto final de Ingeniería en Informática.