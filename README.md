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
│   └── FhirServerConfig.java       # Configuración general del servidor FHIR
├── provider/
│   └── PatientResourceProvider.java # Ejemplo de ResourceProvider para el recurso Patient
├── FhirServerApplication.java       # Clase principal
└── resources/
    └── application.properties
```

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