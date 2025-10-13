# FHIR Report Provider - Guía de Uso

## Descripción

El `ReportResourceProvider` es un provider FHIR que permite crear reportes médicos usando el recurso `DiagnosticReport` de FHIR R5. Este provider se integra con el backend Node.js para persistir los datos en la base de datos.

## Endpoints Disponibles

### Crear Reporte
- **Método**: POST
- **URL**: `/fhir/DiagnosticReport`
- **Content-Type**: `application/fhir+json`

### Leer Reporte
- **Método**: GET
- **URL**: `/fhir/DiagnosticReport/{id}`

### Buscar Reportes
- **Método**: GET
- **URL**: `/fhir/DiagnosticReport`

## Estructura del Recurso DiagnosticReport

```json
{
  "resourceType": "DiagnosticReport",
  "status": "final",
  "code": {
    "text": "Título del reporte"
  },
  "conclusion": "Contenido del reporte",
  "subject": {
    "reference": "Patient/{dni_paciente}"
  },
  "extension": [
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/patient-dni",
      "valueString": "12345678"
    },
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/user-id",
      "valueString": "1"
    },
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/speciality-id",
      "valueString": "2"
    },
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/ehr-id",
      "valueString": "123"
    },
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/report-type",
      "valueString": "1"
    }
  ]
}
```

## Extensiones Personalizadas

El provider utiliza las siguientes extensiones personalizadas:

| URL | Propósito | Tipo | Requerido |
|-----|-----------|------|-----------|
| `http://mi-servidor/fhir/StructureDefinition/patient-dni` | DNI del paciente | String | Sí |
| `http://mi-servidor/fhir/StructureDefinition/user-id` | ID del usuario | String | Sí |
| `http://mi-servidor/fhir/StructureDefinition/speciality-id` | ID de especialidad | String | No |
| `http://mi-servidor/fhir/StructureDefinition/ehr-id` | ID de historia clínica | String | No |
| `http://mi-servidor/fhir/StructureDefinition/report-type` | Tipo de reporte | String | No (default: "1") |

## Mapeo de Datos

El provider mapea los datos FHIR a los campos del backend de la siguiente manera:

| Campo FHIR | Campo Backend | Notas |
|------------|---------------|-------|
| `code.text` | `tittle` | Título del reporte |
| `conclusion` | `text` | Contenido del reporte |
| `extension[patient-dni]` | `patientDni` | DNI del paciente |
| `extension[user-id]` | `userId` | ID del usuario |
| `extension[speciality-id]` | `specialityId` | ID de especialidad (puede ser null) |
| `extension[ehr-id]` | `ehrId` | ID de historia clínica (puede ser null) |
| `extension[report-type]` | `reportType` | Tipo de reporte (default: "1") |

## Manejo de Valores Nulos

El provider maneja correctamente los campos que pueden ser null o vacíos:

- **specialityId**: Si no se proporciona, se envía como `null`
- **ehrId**: Si no se proporciona, se envía como `null`
- **reportType**: Si no se proporciona, se usa "1" como valor por defecto
- **titulo**: Si no se proporciona, se usa "Reporte de Diagnóstico" como valor por defecto
- **contenido**: Si no se proporciona, se envía como string vacío

## Ejemplo de Uso desde el Frontend

```javascript
import { createReportViaFhir } from '@/utils/reportFhirHelper';

// Crear un reporte
const reportData = {
  titulo: "Consulta de Cardiología",
  contenido: "Paciente presenta arritmia leve...",
  dniPaciente: "12345678",
  idUsuario: "1",
  idEspecialidad: "2", // Opcional
  idHistoriaClinica: "123", // Opcional
  tipoReporte: "1" // Opcional, default: "1"
};

const result = await createReportViaFhir(reportData);

if (result.success) {
  console.log('Reporte creado:', result.reportId);
} else {
  console.error('Error:', result.error);
}
```

## Respuesta del Servidor

### Éxito (201 Created)
```json
{
  "id": "123",
  "resourceType": "DiagnosticReport"
}
```

### Error (400/500)
```json
{
  "resourceType": "OperationOutcome",
  "issue": [
    {
      "severity": "error",
      "code": "processing",
      "diagnostics": "Mensaje de error específico"
    }
  ]
}
```

## Configuración

El provider está registrado automáticamente en `FhirServerConfig.java` y no requiere configuración adicional.

## Logs

El provider genera logs detallados para debugging:
- Payload enviado al backend
- Respuesta del backend
- Errores de procesamiento

## Consideraciones de Seguridad

- El provider valida el token de autorización en cada request
- Los datos se envían al backend a través de HTTPS
- No se almacenan datos sensibles en logs

## Troubleshooting

### Error: "No se pudo crear el reporte. No se encontró el accessToken"
- Verificar que el header `Authorization` esté presente
- Verificar que el token sea válido

### Error: "Error en la API externa"
- Verificar que el backend esté ejecutándose en `http://localhost:3000`
- Verificar que el endpoint `/api/report/create` esté disponible

### Error: "Paciente no encontrado"
- Verificar que el DNI del paciente sea válido
- Verificar que el paciente exista en la base de datos
