# Configuración FHIR - Respuestas Simples

## Cambio Implementado

Se ha modificado la configuración del servidor FHIR para que devuelva **respuestas simples en formato JSON** en lugar de respuestas Bundle, manteniendo la compatibilidad con el estándar FHIR R5.

## Antes vs Después

### **Antes (Bundle Format):**
```json
{
  "resourceType": "Bundle",
  "type": "searchset",
  "total": 2,
  "entry": [
    {
      "resource": {
        "resourceType": "Location",
        "id": "1",
        "name": "Buenos Aires",
        "status": "active"
      }
    },
    {
      "resource": {
        "resourceType": "Location",
        "id": "2",
        "name": "Córdoba",
        "status": "active"
      }
    }
  ]
}
```

### **Después (Simple Array):**
```json
[
  {
    "resourceType": "Location",
    "id": "1",
    "name": "Buenos Aires",
    "status": "active"
  },
  {
    "resourceType": "Location",
    "id": "2",
    "name": "Córdoba",
    "status": "active"
  }
]
```

## Configuración del Servidor

### **1. FhirServerConfig.java**
```java
@Override
protected void initialize() {
    setFhirContext(FhirContext.forR5());
    setDefaultPrettyPrint(true);
    setDefaultResponseEncoding(EncodingEnum.JSON);
    
    // Configurar para devolver respuestas simples en lugar de Bundle
    setDefaultSearchResponseStyle(SearchResponseStyle.SIMPLE);
    
    setResourceProviders(List.of(patientResourceProvider, abmResourceProvider, organizationResourceProvider));
}
```

### **2. application.properties**
```properties
# Configuración para respuestas FHIR simples (no Bundle)
hapi.fhir.default_search_response_style=SIMPLE
hapi.fhir.auto_create_placeholder_reference_targets=false
hapi.fhir.allow_override_default_search_params=true
```

## Endpoints Disponibles

### **Location (Ubicaciones)**
```
GET /fhir/Location?_type=province    # Devuelve array de provincias
GET /fhir/Location?_type=city        # Devuelve array de ciudades
GET /fhir/Location/{id}              # Devuelve una ubicación específica
```

### **Organization (Organizaciones)**
```
GET /fhir/Organization?_type=insurance  # Devuelve array de mutuales
GET /fhir/Organization?_type=program    # Devuelve array de prestaciones
GET /fhir/Organization/{id}             # Devuelve una organización específica
```

### **Patient (Pacientes)**
```
GET /fhir/Patient/{dni}               # Devuelve un paciente específico
POST /fhir/Patient                    # Crea un nuevo paciente
```

## Estructura de Respuesta

### **Array de Locations (Provincias)**
```json
[
  {
    "resourceType": "Location",
    "id": "1",
    "name": "Buenos Aires",
    "status": "active",
    "type": [
      {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v3-RoleCode",
            "code": "PROV",
            "display": "Provincia"
          }
        ]
      }
    ]
  }
]
```

### **Array de Organizations (Mutuales)**
```json
[
  {
    "resourceType": "Organization",
    "id": "1",
    "name": "OSDE",
    "active": true,
    "type": [
      {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/organization-type",
            "code": "INS",
            "display": "Insurance Company"
          }
        ]
      }
    ]
  }
]
```

## Ventajas de la Nueva Configuración

### **1. Simplicidad**
- **Respuestas más simples** y fáciles de procesar
- **Menos anidación** en la estructura JSON
- **Mejor legibilidad** para desarrolladores

### **2. Compatibilidad Frontend**
- **Arrays nativos** que Vue.js puede manejar directamente
- **No necesita procesamiento** especial para extraer recursos
- **Menos código** en el frontend

### **3. Estándar FHIR R5**
- **Mantiene compatibilidad** con el estándar FHIR
- **Respuestas válidas** según especificación R5
- **Interoperabilidad** con otros sistemas FHIR

## Procesamiento en el Frontend

### **Función Helper Actualizada**
```javascript
const processFhirResponse = (data) => {
  // Si es un array, devolverlo directamente
  if (Array.isArray(data)) {
    return data;
  }
  
  // Si es un Bundle, extraer los recursos
  if (data.resourceType === 'Bundle' && data.entry) {
    return data.entry.map(entry => entry.resource);
  }
  
  // Si es un solo recurso, devolverlo en un array
  if (data.resourceType) {
    return [data];
  }
  
  // Fallback: devolver el dato tal como viene
  return data;
};
```

### **Uso en Actions**
```javascript
export const obtenerProvincias = async () => {
  try {
    const response = await useAxios.get(`${urlFhirLocation}?_type=province`);
    const processedData = processFhirResponse(response.data);
    return processedData;
  } catch (error) {
    console.error('Error obteniendo provincias:', error);
    throw error;
  }
};
```

## Verificación

### **Test con curl**
```bash
# Provincias (debería devolver array simple)
curl -H "Authorization: Bearer {token}" \
     "http://localhost:8080/fhir/Location?_type=province"

# Mutuales (debería devolver array simple)
curl -H "Authorization: Bearer {token}" \
     "http://localhost:8080/fhir/Organization?_type=insurance"
```

### **Verificar en el Frontend**
- Los selects deberían cargar sin errores `_vnode`
- Los datos deberían venir como arrays simples
- No debería haber necesidad de procesar Bundle

## Notas Importantes

- **Reinicia el servidor FHIR** después de los cambios
- **Verifica que CORS** esté funcionando correctamente
- **Los logs del servidor** deberían mostrar las respuestas simples
- **El frontend** ahora recibe arrays directos en lugar de Bundle
