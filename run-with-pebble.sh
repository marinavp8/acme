#!/bin/bash

# Script para ejecutar la aplicación con Pebble ACME server
# Este script:
# 1. Verifica que Pebble esté corriendo
# 2. Ejecuta la aplicación configurada para usar Pebble
# 3. Deshabilita verificación SSL (necesario para certificados auto-firmados de Pebble)

set -e

echo "=== MVP ACME with Pebble ==="
echo ""

# Verificar que Pebble esté corriendo
echo "Verificando Pebble..."
if ! curl -k -s https://localhost:14000/dir > /dev/null 2>&1; then
    echo "❌ Error: Pebble no está corriendo en el puerto 14000"
    echo ""
    echo "Inicia Pebble con:"
    echo "  docker run --rm -d \\"
    echo "    --name pebble-acme \\"
    echo "    -e PEBBLE_VA_NOSLEEP=1 \\"
    echo "    -e PEBBLE_VA_ALWAYS_VALID=1 \\"
    echo "    -p 14000:14000 \\"
    echo "    -p 15000:15000 \\"
    echo "    letsencrypt/pebble:latest \\"
    echo "    pebble -config /test/config/pebble-config.json -strict false"
    exit 1
fi

echo "✓ Pebble está corriendo en https://localhost:14000"
echo ""

# Crear directorio temporal para claves de prueba
TEST_KEYS_DIR="keys-pebble-test"
mkdir -p "$TEST_KEYS_DIR"
echo "✓ Directorio de claves: $TEST_KEYS_DIR"
echo ""

echo "Iniciando aplicación con configuración de Pebble..."
echo "  - ACME URL: https://localhost:14000/dir"
echo "  - Auto-validación: HABILITADA (PEBBLE_VA_ALWAYS_VALID=1)"
echo "  - SSL Verification: DESHABILITADA (certificados auto-firmados)"
echo ""
echo "La aplicación estará disponible en http://localhost:8080"
echo ""
echo "Para probar:"
echo "  curl -X POST http://localhost:8080/api/certificates/create \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"domain\":\"myapp-test.com\"}'"
echo ""
echo "=========================================="
echo ""

# Ejecutar con SBT usando el perfil 'pebble'
sbt "run -Dspring.profiles.active=pebble"

