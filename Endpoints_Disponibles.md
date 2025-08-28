# Endpoints FHIR Disponibles

## 🎯 **Servidor FHIR Intermedio**

Tu servidor FHIR actúa como **intermediario** entre múltiples servicios, traduciendo datos a formato FHIR estándar y devolviendo respuestas simples en JSON.

## 📍 **Location (Ubicaciones)**

### **1. Obtener Todas las Provincias**
```
GET /fhir/Location?_type=province
```
**Respuesta:** Array de recursos Location con tipo "PROV"
**Uso:** Listar todas las provincias disponibles

### **2. Obtener Todas las Ciudades**
```
GET /fhir/Location?_type=city
```
**Respuesta:** Array de recursos Location con tipo "CITY"
**Uso:** Listar todas las ciudades disponibles

### **3. Obtener Ciudades por Provincia** ⭐ **NUEVO**
```
GET /fhir/Location?provincia={id_provincia}
```
**Respuesta:** Array de recursos Location filtrados por provincia
**Uso:** Obtener solo las ciudades de una provincia específica
**Ejemplo:** `/fhir/Location?provincia=1`

### **4. Obtener Provincia por ID**
```
GET /fhir/Location/{id_provincia}
```
**Respuesta:** Un recurso Location individual
**Uso:** Obtener datos de una provincia específica

### **5. Obtener Ciudad por ID**
```
GET /fhir/Location/{id_ciudad}
```
**Respuesta:** Un recurso Location individual
**Uso:** Obtener datos de una ciudad específica

## 🏢 **Organization (Organizaciones)**

### **1. Obtener Todas las Mutuales**
```
GET /fhir/Organization?_type=insurance
```
**Respuesta:** Array de recursos Organization con tipo "INS"
**Uso:** Listar todas las mutuales disponibles

### **2. Obtener Todas las Prestaciones**
```
GET /fhir/Organization?_type=program
```
**Respuesta:** Array de recursos Organization con tipo "PROG"
**Uso:** Listar todas las prestaciones disponibles

### **3. Obtener Mutual por ID**
```
GET /fhir/Organization/{id_mutual}
```
**Respuesta:** Un recurso Organization individual
**Uso:** Obtener datos de una mutual específica

### **4. Obtener Prestación por ID**
```
GET /fhir/Organization/{id_prestacion}
```
**Respuesta:** Un recurso Organization individual
**Uso:** Obtener datos de una prestación específica

## 👤 **Patient (Pacientes)**

### **1. Obtener Paciente por DNI**
```
GET /fhir/Patient/{dni}
```
**Respuesta:** Un recurso Patient individual
**Uso:** Obtener datos de un paciente específico

### **2. Crear Nuevo Paciente**
```
POST /fhir/Patient
```
**Body:** Recurso Patient en formato JSON
**Uso:** Registrar un nuevo paciente en el sistema

## 🔧 **Parámetros de Consulta Válidos**

### **Parámetros Estándar FHIR:**
- `_type` - Filtrar por tipo de recurso
- `_count` - Limitar número de resultados
- `_format` - Especificar formato de respuesta

### **Parámetros Personalizados:**
- `provincia` - Filtrar ciudades por provincia (solo para Location)

## ❌ **Parámetros NO Válidos (Causan Error 400)**

### **Parámetros Bundle (No Soportados):**
- `partof=Location/{id}` ❌
- `_include` ❌
- `_revinclude` ❌
- `_summary` ❌

### **Parámetros de Búsqueda Avanzada:**
- `_text` ❌
- `_content` ❌
- `_list` ❌

## 📊 **Formato de Respuesta**

### **Respuesta Exitosa:**
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

### **Respuesta de Error:**
```json
{
  "resourceType": "OperationOutcome",
  "issue": [
    {
      "severity": "error",
      "code": "invalid",
      "diagnostics": "Parameter 'partof' is not supported"
    }
  ]
}
```

## 🚀 **Uso Correcto en el Frontend**

### **Antes (Incorrecto - Error 400):**
```javascript
// ❌ Esto causa error 400
const response = await useAxios.get(`${urlFhirLocation}?_type=city&partof=Location/${id_provincia}`);
```

### **Después (Correcto):**
```javascript
// ✅ Usar el endpoint específico
const response = await useAxios.get(`${urlFhirLocation}?provincia=${id_provincia}`);
```

## 🔍 **Debugging de Errores 400**

### **1. Verificar Parámetros:**
- Solo usar parámetros válidos listados arriba
- No usar parámetros Bundle estándar
- Verificar que los valores no estén vacíos

### **2. Verificar Headers:**
- `Authorization: Bearer {token}` debe estar presente
- `Content-Type: application/json` para POST

### **3. Verificar URLs:**
- Las URLs deben ser exactas
- No usar parámetros de query no soportados

## ✅ **Ventajas de tu Configuración**

1. **Simplicidad:** Respuestas directas sin Bundle
2. **Interoperabilidad:** Formato FHIR R5 estándar
3. **Eficiencia:** Filtrado en el servidor, no en el cliente
4. **Flexibilidad:** Endpoints específicos para casos de uso comunes
5. **Debugging:** Logs detallados en el servidor

## 🔄 **Próximos Pasos**

1. **Reinicia el servidor FHIR** para cargar el nuevo endpoint
2. **Prueba el endpoint** de ciudades por provincia
3. **Verifica en los logs** que no haya errores
4. **Testea en el frontend** la funcionalidad de ciudades
