#!/bin/bash

# Script de prueba para la API ACME

echo "Testing ACME API..."
echo ""

# Test Health endpoint
echo "1. Testing Health endpoint..."
curl -s http://localhost:8080/api/certificates/health | jq '.' || echo "Application might not be running"
echo ""
echo ""

# Test Create Certificate (replace with your domain)
echo "2. Testing Create Certificate endpoint..."
echo "   Usage: curl -X POST http://localhost:8080/api/certificates/create \\"
echo "          -H \"Content-Type: application/json\" \\"
echo "          -d '{\"domain\":\"your-domain.com\"}'"
echo ""

# Test Revoke Certificate (replace with your domain)
echo "3. Testing Revoke Certificate endpoint..."
echo "   Usage: curl -X POST http://localhost:8080/api/certificates/revoke \\"
echo "          -H \"Content-Type: application/json\" \\"
echo "          -d '{\"domain\":\"your-domain.com\"}'"
echo ""

echo "Examples:"
echo "--------"
echo ""
echo "Create certificate for domain example.com:"
echo "curl -X POST http://localhost:8080/api/certificates/create -H 'Content-Type: application/json' -d '{\"domain\":\"example.com\"}'"
echo ""
echo "Revoke certificate for domain example.com:"
echo "curl -X POST http://localhost:8080/api/certificates/revoke -H 'Content-Type: application/json' -d '{\"domain\":\"example.com\"}'"
