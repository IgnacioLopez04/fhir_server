# FHIR ABM Endpoints

Este documento describe los endpoints FHIR disponibles para acceder a los recursos de ABM (Alta, Baja, Modificación) del sistema.

## Recursos de Ubicación (Location)

### Provincias

#### Buscar todas las provincias
```
GET /fhir/Location?_type=province
```

#### Obtener provincia específica
```
GET /fhir/Location/{id_provincia}
```

### Ciudades

#### Buscar todas las ciudades
```
GET /fhir/Location?_type=city
```

#### Obtener ciudad específica
```
GET /fhir/Location/{id_ciudad}
```

## Recursos de Organización (Organization)

### Mutuales

#### Buscar todas las mutuales
```
GET /fhir/Organization?_type=insurance
```

#### Obtener mutual específica
```
GET /fhir/Organization/{id_mutual}
```

### Prestaciones/Servicios

#### Buscar todas las prestaciones
```
GET /fhir/Organization?_type=program
```

#### Obtener prestación específica
```
GET /fhir/Organization/{id_prestacion}
```

## Estructura de los Recursos

### Location (Provincias y Ciudades)

```json
{
  "resourceType": "Location",
  "id": "string",
  "name": "string",
  "status": "active",
  "type": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/v3-RoleCode",
          "code": "PROV|CITY",
          "display": "Provincia|Ciudad"
        }
      ]
    }
  ],
  "partOf": {
    "reference": "Location/{id_provincia}"
  }
}
```

### Organization (Mutuales y Prestaciones)

```json
{
  "resourceType": "Organization",
  "id": "string",
  "name": "string",
  "active": true,
  "type": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/organization-type",
          "code": "INS|PROG",
          "display": "Insurance Company|Program"
        }
      ]
    }
  ]
}
```

## Autenticación

Todos los endpoints requieren autenticación mediante el header `Authorization` con el token JWT válido.

## Mapeo de Tipos

- **Provincias**: `Location` con tipo `PROV` (Provincia)
- **Ciudades**: `Location` con tipo `CITY` (Ciudad) y referencia a provincia
- **Mutuales**: `Organization` con tipo `INS` (Insurance Company)
- **Prestaciones**: `Organization` con tipo `PROG` (Program)

## Ejemplos de Uso

### Obtener todas las provincias de Argentina
```bash
curl -H "Authorization: Bearer {token}" \
     "http://localhost:8080/fhir/Location?_type=province"
```

### Obtener ciudades de una provincia específica
```bash
curl -H "Authorization: Bearer {token}" \
     "http://localhost:8080/fhir/Location?_type=city&partof=Location/{id_provincia}"
```

### Obtener todas las mutuales
```bash
curl -H "Authorization: Bearer {token}" \
     "http://localhost:8080/fhir/Organization?_type=insurance"
```

### Obtener una prestación específica
```bash
curl -H "Authorization: Bearer {token}" \
     "http://localhost:8080/fhir/Organization/{id_prestacion}"
```

## Notas de Implementación

- Los proveedores FHIR se comunican con la API REST del backend en `http://localhost:3000/api`
- Se utilizan códigos estándar de HL7 FHIR para los tipos de recursos
- Las ciudades mantienen referencia a su provincia mediante el campo `partOf`
- Todos los recursos están marcados como activos por defecto
- Se maneja la autenticación mediante tokens JWT en los headers
