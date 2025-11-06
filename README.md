# MVP ACME - Scala Spring Boot ACME Client

Application Scala con Spring Boot para gestionar certificados SSL/TLS usando el protocolo ACME (Let's Encrypt staging environment).

## Características

- **Crear certificados SSL/TLS** mediante el protocolo ACME
- **Revocar certificados** existentes
- Uso del entorno de staging de Let's Encrypt para pruebas seguras
- API REST simple y directa

## Requisitos

- Java 11 o superior
- Scala 2.13.x
- SBT 1.9.x

## Construcción y Ejecución

### Compilar
```bash
sbt compile
```

### Ejecutar
```bash
sbt run
```

La aplicación se ejecutará en `http://localhost:8080`

### Ejecutar tests
```bash
sbt test
```

## Endpoints API

### 1. Crear Certificado

**POST** `/api/certificates/create`

```json
{
  "domain": "example.com"
}
```

**Nota**: Este endpoint requiere que configures el challenge HTTP-01. La aplicación te indicará qué archivo debes hacer disponible en tu servidor.

### 2. Revocar Certificado

**POST** `/api/certificates/revoke`

```json
{
  "domain": "example.com"
}
```

### 3. Health Check

**GET** `/api/certificates/health`

## Pruebas con curl

### Crear certificado:
```bash
curl -X POST http://localhost:8080/api/certificates/create \
  -H "Content-Type: application/json" \
  -d '{"domain":"tu-dominio.com"}'
```

### Revocar certificado:
```bash
curl -X POST http://localhost:8080/api/certificates/revoke \
  -H "Content-Type: application/json" \
  -d '{"domain":"tu-dominio.com"}'
```

## Configuración ACME

La aplicación usa el servidor de staging de Let's Encrypt:
- URL: `acme://letsencrypt.org/staging`
- Los certificados generados NO son válidos para producción
- No hay límites de rate limiting estrictos

## Estructura del Proyecto

```
src/main/scala/com/acme/
├── AcmeApplication.scala          # Punto de entrada de Spring Boot
├── controller/
│   └── CertificateController.scala # REST API endpoints
├── service/
│   └── AcmeService.scala          # Lógica de negocio ACME
└── config/
    └── JacksonConfig.scala        # Configuración JSON para Scala
```

## Almacenamiento de Claves

Las claves y certificados se almacenan en el directorio `keys/`:
- `account.key` - Clave de la cuenta ACME
- `domain.key` - Clave privada del dominio
- `certificate.crt` - Certificado generado
- `certificate-chain.crt` - Cadena de certificados

**⚠️ IMPORTANTE**: Nunca subas estas claves a control de versiones.

## Testing

El proyecto incluye tests unitarios completos y soporte para tests de integración con Pebble.

**Ejecutar tests:**
```bash
sbt test
```

**Ver guía completa de testing:** `TESTING.md`

## Documentación Adicional

- **TESTING.md** - Guía completa de testing, incluyendo cómo usar Pebble para tests de integración
- **USAGE_EXAMPLES.md** - Ejemplos detallados de uso de la API
- **CLAUDE.md** - Guía técnica para desarrollo (arquitectura, patrones, configuración)

## Notas de Seguridad

- Este proyecto usa el entorno de **staging** de Let's Encrypt
- Los certificados generados son de prueba y no deben usarse en producción
- Para producción, cambia la URL del servidor ACME a `acme://letsencrypt.org`
