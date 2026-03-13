## Endpoints FHIR del serverFhir

**Suposiciones generales**

- **Base URL FHIR**: `/fhir`
- **Auth**: la mayoría de endpoints requieren header `Authorization: Bearer {access_token}`.
- **Formato**: JSON FHIR R5.

---

## Patient (`Patient`)

### GET `/fhir/Patient/{id}`

- **Descripción**: Obtener paciente por `hash_id`.
- **Backend**: `GET /patient/{hash_id}`
- **Headers**:
  - `Authorization: Bearer {access_token}`
- **Path params**:
  - `id`: `hash_id` del paciente.
- **Body request**: *(no aplica)*  
- **Body response (ejemplo completo)**:

```json
{
  "resourceType": "Patient",
  "id": "HASH123",
  "identifier": [
    { "value": "HASH123" },
    {
      "system": "http://mi-servidor.com/fhir/dni",
      "value": "12345678"
    }
  ],
  "name": [
    {
      "family": "García",
      "given": ["Juan"]
    }
  ],
  "birthDate": "1990-01-01",
  "active": true
}
```

---

### GET `/fhir/Patient`

- **Descripción**: Listar pacientes, con opción de incluir inactivos.
- **Backend**:  
  - `GET /patient`  
  - `GET /patient?includeInactive=true`
- **Headers**:
  - `Authorization: Bearer {access_token}`
- **Query params**:
  - `includeInactive` (opcional): `"true"` para incluir inactivos.
- **Body request**: *(no aplica)*  
- **Body response (ejemplo)**: `Bundle` con múltiples `Patient`.

```json
{
  "resourceType": "Bundle",
  "type": "searchset",
  "entry": [
    { "resource": { "resourceType": "Patient", "id": "HASH123" } },
    { "resource": { "resourceType": "Patient", "id": "HASH456" } }
  ]
}
```

---

### POST `/fhir/Patient`

- **Descripción**: Crear un nuevo paciente.
- **Backend**: `POST /patient`
- **Headers**:
  - `Authorization: Bearer {access_token}`
  - `Content-Type: application/fhir+json`
- **Body request (ejemplo completo)**:

```json
{
  "resourceType": "Patient",
  "identifier": [
    {
      "system": "http://mi-servidor.com/fhir/dni",
      "value": "12345678"
    }
  ],
  "name": [
    {
      "family": "García",
      "given": ["Juan", "Pablo"]
    }
  ],
  "birthDate": "1990-01-01",
  "telecom": [
    {
      "system": "phone",
      "value": "3512345678"
    },
    {
      "system": "email",
      "value": "juan.p.garcia@example.com"
    }
  ],
  "address": [
    {
      "line": ["Calle Falsa 123 1°A"],
      "city": "Córdoba",
      "state": "Córdoba",
      "country": "AR"
    }
  ],
  "extension": [
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/id_mutual",
      "valueString": "20"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/numero_afiliado",
      "valueString": "ABC-123456"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/prestacion",
      "valueString": "Rehabilitación"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/id_prestacion",
      "valueString": "15"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/calle",
      "valueString": "Calle Falsa"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/numero",
      "valueString": "123"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/barrio",
      "valueString": "Centro"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/id_ciudad",
      "valueString": "1"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/id_provincia",
      "valueString": "2"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/piso_departamento",
      "valueString": "1°A"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/con_quien_vive",
      "valueString": "Padres"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/ocupacion_actual",
      "valueString": "Estudiante"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/ocupacion_anterior",
      "valueString": "Empleado administrativo"
    }
  ]
}
```

---

### PUT `/fhir/Patient/{id}`

- **Descripción general**: Actualización, activación o desactivación lógica.
- **Headers**:
  - `Authorization: Bearer {access_token}`
  - `Content-Type: application/fhir+json`
- **Path params**:
  - `id`: `hash_id` del paciente.

#### a) Desactivar paciente (borrado lógico)

- **Regla**: enviar `active=false` (y opcionalmente solo ese campo).
- **Backend**: `DELETE /patient/delete/{hash_id}`
- **Body request (ejemplo completo)**:

