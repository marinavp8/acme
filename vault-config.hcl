# Configuración de Vault para producción
# Usar con: docker-compose -f docker-compose-vault-prod.yml up

storage "file" {
  path = "/vault/file"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1  # En producción, habilitar TLS
}

api_addr = "http://0.0.0.0:8200"
cluster_addr = "https://0.0.0.0:8201"
ui = true

# Audit logging
# audit {
#   type = "file"
#   path = "/vault/logs/audit.log"
# }

