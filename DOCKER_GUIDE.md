# Guía de Docker Compose

Esta guía explica los diferentes archivos de Docker Compose disponibles y cómo usarlos.

## 📦 Archivos disponibles

### 1. `docker-compose-vault.yml` (Desarrollo)

**Uso:** Desarrollo y pruebas rápidas con Vault en modo dev

**Características:**
- ✅ Vault en modo desarrollo (datos en memoria)
- ✅ Token root predefinido: `root`
- ✅ Pebble para testing ACME
- ✅ Volúmenes para logs
- ✅ Auto-unseal (no requiere inicialización)

**Iniciar:**
```bash
docker-compose -f docker-compose-vault.yml up -d
```

**Ventajas:**
- Inicio inmediato, sin configuración
- Token root `root` ya configurado
- Ideal para desarrollo local

**Desventajas:**
- Datos se pierden al reiniciar (modo dev)
- No es seguro para producción

---

### 2. `docker-compose-vault-prod.yml` (Producción)

**Uso:** Entorno de producción con persistencia real

**Características:**
- ✅ Vault con storage persistente (file backend)
- ✅ Datos persisten entre reinicios
- ✅ Configuración desde `vault-config.hcl`
- ✅ Healthchecks y restart automático
- ✅ Requiere inicialización manual (más seguro)

**Iniciar:**
```bash
# 1. Levantar servicios
docker-compose -f docker-compose-vault-prod.yml up -d

# 2. Esperar que Vault inicie
sleep 5

# 3. Inicializar Vault (PRIMERA VEZ SOLAMENTE)
docker exec acme-vault-prod vault operator init

# Guardar las unseal keys y root token que se muestran!
```

**Inicialización (primera vez):**
```bash
# Vault mostrará algo como:
Unseal Key 1: ABC123...
Unseal Key 2: DEF456...
Unseal Key 3: GHI789...
Unseal Key 4: JKL012...
Unseal Key 5: MNO345...

Initial Root Token: s.XYZ789...

# ⚠️ GUARDAR ESTAS CLAVES EN LUGAR SEGURO!
```

**Unseal Vault (después de cada reinicio):**
```bash
# Necesitas 3 de las 5 unseal keys
docker exec acme-vault-prod vault operator unseal <key1>
docker exec acme-vault-prod vault operator unseal <key2>
docker exec acme-vault-prod vault operator unseal <key3>

# Verificar estado
docker exec acme-vault-prod vault status
```

**Ventajas:**
- Datos persisten entre reinicios
- Más seguro (requiere unseal)
- Apto para producción

**Desventajas:**
- Requiere inicialización manual
- Necesitas guardar unseal keys de forma segura

---

## 🚀 Comandos útiles

### Gestión de servicios

```bash
# Iniciar servicios
docker-compose -f docker-compose-vault.yml up -d

# Ver logs
docker-compose -f docker-compose-vault.yml logs -f

# Parar servicios
docker-compose -f docker-compose-vault.yml down

# Parar y eliminar volúmenes (⚠️ borra todos los datos)
docker-compose -f docker-compose-vault.yml down -v
```

### Verificar estado

```bash
# Estado de Vault
curl http://localhost:8200/v1/sys/health

# Estado de Pebble
curl -k https://localhost:14000/dir

# Logs de Vault
docker logs acme-vault -f

# Logs de Pebble
docker logs acme-pebble -f
```

### Acceder a los servicios

```bash
# Shell en Vault
docker exec -it acme-vault sh

# Shell en Pebble
docker exec -it acme-pebble sh

# Ejecutar comandos de Vault
docker exec acme-vault vault status
docker exec acme-vault vault kv list secret/acme
```

## 📂 Volúmenes

### Ubicación de los volúmenes

```bash
# Listar volúmenes
docker volume ls | grep acme

# Inspeccionar un volumen
docker volume inspect mvp-acme_vault-data

# Ver ubicación en el filesystem
docker volume inspect mvp-acme_vault-data | jq -r '.[0].Mountpoint'
```

### Backup de volúmenes

```bash
# Backup de datos de Vault
docker run --rm \
  -v mvp-acme_vault-data:/data \
  -v $(pwd)/backups:/backup \
  alpine tar czf /backup/vault-backup-$(date +%Y%m%d).tar.gz /data

# Restaurar backup
docker run --rm \
  -v mvp-acme_vault-data:/data \
  -v $(pwd)/backups:/backup \
  alpine tar xzf /backup/vault-backup-20231104.tar.gz -C /
```

### Limpiar volúmenes

```bash
# ⚠️ CUIDADO: Esto borra todos los datos!

# Parar servicios
docker-compose -f docker-compose-vault.yml down

# Eliminar volúmenes
docker volume rm mvp-acme_vault-data
docker volume rm mvp-acme_vault-logs
docker volume rm mvp-acme_pebble-logs

# O eliminar todo de una vez
docker-compose -f docker-compose-vault.yml down -v
```

## 🔧 Troubleshooting

### Vault no responde

```bash
# Ver logs
docker logs acme-vault

# Verificar estado
docker exec acme-vault vault status

# Si está "sealed", hacer unseal
docker exec acme-vault vault operator unseal <key>
```

### Pebble no responde

```bash
# Ver logs
docker logs acme-pebble

# Reiniciar servicio
docker restart acme-pebble

# Verificar que esté escuchando
docker exec acme-pebble netstat -tuln | grep 14000
```

### Puerto ya en uso

```bash
# Ver qué está usando el puerto 8200
lsof -i :8200

# Cambiar puerto en docker-compose.yml
ports:
  - "8201:8200"  # Host:Container
```

### Volúmenes llenos

```bash
# Ver espacio usado por volúmenes
docker system df -v

# Limpiar volúmenes no usados
docker volume prune
```

## 🔐 Seguridad

### Modo desarrollo (docker-compose-vault.yml)
- ✅ Token root predefinido: `root`
- ⚠️ Datos en memoria (se pierden al reiniciar)
- ⚠️ Solo para desarrollo local

### Modo producción (docker-compose-vault-prod.yml)
- ✅ Datos persisten en disco
- ✅ Requiere unseal keys
- ✅ Token root generado aleatoriamente
- ✅ Audit logs habilitados

### Recomendaciones de seguridad

1. **Nunca usar modo dev en producción**
2. **Guardar unseal keys en lugar seguro** (KMS, password manager)
3. **Rotar el root token** después de la inicialización
4. **Habilitar TLS** en producción
5. **Usar AppRole** en lugar de root token para la aplicación
6. **Habilitar audit logging**
7. **Backup regular** de los volúmenes de Vault

## 📊 Monitoreo

### Healthchecks

Los servicios tienen healthchecks configurados:

```bash
# Ver estado de salud
docker ps --format "table {{.Names}}\t{{.Status}}"
```

### Métricas

```bash
# Métricas de Vault (requiere autenticación)
curl -H "X-Vault-Token: root" \
  http://localhost:8200/v1/sys/metrics

# Dashboard web de Vault
open http://localhost:8200/ui
```

## 📚 Referencias

- [Vault Docker Hub](https://hub.docker.com/_/vault)
- [Pebble GitHub](https://github.com/letsencrypt/pebble)
- [Docker Volumes](https://docs.docker.com/storage/volumes/)
- [Vault Production Deployment](https://learn.hashicorp.com/tutorials/vault/deployment-guide)