```json
{
  "resourceType": "Patient",
  "id": "HASH123",
  "active": false
}
```

#### b) Reactivar paciente

- **Regla**: enviar `active=true` **sin** `birthDate` (indica que solo se cambia estado).
- **Backend**: `PUT /patient/activate/{hash_id}`
- **Body request (ejemplo completo)**:

```json
{
  "resourceType": "Patient",
  "id": "HASH123",
  "active": true
}
```

#### c) Actualizar datos generales

- **Regla**: incluir campos editables (ej.: fecha de nacimiento, teléfono, extensiones).
- **Backend**: `PUT /patient/{hash_id}`
- **Body request (ejemplo completo)**:

```json
{
  "resourceType": "Patient",
  "id": "HASH123",
  "identifier": [
    {
      "system": "http://mi-servidor.com/fhir/dni",
      "value": "12345678"
    }
  ],
  "name": [
    {
      "family": "García",
      "given": ["Juan", "Pablo"]
    }
  ],
  "birthDate": "1990-02-15",
  "telecom": [
    {
      "system": "phone",
      "value": "3519998888"
    },
    {
      "system": "email",
      "value": "juan.p.garcia@example.com"
    }
  ],
  "address": [
    {
      "line": ["Calle Actualizada 456 2°B"],
      "city": "Córdoba",
      "state": "Córdoba",
      "country": "AR"
    }
  ],
  "extension": [
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/id_mutual",
      "valueString": "25"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/numero_afiliado",
      "valueString": "XYZ-987654"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/prestacion",
      "valueString": "Rehabilitación intensiva"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/id_prestacion",
      "valueString": "18"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/calle",
      "valueString": "Calle Actualizada"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/numero",
      "valueString": "456"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/barrio",
      "valueString": "Norte"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/id_ciudad",
      "valueString": "3"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/id_provincia",
      "valueString": "4"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/piso_departamento",
      "valueString": "2°B"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/con_quien_vive",
      "valueString": "Pareja"
    },
    {
      "url": "http://mi-servidor.com/fhir/StructureDefinition/ocupacion_actual",
      "valueString": "Empleado administrativo"
    }
  ]
}
```

---

## Practitioner (`Practitioner`)

### GET `/fhir/Practitioner/$get-user-types`

- **Tipo**: operación `$get-user-types`
- **Descripción**: Devuelve `ValueSet` con tipos de usuario.
- **Backend**: `GET /user/type`
- **Headers**:
  - `Authorization: Bearer {access_token}`
- **Query params**: *(no aplica)*  
- **Body request**: *(no aplica)*  

---

### GET `/fhir/Practitioner`

- **Descripción**: Listar todos los usuarios.
- **Backend**: `GET /user/`
- **Headers**:
  - `Authorization: Bearer {access_token}`
- **Query params**: *(no aplica)*  
- **Body request**: *(no aplica)*  

---

### GET `/fhir/Practitioner/{id}`

- **Descripción**: Obtener usuario por `hash_id`.
- **Backend**: `GET /user/` (se filtra en memoria por `hash_id`).
- **Headers**:
  - `Authorization: Bearer {access_token}`
- **Path params**:
  - `id`: `hash_id` del usuario.

---

### PUT `/fhir/Practitioner/{id}`

- **Descripción general**: Activar/bloquear usuario o actualizar datos.
- **Headers**:
  - `Authorization: Bearer {access_token}`
  - `Content-Type: application/fhir+json`
- **Path params**:
  - `id`: `hash_id` del usuario.

#### a) Sólo activar/bloquear (solo campo `active`)

- **Backend**:
  - `active=true` → `PUT /user/activate/{hash_id}`
  - `active=false` → `DELETE /user/{hash_id}`
- **Body request (ejemplo)**:

```json
{
  "resourceType": "Practitioner",
  "id": "USERHASH1",
  "active": false
}
```

#### b) Actualizar datos completos

- **Backend**:
  - `PUT /user/{hash_id}` (datos)
  - y luego, si `active` viene informado, `PUT /user/activate/{hash_id}` o `DELETE /user/{hash_id}`
