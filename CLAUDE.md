# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Scala application using Spring Boot that implements an ACME (Automatic Certificate Management Environment) client. It provides REST endpoints to create and revoke SSL/TLS certificates using Let's Encrypt's staging environment via the `org.shredzone.acme4j` library.

## Build & Run Commands

### Compile the project
```bash
sbt compile
```

### Run the application
```bash
sbt run
```

### Package the application
```bash
sbt stage
```

### Clean build artifacts
```bash
sbt clean
```

### Run tests
```bash
sbt test
```

### Run a specific test
```bash
# Unit tests only
sbt "testOnly com.acme.service.AcmeServiceUnitTest"

# Integration tests with Pebble (requires Docker)
sbt "testOnly com.acme.service.AcmeServiceIntegrationTest"
```

## Application Architecture

### Core Components

1. **AcmeApplication** (`com.acme.AcmeApplication`)
   - Spring Boot entry point
   - Located at: `src/main/scala/com/acme/AcmeApplication.scala`

2. **CertificateController** (`com.acme.controller.CertificateController`)
   - REST API controller exposing certificate operations
   - Endpoints:
     - `POST /api/certificates/create` - Create certificate for a domain
     - `POST /api/certificates/revoke` - Revoke certificate for a domain
     - `GET /api/certificates/health` - Health check
   - Located at: `src/main/scala/com/acme/controller/CertificateController.scala`

3. **AcmeService** (`com.acme.service.AcmeService`)
   - Core business logic for ACME protocol operations
   - Handles account creation/retrieval
   - Manages certificate ordering and challenge processing
   - Stores keys and certificates in `keys/` directory
   - Located at: `src/main/scala/com/acme/service/AcmeService.scala`

4. **JacksonConfig** (`com.acme.config.JacksonConfig`)
   - Configures Jackson ObjectMapper with Scala module
   - Required for proper JSON serialization/deserialization of Scala case classes
   - Located at: `src/main/scala/com/acme/config/JacksonConfig.scala`

### ACME Protocol Flow

The certificate creation process follows these steps:

1. **Account Setup**: Loads or creates an RSA 2048-bit key pair for the ACME account
2. **Account Registration**: Finds existing account or registers new one with Let's Encrypt staging
3. **Order Creation**: Creates an order for the specified domain
4. **Authorization**: Retrieves HTTP-01 challenge requirements
5. **Challenge Setup**: Application returns challenge token and authorization (user must set up HTTP endpoint)
6. **CSR Generation**: Creates Certificate Signing Request with domain key pair
7. **Certificate Issuance**: ACME server validates challenge and issues certificate
8. **Storage**: Saves certificate and chain to `keys/` directory

### Key Storage Structure

All cryptographic material is stored in the `keys/` directory:
- `account.key` - ACME account private key (RSA 2048-bit)
- `domain.key` - Domain private key (RSA 2048-bit)
- `certificate.crt` - Issued X.509 certificate
- `certificate-chain.crt` - Certificate with full chain

**CRITICAL**: These files are in `.gitignore` and must NEVER be committed to version control.

### ACME Server Configuration

- **Staging URL**: `acme://letsencrypt.org/staging`
- **Purpose**: Testing without rate limits, issues invalid certificates
- **Production URL**: `acme://letsencrypt.org` (not used in this codebase)

To switch to production, change `ACME_SERVER_URL` in `AcmeService.scala:16`

### Challenge Handling

Currently implements **HTTP-01 challenge**:
- Application throws exception with challenge details
- User must manually set up HTTP endpoint at: `http://{domain}/.well-known/acme-challenge/{token}`
- Endpoint must return the authorization string
- After setup, user calls the create endpoint again to continue

**Future Enhancement**: Implement automatic HTTP server for challenge response or DNS-01 challenge support.

### Certificate Revocation

The revocation process uses the acme4j API:
- Loads the X.509 certificate from the `keys/certificate.crt` file
- Uses `Certificate.revoke(session, domainKeyPair, x509Cert, RevocationReason.UNSPECIFIED)`
- Requires the domain key pair (not just the account key)
- This allows revocation even if the account key is lost, as long as the domain key pair is available

## Technology Stack

- **Scala**: 2.13.12
- **Spring Boot**: 3.2.0
- **ACME4J**: 3.2.1 (Shred Zone ACME client library)
- **Jackson Scala Module**: 2.16.0 (JSON processing)
- **Bouncy Castle**: 1.77 (Cryptographic operations)
- **SBT**: 1.9.7 (Build tool)

