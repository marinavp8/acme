# Ejemplos de Uso - MVP ACME

Este documento proporciona ejemplos prácticos para usar la aplicación ACME.

## Prerequisitos

1. Tener un dominio que controles
2. Capacidad de configurar un servidor HTTP en ese dominio
3. La aplicación corriendo en `http://localhost:8080`

## Iniciar la Aplicación

```bash
sbt run
```

La aplicación iniciará en el puerto 8080.

## 1. Health Check

Verifica que la aplicación esté corriendo:

```bash
curl http://localhost:8080/api/certificates/health
```

**Respuesta esperada:**
```json
{
  "success": true,
  "message": "ACME service is running"
}
```

## 2. Crear Certificado

### Paso 1: Hacer la primera petición

```bash
curl -X POST http://localhost:8080/api/certificates/create \
  -H "Content-Type: application/json" \
  -d '{"domain":"ejemplo.com"}'
```

**Respuesta esperada (primera vez):**
```json
{
  "success": false,
  "error": "Failed to create certificate: Please set up HTTP challenge:\nURL: http://ejemplo.com/.well-known/acme-challenge/TOKEN_AQUI\nContent: AUTHORIZATION_STRING_AQUI\nThen call the endpoint again to continue."
}
```

### Paso 2: Configurar el Challenge HTTP-01

Necesitas hacer que la URL indicada devuelva el contenido especificado.

**Opciones:**

#### Opción A: Usando un servidor simple de Python

```bash
# Crear directorio para el challenge
mkdir -p .well-known/acme-challenge

# Guardar el contenido de autorización
echo "AUTHORIZATION_STRING_AQUI" > .well-known/acme-challenge/TOKEN_AQUI

# Iniciar servidor HTTP simple
python3 -m http.server 80
```

#### Opción B: Configuración en Nginx

```nginx
server {
    listen 80;
    server_name ejemplo.com;

    location /.well-known/acme-challenge/ {
        alias /var/www/acme-challenge/;
    }
}
```

Luego crear el archivo:
```bash
mkdir -p /var/www/acme-challenge
echo "AUTHORIZATION_STRING_AQUI" > /var/www/acme-challenge/TOKEN_AQUI
```

#### Opción C: Configuración en Apache

```apache
<VirtualHost *:80>
    ServerName ejemplo.com

    Alias /.well-known/acme-challenge/ /var/www/acme-challenge/

    <Directory /var/www/acme-challenge/>
        Options None
        AllowOverride None
        Require all granted
    </Directory>
</VirtualHost>
```

### Paso 3: Verificar que el challenge esté accesible

```bash
curl http://ejemplo.com/.well-known/acme-challenge/TOKEN_AQUI
```

Debe devolver exactamente el string de autorización.

### Paso 4: Llamar al endpoint de nuevo

Una vez configurado el challenge, vuelve a hacer la petición:

```bash
curl -X POST http://localhost:8080/api/certificates/create \
  -H "Content-Type: application/json" \
  -d '{"domain":"ejemplo.com"}'
```

**Respuesta esperada (éxito):**
```json
{
  "success": true,
  "message": "Certificate created successfully for domain: ejemplo.com",
  "data": {
    "domain": "ejemplo.com",
    "certificatePath": "/path/to/keys/certificate.crt",
    "certificateChainPath": "/path/to/keys/certificate-chain.crt",
    "status": "VALID"
  }
}
```

### Paso 5: Verificar los archivos generados

Los certificados y claves se guardan en el directorio `keys/`:

```bash
ls -la keys/
# Deberías ver:
# - account.key              (Clave de cuenta ACME)
# - domain.key               (Clave privada del dominio)
# - certificate.crt          (Certificado)
# - certificate-chain.crt    (Cadena de certificados)
```

## 3. Revocar Certificado

Una vez que tengas un certificado creado:

```bash
curl -X POST http://localhost:8080/api/certificates/revoke \
  -H "Content-Type: application/json" \
  -d '{"domain":"ejemplo.com"}'
```

**Respuesta esperada:**
```json
{
  "success": true,
  "message": "Certificate for domain ejemplo.com has been revoked successfully"
}
```

## 4. Manejo de Errores Comunes

### Error: "Domain parameter is required"

**Causa:** No se envió el parámetro domain o está vacío.

**Solución:**
```bash
# Correcto
curl -X POST http://localhost:8080/api/certificates/create \
  -H "Content-Type: application/json" \
  -d '{"domain":"ejemplo.com"}'

# Incorrecto (falta el domain)
curl -X POST http://localhost:8080/api/certificates/create \
  -H "Content-Type: application/json" \
  -d '{}'
```

### Error: "Certificate file not found"

**Causa:** Intentas revocar un certificado que no existe.

**Solución:** Primero crea el certificado con el endpoint `/create`, luego revócalo.

### Error: Challenge no accesible

**Causa:** Let's Encrypt no puede acceder a la URL del challenge.

**Solución:**
1. Verifica que tu dominio apunte a tu servidor
2. Verifica que el puerto 80 esté abierto
3. Verifica que el archivo sea accesible públicamente
4. Verifica que no haya redirecciones HTTP a HTTPS

```bash
# Test desde tu máquina
curl http://ejemplo.com/.well-known/acme-challenge/TOKEN

# Test desde un servidor externo
curl -I http://ejemplo.com/.well-known/acme-challenge/TOKEN
```

## 5. Logs y Debugging

Para ver los logs detallados, la aplicación está configurada con nivel DEBUG para:
- `com.acme` (código de la aplicación)
- `org.shredzone.acme4j` (librería ACME)

Los logs aparecerán en la consola donde ejecutaste `sbt run`.

## 6. Usando el Script de Prueba

Se incluye un script de prueba:

```bash
./test-api.sh
```

Este script muestra ejemplos de cómo usar todos los endpoints.

## 7. Notas Importantes

1. **Staging vs Production**: Esta aplicación usa el entorno de **staging** de Let's Encrypt
   - Los certificados generados NO son válidos para uso en producción
   - No hay límites estrictos de rate limiting
   - Ideal para pruebas y desarrollo

2. **Seguridad de las Claves**:
   - Las claves en `keys/` son MUY SENSIBLES
   - Nunca las subas a control de versiones
   - Protégelas con permisos adecuados: `chmod 600 keys/*`

3. **Rate Limiting en Producción**:
   - Si cambias a producción, ten en cuenta los límites de Let's Encrypt
   - Máximo 50 certificados por dominio registrado por semana
   - Ver: https://letsencrypt.org/docs/rate-limits/

4. **Dominio debe ser accesible**:
   - El dominio debe resolver a una IP pública
   - El servidor HTTP debe ser accesible desde internet
   - No funcionará con localhost o IPs privadas