- **Body request (ejemplo)**:

```json
{
  "resourceType": "Practitioner",
  "id": "USERHASH1",
  "identifier": [
    {
      "system": "http://mi-servidor.com/fhir/dni",
      "value": "30111222"
    }
  ],
  "name": [
    {
      "family": "Pérez",
      "given": ["Ana"]
    }
  ],
  "telecom": [
    {
      "system": "email",
      "value": "ana.perez@example.com"
    }
  ],
  "birthDate": "1985-06-10",
  "extension": [
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/id-tipo-usuario",
      "valueString": "2"
    }
  ],
  "active": true
}
```

---

### POST `/fhir/Practitioner`

- **Descripción**: Crear usuario.
- **Backend**: `POST /user/create`
- **Headers**:
  - `Authorization: Bearer {access_token}`
  - `Content-Type: application/fhir+json`
- **Body request (ejemplo completo)**:

```json
{
  "resourceType": "Practitioner",
  "identifier": [
    {
      "system": "http://mi-servidor.com/fhir/dni",
      "value": "30111222"
    }
  ],
  "name": [
    {
      "family": "Pérez",
      "given": ["Ana"]
    }
  ],
  "telecom": [
    {
      "system": "phone",
      "value": "3515550000"
    },
    {
      "system": "email",
      "value": "ana.perez@example.com"
    }
  ],
  "birthDate": "1985-06-10",
  "extension": [
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/id-tipo-usuario",
      "valueString": "2"
    }
  ],
  "active": true
}
```

---

## DiagnosticReport – Informes (`ReportResourceProvider`)

### POST `/fhir/DiagnosticReport`

- **Descripción**: Crea informe **o** anexo según extensión `is-annex`.
- **Backend**:
  - Informe normal → `POST /report/create`
  - Anexo → `POST /report/{reportId}/createAnnex`
- **Headers**:
  - `Authorization: Bearer {access_token}`
  - `Content-Type: application/fhir+json`
- **Body request (informe normal, ejemplo completo)**:

```json
{
  "resourceType": "DiagnosticReport",
  "status": "final",
  "category": [
    {
      "text": "Informe médico"
    }
  ],
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "18748-4",
        "display": "Informe de evaluación"
      }
    ],
    "text": "Informe de evaluación"
  },
  "subject": {
    "reference": "Patient/HASH123"
  },
  "effectiveDateTime": "2025-10-01T10:15:30-03:00",
  "issued": "2025-10-01T10:20:00-03:00",
  "conclusion": "Texto completo del informe de evaluación...",
  "extension": [
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/patient-dni",
      "valueString": "12345678"
    },
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/user-id",
      "valueString": "10"
    },
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/report-type",
      "valueString": "evaluacion-inicial"
    },
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/speciality-id",
      "valueString": "5"
    },
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/ehr-id",
      "valueString": "EHR_HASH_1"
    }
  ]
}
```

- **Body request (anexo, ejemplo completo)**:

```json
{
  "resourceType": "DiagnosticReport",
  "status": "final",
  "subject": {
    "reference": "DiagnosticReport/REPORT_HASH_ID"
  },
  "effectiveDateTime": "2025-10-02T09:00:00-03:00",
  "conclusion": "Comentario/anexo adicional sobre la evolución del paciente...",
  "extension": [
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/is-annex",
      "valueBoolean": true
    },
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/user-id",
      "valueString": "10"
    }
  ]
}
```

---

### POST `/fhir/DiagnosticReport/$create-report`

- **Descripción**: Crea informe principal explícitamente.
- **Backend**: `POST /report/create`
- **Headers / Body**: igual que `POST /fhir/DiagnosticReport` (informe normal).

---

### POST `/fhir/DiagnosticReport/$create-annex`

- **Descripción**: Crea anexo/comentario asociado a un informe.
- **Backend**: `POST /report/{reportId}/createAnnex`
- **Headers / Body**: igual que ejemplo de anexo anterior.

---

### GET `/fhir/DiagnosticReport/{id}`