## API Request/Response Models

All models are Scala case classes in `CertificateController.scala:59-74`:

**Requests**:
- `CreateCertificateRequest(domain: String)`
- `RevokeCertificateRequest(domain: String)`

**Responses**:
- `SuccessResponse(success: Boolean, message: String)`
- `ErrorResponse(error: String)` (with `success = false`)
- `CertificateResponse(success: Boolean, message: String, data: Option[CertificateResult])`

**Data Models**:
- `CertificateResult(domain: String, certificatePath: String, certificateChainPath: String, status: String)`

## Configuration

Application configuration is in `src/main/resources/application.yml`:
- Server port: 8080
- Logging: DEBUG for `com.acme` and `org.shredzone.acme4j` packages

## Testing

When testing certificate creation:
1. Use a domain you control
2. Ensure the domain resolves to an accessible HTTP server
3. Set up the challenge response at the required path
4. Let's Encrypt staging has less strict rate limits than production

## Common Development Patterns

### Adding New Endpoints
1. Add method to `CertificateController`
2. Use `@PostMapping` or `@GetMapping` annotations
3. Return `ResponseEntity[ApiResponse]` for consistent responses
4. Handle `Try[T]` results from `AcmeService` with pattern matching

### Modifying ACME Flow
- All ACME logic is centralized in `AcmeService`
- Uses `scala.util.Try` for error handling
- Key operations use `org.shredzone.acme4j` API
- Refer to acme4j documentation: https://shredzone.org/maven/acme4j/

## Testing

### Unit Tests

The project includes unit tests for the ACME service located at `src/test/scala/com/acme/service/AcmeServiceUnitTest.scala`.

**Unit Tests (AcmeServiceUnitTest):**
- Service instantiation with default and custom configuration
- RSA key pair generation and loading
- Verification of 2048-bit RSA key generation
- Certificate revocation error handling (missing certificate file)
- Certificate revocation error handling (invalid certificate)
- Certificate creation error handling (unreachable ACME server)

**Integration Tests with Pebble (AcmeServiceIntegrationTest):**
- Pebble container startup and accessibility
- AcmeService connection to Pebble ACME server
- Account key creation
- Certificate creation attempt with auto-validated challenges
- Certificate revocation with proper error handling

**Running tests:**
```bash
sbt test
```

### Test Configuration

- **Test forking**: Enabled (`Test / fork := true`) to support Java 25 with Mockito
- **JVM options**: `-Dnet.bytebuddy.experimental=true` for compatibility
- **Test resources**: Located in `src/test/resources/`
  - `application-test.yml`: Test-specific application configuration
  - `logback-test.xml`: Test logging configuration

### Integration Testing with Pebble

The project includes **automated integration tests** using [Pebble](https://github.com/letsencrypt/pebble), Let's Encrypt's ACME test server, via Testcontainers.

**Running integration tests:**
```bash
sbt "testOnly com.acme.service.AcmeServiceIntegrationTest"
```

**Requirements:**
- Docker installed and running
- ~5-10 seconds for container startup (longer on arm64/Apple Silicon due to emulation)

**What the integration tests do:**
- Automatically start Pebble in a Docker container
- Disable SSL verification for Pebble's self-signed certificates (test only!)
- Test full ACME protocol flows with a real ACME server
- Clean up containers automatically after tests

**Pebble configuration:**
- `PEBBLE_VA_ALWAYS_VALID=1`: Auto-validates all challenges
- `PEBBLE_VA_NOSLEEP=1`: No delays between validation attempts
- Ports: 14000 (ACME API), 15000 (Management API)

See `TESTING.md` for manual Pebble testing workflows.

### Test Dependencies

- **ScalaTest** 3.2.17: Testing framework
- **Mockito** 4.11: Mocking library (via scalatestplus)
- **Testcontainers** 1.19.3: Docker container management for tests
- **Spring Boot Test**: Spring testing support

### Working with Spring Boot in Scala
- Use `@Autowired()()` with double parentheses for constructor injection
- Register Scala case classes with Jackson via `DefaultScalaModule`
- Use `Array[String]` for annotation array parameters (not Scala Seq)
- Main method must be in companion object with `args: _*` spread operator
