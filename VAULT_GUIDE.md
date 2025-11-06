# Guía: Usar HashiCorp Vault para Almacenar Certificados

Esta guía explica cómo configurar y usar HashiCorp Vault como backend de almacenamiento para certificados ACME.

## 🎯 ¿Por qué Vault?

- ✅ **Seguridad**: Cifrado en reposo y en tránsito
- ✅ **Auditoría**: Log completo de todos los accesos
- ✅ **Control de acceso**: Políticas granulares
- ✅ **Versionado**: Historial de cambios en secretos
- ✅ **Alta disponibilidad**: Clúster para producción
- ✅ **Rotación automática**: Renovación de secretos

## 🚀 Inicio Rápido

### 1. Iniciar Vault y Pebble con Docker Compose

```bash
# Levantar servicios
docker-compose -f docker-compose-vault.yml up -d

# Verificar que Vault esté corriendo
curl http://localhost:8200/v1/sys/health
```

### 2. Configurar Vault (primera vez)

```bash
# Habilitar secrets engine KV v2 (en modo dev ya está habilitado)
docker exec acme-vault vault secrets enable -version=2 -path=secret kv

# Verificar
docker exec acme-vault vault secrets list
```

### 3. Ejecutar la aplicación con Vault

```bash
# Establecer token de Vault
export VAULT_TOKEN=root

# Ejecutar con perfil vault
sbt -Dspring.profiles.active=vault run
```

## 📋 Configuración

### application-vault.yml

```yaml
acme:
  storage:
    type: vault  # filesystem o vault
    vault:
      address: http://localhost:8200
      token: ${VAULT_TOKEN:root}
      path: secret/data/acme
```

### Variables de entorno

```bash
# Token de Vault (REQUERIDO)
export VAULT_TOKEN=your-vault-token

# O token de AppRole
export VAULT_ROLE_ID=your-role-id
export VAULT_SECRET_ID=your-secret-id
```

## 🧪 Pruebas

### Crear un certificado (se guarda en Vault)

```bash
curl -X POST http://localhost:8080/api/certificates/create \
  -H "Content-Type: application/json" \
  -d '{"domain":"myapp.com"}'
```

### Ver certificado en Vault directamente

```bash
# Ver el certificado almacenado
docker exec acme-vault vault kv get secret/acme/myapp.com

# Ver metadata
docker exec acme-vault vault kv metadata get secret/acme/myapp.com
```

### Listar todos los certificados

```bash
# Desde la API
curl http://localhost:8080/api/certificates/list

# Desde Vault CLI
docker exec acme-vault vault kv list secret/acme
```

## 🗂️ Estructura en Vault

Los certificados se almacenan en:

```
secret/
└── acme/
    ├── myapp.com/
    │   ├── certificate      (Certificado PEM)
    │   ├── private_key      (Clave privada PEM)
    │   ├── chain            (Cadena completa)
    │   ├── domain           (Nombre del dominio)
    │   └── created_at       (Timestamp)
    ├── api.example.com/
    └── ...
```

## 🔒 Seguridad para Producción

### 1. Usar AppRole en lugar de token root

```bash
# Habilitar AppRole
vault auth enable approle

# Crear política para ACME
vault policy write acme-policy - <<EOF
path "secret/data/acme/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "secret/metadata/acme/*" {
  capabilities = ["list", "read", "delete"]
}
EOF

# Crear AppRole
vault write auth/approle/role/acme-app \
  token_policies="acme-policy" \
  token_ttl=1h \
  token_max_ttl=4h

# Obtener credenciales
vault read auth/approle/role/acme-app/role-id
vault write -f auth/approle/role/acme-app/secret-id
```

### 2. Habilitar TLS en Vault

```yaml
# application-vault.yml
acme:
  storage:
    vault:
      address: https://vault.production.com:8200
      token: ${VAULT_TOKEN}
      # TLS settings
      ssl-verify: true
      trust-store-file: /path/to/truststore.jks
```

### 3. Usar Vault Agent para auto-renovación

```hcl
# vault-agent.hcl
auto_auth {
  method "approle" {
    config = {
      role_id_file_path = "/etc/vault/role-id"
      secret_id_file_path = "/etc/vault/secret-id"
    }
  }
  
  sink "file" {
    config = {
      path = "/var/run/vault-token"
    }
  }
}
```

## 📊 Auditoría

Ver logs de acceso a certificados:

```bash
# Ver audit logs
docker exec acme-vault vault audit list

# Ver historial de versiones de un certificado
docker exec acme-vault vault kv metadata get secret/acme/myapp.com
```

## 🔄 Migración

### Migrar certificados existentes del filesystem a Vault

```bash
# Script de migración (crear como migrate-to-vault.sh)
#!/bin/bash

for cert in keys/*.crt; do
  domain=$(basename "$cert" .crt)
  [ "$domain" = "*-chain" ] && continue
  
  echo "Migrando $domain..."
  
  vault kv put secret/acme/$domain \
    certificate=@keys/${domain}.crt \
    private_key=@keys/${domain}.key \
    chain=@keys/${domain}-chain.crt \
    domain=$domain \
    created_at=$(date +%s)
done
```

## 🔧 Troubleshooting

### Error: "connection refused"

```bash
# Verificar que Vault esté corriendo
docker ps | grep vault

# Ver logs de Vault
docker logs acme-vault
```

### Error: "permission denied"

```bash
# Verificar token
echo $VAULT_TOKEN

# Verificar políticas
docker exec acme-vault vault token lookup
```

### Error: "path not found"

```bash
# Verificar que KV v2 esté habilitado
docker exec acme-vault vault secrets list

# El path debe ser secret/data/acme, no secret/acme
```

## 📚 Referencias

- [HashiCorp Vault Docs](https://www.vaultproject.io/docs)
- [KV Secrets Engine](https://www.vaultproject.io/docs/secrets/kv)
- [AppRole Auth Method](https://www.vaultproject.io/docs/auth/approle)
- [Vault Production Hardening](https://learn.hashicorp.com/tutorials/vault/production-hardening)

## 🎓 Ejemplos Avanzados

### Rotación automática de certificados

```scala
// Scheduled task para renovar certificados cerca de expirar
@Scheduled(cron = "0 0 2 * * *")  // Cada día a las 2 AM
def autoRenewCertificates(): Unit = {
  val certificates = acmeService.listCertificates()
  certificates.foreach { cert =>
    if (isDueForRenewal(cert)) {
      acmeService.renewCertificate(cert.domain)
    }
  }
}
```

### Notificaciones de eventos

```scala
// Webhook cuando se crea/renueva un certificado
acmeService.onCreate { cert =>
  slack.notify(s"✅ Certificado creado: ${cert.domain}")
  vault.audit.log("certificate_created", cert.domain)
}
```