- **Descripción**: Devuelve un informe básico (stub).  
  *(No consulta backend en la implementación actual.)*

---

### GET `/fhir/DiagnosticReport?patient={hashId}`

- **Descripción**: Lista informes del paciente.
- **Backend**: `GET /report/all/{hashId}`
- **Headers**:
  - `Authorization: Bearer {access_token}`
- **Query params**:
  - `patient`: `hash_id` del paciente.

---

### GET `/fhir/DiagnosticReport?annex={reportHashId}`

- **Descripción**: Lista anexos de un informe.
- **Backend**: `GET /report/{reportHashId}/annexes`
- **Headers**:
  - `Authorization: Bearer {access_token}`
- **Query params**:
  - `annex`: hash del informe.

---

### GET `/fhir/DiagnosticReport/$list-reports?patient={hashId}`

- **Descripción**: Igual a `?patient=`, pero devuelve `Bundle` desde operación custom.
- **Backend**: `GET /report/all/{hashId}`

---

## DiagnosticReport – Historia Fisiátrica (`EhrResourceProvider`)

### GET `/fhir/DiagnosticReport?patient={hashId}`  (historia fisiátrica)

- **Descripción**: Obtiene la historia fisiátrica del paciente (lista con un `DiagnosticReport`).
- **Backend**: `GET /ehr/hc-fisiatric/{hashId}`
- **Headers**:
  - `Authorization: Bearer {access_token}`
- **Query params**:
  - `patient`: hash del paciente.

---

### GET `/fhir/DiagnosticReport/$get-historia?patient={hashId}`

- **Descripción**: Devuelve la historia fisiátrica en un `Bundle`.
- **Backend**: `GET /ehr/hc-fisiatric/{hashId}`

---

### GET `/fhir/DiagnosticReport/$get-historia-history?patient={hashId}`

- **Descripción**: Devuelve historial de versiones de la historia fisiátrica.
- **Backend**: `GET /ehr/hc-fisiatric/{hashId}/history`

---

### GET `/fhir/DiagnosticReport/{id}`  (historia fisiátrica por id)

- **Descripción**: Obtiene historia fisiátrica por `ehrHashId`.
- **Backend**: `POST /ehr/hc-fisiatric` con parámetro `ehrHashId`.

---

### POST `/fhir/DiagnosticReport`  (crear historia fisiátrica)

- **Descripción**: Crea una nueva historia fisiátrica.
- **Backend**: `POST /ehr/hc-fisiatric`
- **Headers**:
  - `Authorization: Bearer {access_token}`
  - `Content-Type: application/fhir+json`
- **Body request (ejemplo completo, orientativo)**:

```json
{
  "resourceType": "DiagnosticReport",
  "status": "final",
  "subject": {
    "reference": "Patient/HASH123"
  },
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "11450-4",
        "display": "Problem List Reported"
      }
    ],
    "text": "Historia Clínica Fisiátrica"
  },
  "effectiveDateTime": "2025-10-01T10:15:30-03:00",
  "issued": "2025-10-01T10:20:00-03:00",
  "conclusion": "Resumen textual de la historia clínica fisiátrica...",
  "extension": [
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/evaluacion-consulta",
      "valueString": "{\"derivadosPor\":\"Traumatología\",\"medicacionActual\":\"Ibuprofeno\",\"antecedentesCuadro\":\"Esguince de tobillo hace 2 meses\",\"estudiosRealizados\":\"RMN de tobillo\"}"
    },
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/fisiologico",
      "valueString": "{\"peso\":70,\"talla\":1.75,\"imc\":22.9}"
    },
    {
      "url": "http://mi-servidor/fhir/StructureDefinition/antecedentes-personales",
      "valueString": "{\"patologicos\":\"Asma leve\",\"quirurgicos\":\"Apendicectomía 2010\"}"
    }
  ]
}
```

---

### POST `/fhir/DiagnosticReport/$create-historia`

- **Descripción**: Atajo para crear historia fisiátrica (llama internamente a `createHistoriaFisiatrica`).
- **Backend**: `POST /ehr/hc-fisiatric`

---

## DocumentReference (`DocumentReference`)

