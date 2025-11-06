#!/bin/bash

# Script helper para usar Vault CLI fácilmente
# Uso: ./vault-cli.sh <comando>
# Ejemplo: ./vault-cli.sh "kv list secret/acme"

VAULT_CMD="docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=root acme-vault vault"

if [ -z "$1" ]; then
    echo "Uso: $0 <comando>"
    echo ""
    echo "Ejemplos:"
    echo "  $0 status"
    echo "  $0 'kv list secret/acme'"
    echo "  $0 'kv get secret/acme/myapp.com'"
    echo "  $0 'kv put secret/data/acme/test cert=value'"
    echo "  $0 'secrets list'"
    exit 1
fi

$VAULT_CMD $@

