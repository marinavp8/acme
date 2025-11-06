# Guía Rápida: Pruebas con Pebble

Pebble es un servidor ACME de prueba que te permite probar la creación de certificados SSL sin necesidad de un dominio real o configuración de DNS.

## 🚀 Inicio Rápido (3 pasos)

### Paso 1: Iniciar Pebble

```bash
docker run --rm -d \
  --name pebble-acme \
  -e PEBBLE_VA_NOSLEEP=1 \
  -e PEBBLE_VA_ALWAYS_VALID=1 \
  -p 14000:14000 \
  -p 15000:15000 \
  letsencrypt/pebble:latest \
  pebble -config /test/config/pebble-config.json -strict false
```

### Paso 2: Iniciar la aplicación con perfil Pebble

```bash
./run-with-pebble.sh
```

O manualmente:

```bash
sbt "run -Dspring.profiles.active=pebble"
```

### Paso 3: Probar creación de certificado

```bash
curl -X POST http://localhost:8080/api/certificates/create \
  -H "Content-Type: application/json" \
  -d '{"domain":"myapp-test.com"}'
```

**Resultado esperado:**

```json
{
  "success": true,
  "message": "Certificate created successfully for domain: myapp-test.com",
  "data": {
    "domain": "myapp-test.com",
    "certificatePath": "/path/to/keys-pebble-test/certificate.crt",
    "certificateChainPath": "/path/to/keys-pebble-test/certificate-chain.crt",
    "status": "VALID"
  }
}
```

## 🎯 Ventajas de Pebble

- ✅ **No necesitas dominio real** - Cualquier nombre funciona
- ✅ **Auto-validación** - Con `PEBBLE_VA_ALWAYS_VALID=1`, todos los challenges se validan automáticamente
- ✅ **Sin rate limits** - Prueba cuantas veces quieras
- ✅ **Rápido** - No hay esperas entre validaciones con `PEBBLE_VA_NOSLEEP=1`
- ✅ **Local** - Todo corre en tu máquina

## 📋 Scripts Disponibles

### `run-with-pebble.sh`
Inicia la aplicación configurada para usar Pebble:
- Verifica que Pebble esté corriendo
- Configura la aplicación con el perfil `pebble`
- Deshabilita verificación SSL (necesario para certificados auto-firmados de Pebble)

### `test-pebble.sh`
Script de prueba automatizado:
- Verifica que todo esté funcionando
- Hace health check
- Intenta crear un certificado
- Muestra los resultados

```bash
./test-pebble.sh
```

## 🔧 Configuración

La configuración de Pebble está en `application-pebble.yml`:

```yaml
acme:
  server-url: https://localhost:14000/dir
  key-dir: keys-pebble-test
  auto-validate: true  # Pebble valida automáticamente
  disable-ssl-verification: true  # Solo para testing!
```

## 🧪 Ejemplos de Pruebas

### Crear certificado para dominio de prueba

```bash
curl -X POST http://localhost:8080/api/certificates/create \
  -H "Content-Type: application/json" \
  -d '{"domain":"miapp.com"}'
```

### Crear certificado para subdominio

```bash
curl -X POST http://localhost:8080/api/certificates/create \
  -H "Content-Type: application/json" \
  -d '{"domain":"api.miapp.com"}'
```

### ⚠️ Dominios bloqueados

Algunos dominios están bloqueados por política (RFC 2606):
- ❌ `*.example.com`, `*.example.org`, `*.example.net`
- ❌ `localhost`, `*.localhost`
- ❌ `test`, `*.test`

Usa dominios inventados pero realistas como:
- ✅ `myapp.com`, `acme-test.org`, `mydomain.net`
- ✅ `api.myapp.com`, `staging.mydomain.com`

### Verificar archivos generados

```bash
ls -lh keys-pebble-test/
```

Deberías ver:
- `account.key` - Clave de cuenta ACME
- `domain.key` - Clave privada del dominio
- `certificate.crt` - Certificado SSL
- `certificate-chain.crt` - Cadena de certificados

### Revocar certificado

```bash
curl -X POST http://localhost:8080/api/certificates/revoke \
  -H "Content-Type: application/json" \
  -d '{"domain":"miapp.com"}'
```

## 🛑 Detener Pebble

```bash
docker stop pebble-acme
```

## ⚠️ Importante

1. **Solo para testing**: Los certificados generados por Pebble NO son válidos para producción
2. **SSL deshabilitado**: La verificación SSL está deshabilitada solo cuando usas el perfil `pebble`
3. **Archivos separados**: Las claves se guardan en `keys-pebble-test/` para no interferir con claves de producción

## 🐛 Troubleshooting

### Error: "Pebble no está corriendo"

```bash
# Verificar si Pebble está corriendo
docker ps | grep pebble

# Ver logs de Pebble
docker logs pebble-acme

# Reiniciar Pebble
docker stop pebble-acme
docker run --rm -d --name pebble-acme ... (comando completo de arriba)
```

### Error: "Connection refused"

Espera unos segundos para que Pebble inicie completamente:

```bash
# Verificar que Pebble responde
curl -k https://localhost:14000/dir
```

### Error de compilación

```bash
# Limpiar y recompilar
sbt clean compile
```

## 📚 Referencias

- [Pebble GitHub](https://github.com/letsencrypt/pebble)
- [ACME Protocol](https://tools.ietf.org/html/rfc8555)
- [Let's Encrypt](https://letsencrypt.org/)

