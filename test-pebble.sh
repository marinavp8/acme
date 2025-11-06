#!/bin/bash

# Script de prueba rápida con Pebble
# Asume que Pebble y la aplicación ya están corriendo

set -e

echo "=== Prueba de MVP ACME con Pebble ==="
echo ""

# Verificar que la aplicación esté corriendo
echo "1. Verificando que la aplicación esté corriendo..."
if ! curl -s http://localhost:8080/api/certificates/health > /dev/null 2>&1; then
    echo "❌ Error: La aplicación no está corriendo en http://localhost:8080"
    echo "Ejecuta: ./run-with-pebble.sh"
    exit 1
fi
echo "✓ Aplicación corriendo"
echo ""

# Health check
echo "2. Health check..."
HEALTH=$(curl -s http://localhost:8080/api/certificates/health | jq -r '.message' 2>/dev/null || echo "error")
if [ "$HEALTH" = "ACME service is running" ]; then
    echo "✓ Health check exitoso"
else
    echo "⚠️  Health check: $HEALTH"
fi
echo ""

# Crear certificado
echo "3. Creando certificado para 'myapp-test.com'..."
echo ""
RESPONSE=$(curl -s -X POST http://localhost:8080/api/certificates/create \
  -H 'Content-Type: application/json' \
  -d '{"domain":"myapp-test.com"}')

echo "Respuesta:"
echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
echo ""

# Verificar si fue exitoso
SUCCESS=$(echo "$RESPONSE" | jq -r '.success' 2>/dev/null || echo "false")
if [ "$SUCCESS" = "true" ]; then
    echo "✅ Certificado creado exitosamente!"
    echo ""
    echo "Archivos generados:"
    ls -lh keys-pebble-test/
else
    echo "❌ Error al crear certificado"
    ERROR=$(echo "$RESPONSE" | jq -r '.error' 2>/dev/null || echo "Unknown error")
    echo "Error: $ERROR"
fi

echo ""
echo "=========================================="

