# Testing Guide - MVP ACME

Este documento describe cómo ejecutar tests en el proyecto MVP ACME.

## Tests Unitarios

Los tests unitarios verifican la funcionalidad básica sin dependencias externas.

### Ejecutar todos los tests

```bash
sbt test
```

### Ejecutar un test específico

```bash
sbt "testOnly com.acme.service.AcmeServiceUnitTest"
```

### Tests incluidos

#### AcmeServiceUnitTest

Tests para el servicio ACME:

**Configuración y gestión de claves:**
- ✅ Instanciación con configuración por defecto
- ✅ Configuración con parámetros personalizados
- ✅ Creación y carga de pares de claves RSA
- ✅ Generación de claves RSA de 2048 bits

**Tests de revokeCertificate:**
- ✅ Falla cuando el archivo de certificado no existe
- ✅ Falla cuando el certificado existe pero la clave de dominio falta

**Tests de createCertificate:**
- ✅ Falla con mensaje útil cuando el servidor ACME es inalcanzable

#### AcmeServiceIntegrationTest (con Pebble y Testcontainers)

Tests de integración completos usando Pebble:

**Infraestructura:**
- ✅ Pebble container inicia y es accesible
- ✅ AcmeService se conecta al servidor Pebble ACME
- ✅ Claves de cuenta se crean correctamente

**Flujo ACME:**
- ✅ Intento de creación de certificado con challenges auto-validados
- ✅ Revocación falla apropiadamente cuando el certificado no existe

**Ejecución:**
```bash
sbt "testOnly com.acme.service.AcmeServiceIntegrationTest"
```

**Resultado:**
```
[info] Run completed in 5 seconds, 497 milliseconds.
[info] Total number of tests run: 5
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 5, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

## Tests de Integración con Pebble

[Pebble](https://github.com/letsencrypt/pebble) es un servidor ACME de prueba pequeño diseñado por Let's Encrypt para probar clientes ACME.

### Opción 1: Usando Docker directamente

#### 1. Iniciar Pebble

```bash
docker run --rm -it \
  -e PEBBLE_VA_NOSLEEP=1 \
  -e PEBBLE_VA_ALWAYS_VALID=1 \
  -p 14000:14000 \
  -p 15000:15000 \
  letsencrypt/pebble:latest \
  pebble -config /test/config/pebble-config.json -strict false
```

#### 2. Configurar la aplicación para usar Pebble

Modifica `application.yml` temporalmente o crea un perfil de test:

```yaml
acme:
  server-url: https://localhost:14000/dir
```

#### 3. Ejecutar la aplicación

```bash
sbt run
```

#### 4. Probar los endpoints

```bash
# Health check
curl http://localhost:8080/api/certificates/health

# Crear certificado (Pebble auto-valida challenges con PEBBLE_VA_ALWAYS_VALID=1)
curl -X POST http://localhost:8080/api/certificates/create \
  -H "Content-Type: application/json" \
  -d '{"domain":"test.example.com"}'
```

### Opción 2: Usando Testcontainers (Requiere Docker)

#### Requisitos

1. Docker instalado y corriendo
2. Testcontainers configurado

#### Ejecutar tests de integración

```bash
# Nota: Los tests con Testcontainers requieren configuración adicional
# y están documentados para referencia futura
sbt "testOnly *IntegrationTest"
```

### Características de Pebble

- **PEBBLE_VA_NOSLEEP=1**: No espera entre intentos de validación
- **PEBBLE_VA_ALWAYS_VALID=1**: Acepta todos los challenges automáticamente (ideal para tests)
- **Puerto 14000**: API ACME
- **Puerto 15000**: API de gestión
- **Certificados auto-firmados**: Pebble usa certificados HTTPS auto-firmados

### Limitaciones

1. **Certificados no confiables**: Los certificados generados por Pebble son para pruebas solamente
2. **Configuración SSL**: acme4j necesita configuración adicional para confiar en los certificados de Pebble
3. **Docker requerido**: Tests con Testcontainers requieren Docker

## Estructura de Tests

```
src/test/
├── scala/com/acme/
│   └── service/
│       └── AcmeServiceUnitTest.scala    # Tests unitarios del servicio
└── resources/
    ├── application-test.yml             # Configuración para tests
    └── logback-test.xml                 # Configuración de logging para tests
```

## Configuración de Tests

### application-test.yml

Configuración específica para tests:
- Puerto aleatorio para evitar conflictos
- Logs en nivel DEBUG para debugging

### logback-test.xml

Configuración de logging para tests:
- Formato simplificado
- Logs de testcontainers en INFO
- Logs de la aplicación en DEBUG

## Mejoras Futuras

### Tests de Integración Completos

Para implementar tests de integración completos con Pebble:

1. **Configurar SSL Trust**: Crear un `TrustManager` que acepte los certificados de Pebble
2. **Testcontainers personalizados**: Implementar un container customizado con configuración de Pebble
3. **Mock HTTP Server**: Para simular el challenge HTTP-01
4. **Tests E2E**: Tests end-to-end que validen todo el flujo ACME

### Ejemplo de configuración SSL para Pebble

```scala
// Deshabilitar verificación SSL para tests (solo en tests!)
val trustManager = new X509TrustManager {
  def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = {}
  def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = {}
  def getAcceptedIssuers(): Array[X509Certificate] = Array.empty
}

val sslContext = SSLContext.getInstance("TLS")
sslContext.init(null, Array(trustManager), null)
HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory)
```

## Ejecutar Tests en CI/CD

### GitHub Actions ejemplo

```yaml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run tests
        run: sbt test
```

## Tips de Testing

1. **Directorios temporales**: Los tests usan directorios temporales que se limpian automáticamente
2. **Aislamiento**: Cada test crea su propio directorio de keys para evitar interferencias
3. **Java 25**: Los tests están configurados para funcionar con Java 25 usando `Test / fork := true`

## Troubleshooting

### Error: "Docker not found"

Instala Docker Desktop para ejecutar tests con Testcontainers.

### Error: "Port already in use"

Asegúrate de que no hay otra instancia de la aplicación corriendo en el puerto 8080.

### Error: "SSL handshake failed" con Pebble

Pebble usa certificados auto-firmados. Necesitas configurar tu cliente para confiar en ellos (solo en tests).