### POST `/fhir/DocumentReference`

- **Descripción**: Crea un `DocumentReference` lógico (sin gestionar el archivo físico aquí).
- **Headers**:
  - `Authorization: Bearer {access_token}`
  - `Content-Type: application/fhir+json`
- **Body request (ejemplo completo)**:

```json
{
  "resourceType": "DocumentReference",
  "status": "current",
  "type": {
    "text": "Archivo"
  },
  "subject": {
    "reference": "Patient/HASH123"
  },
  "description": "Informe de radiografía de tobillo",
  "content": [
    {
      "attachment": {
        "contentType": "application/pdf",
        "url": "https://storage.example.com/files/tobillo-123.pdf",
        "title": "rx-tobillo-123.pdf"
      }
    }
  ],
  "extension": [
    {
      "url": "http://example.org/fhir/StructureDefinition/file-type",
      "valueString": "pdf"
    },
    {
      "url": "http://example.org/fhir/StructureDefinition/file-url",
      "valueString": "https://storage.example.com/files/tobillo-123.pdf"
    },
    {
      "url": "http://example.org/fhir/StructureDefinition/file-name",
      "valueString": "rx-tobillo-123.pdf"
    }
  ]
}
```

---

### GET `/fhir/DocumentReference/$get-files?patient={hashId}&fileType={type}`

- **Descripción**: Devuelve archivos del paciente como `Bundle` de `DocumentReference`.
- **Backend**: `GET /file?hash_id={hashId}[&fileType={type}]`
- **Headers**:
  - `Authorization: Bearer {access_token}`
- **Query params**:
  - `patient` (obligatorio): hash del paciente.
  - `fileType` (opcional): tipo de archivo (ej. `"image"`, `"pdf"`, etc.).

---

### GET `/fhir/DocumentReference/{id}`

- **Descripción**: Devuelve `DocumentReference` básico (stub).

---

### GET `/fhir/DocumentReference?patient={hashId}&fileType={type}`

- **Descripción**: Lista archivos como array de `DocumentReference`.
- **Backend**: `GET /file?hash_id={hashId}[&fileType={type}]`

---

## Organization (`Organization`)

### GET `/fhir/Organization?_type=insurance`

- **Descripción**: Lista mutuales como `Organization`.
- **Backend**: `GET /abm/mutuales`
- **Headers**:
  - `Authorization: Bearer {access_token}`
- **Query params**:
  - `_type=insurance`

---

### GET `/fhir/Organization?_type=program`

- **Descripción**: Lista prestaciones como `Organization`.
- **Backend**: `GET /abm/prestaciones`

---

### GET `/fhir/Organization/{id}` (mutual)

- **Descripción**: Obtiene mutual por `id_mutual`.
- **Backend**: `GET /abm/mutuales` y filtrado por `id_mutual`.

---

### GET `/fhir/Organization/{id}` (prestación)

- **Descripción**: Obtiene prestación por `id_prestacion`.
- **Backend**: `GET /abm/prestaciones` y filtrado por `id_prestacion`.

---

## Location (`Location`)

### GET `/fhir/Location?_type=province`

- **Descripción**: Lista provincias como `Location`.
- **Backend**: `GET /abm/provincias`
- **Headers**:
  - `Authorization: Bearer {access_token}`

---

### GET `/fhir/Location`

- **Descripción**: Lista todas las ciudades como `Location`.
- **Backend**: `GET /abm/ciudades`

---

### GET `/fhir/Location?provincia={idProvincia}`

- **Descripción**: Lista ciudades de una provincia específica.
- **Backend**: `GET /abm/ciudades/{id_provincia}`
- **Query params**:
  - `provincia`: id de la provincia.

---

### GET `/fhir/Location/{id}` (provincia)

- **Descripción**: Obtiene una provincia por `id_provincia`.
- **Backend**: `GET /abm/provincias` y filtrado.

---

### GET `/fhir/Location/{id}` (ciudad)

- **Descripción**: Obtiene una ciudad por `id_ciudad`.
- **Backend**: `GET /abm/ciudades` y filtrado.

