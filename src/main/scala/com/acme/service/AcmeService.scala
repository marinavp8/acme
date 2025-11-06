package com.acme.service

import com.acme.storage.CertificateStorage
import org.shredzone.acme4j._
import org.shredzone.acme4j.challenge.Http01Challenge
import org.shredzone.acme4j.util.{CSRBuilder, KeyPairUtils}
import org.slf4j.LoggerFactory

import java.io.{File, FileInputStream, FileReader, FileWriter, StringWriter, StringReader}
import java.security.KeyPair
import java.security.cert.{CertificateFactory, X509Certificate}
import java.util.Optional
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

// Note: @Service annotation removed - AcmeService is now created via @Bean in AcmeConfig
// This allows configuration to be injected from application.yml
class AcmeService(
  // Allow configuration of ACME server URL (for testing with Pebble)
  private val acmeServerUrl: String = "acme://letsencrypt.org/staging",
  // Allow configuration of key directory (for testing)
  private val keyDirPath: String = "keys",
  // Auto-validate challenges (for testing with Pebble's PEBBLE_VA_ALWAYS_VALID=1)
  private val autoValidateChallenges: Boolean = false,
  // Storage backend (filesystem or vault)
  private val storage: CertificateStorage
) {

  private val logger = LoggerFactory.getLogger(classOf[AcmeService])

  // ACME server URL
  private val ACME_SERVER_URL = acmeServerUrl

  // Directory to store account and certificate keys
  private val KEY_DIR = new File(keyDirPath)
  private val ACCOUNT_KEY_FILE = new File(KEY_DIR, "account.key")
  
  // Helper methods to get domain-specific file paths
  private def getDomainKeyFile(domain: String): File = new File(KEY_DIR, s"$domain.key")
  private def getCertFile(domain: String): File = new File(KEY_DIR, s"$domain.crt")
  private def getCertChainFile(domain: String): File = new File(KEY_DIR, s"$domain-chain.crt")

  // Create key directory if it doesn't exist
  if (!KEY_DIR.exists()) {
    KEY_DIR.mkdirs()
    logger.info(s"Created key directory: ${KEY_DIR.getAbsolutePath}")
  }

  // Constructor for Spring (default values) - requires storage
  def this(storage: CertificateStorage) = this("acme://letsencrypt.org/staging", "keys", false, storage)

  // Getters for configuration (for diagnostics)
  def getServerUrl: String = acmeServerUrl
  def getKeyDir: String = keyDirPath
  def getAutoValidate: Boolean = autoValidateChallenges

  /**
   * Creates a certificate for the given domain using ACME protocol
   */
  def createCertificate(domain: String): Try[CertificateResult] = Try {
    logger.info(s"Starting certificate creation for domain: $domain")
    
    // Get domain-specific file paths
    val domainKeyFile = getDomainKeyFile(domain)
    val certFile = getCertFile(domain)
    val certChainFile = getCertChainFile(domain)

    // Load or create account key pair
    val accountKeyPair = loadOrCreateKeyPair(ACCOUNT_KEY_FILE)
    logger.info("Account key pair loaded/created")

    // Create a session with the ACME server
    val session = new Session(ACME_SERVER_URL)
    logger.info("Session created with ACME server")

    // Get or create account
    val account = findOrRegisterAccount(session, accountKeyPair)
    logger.info(s"Account ready: ${account.getLocation}")

    // Create order for the domain
    val order = account.newOrder().domains(domain).create()
    logger.info(s"Order created with status: ${order.getStatus}")

    // Get authorizations
    val authorizations = order.getAuthorizations.asScala

    // Process each authorization (challenge)
    authorizations.foreach { auth =>
      logger.info(s"Processing authorization for: ${auth.getIdentifier.getDomain}")

      // Find HTTP-01 challenge
      // In acme4j v3+, findChallenge returns Optional[Challenge]
      val challengeOptional: Optional[_] = auth.findChallenge(Http01Challenge.TYPE)

      if (challengeOptional.isPresent) {
        val challenge = challengeOptional.get().asInstanceOf[Http01Challenge]
        logger.info(s"HTTP-01 Challenge found")
        logger.info(s"Challenge token: ${challenge.getToken}")
        logger.info(s"Challenge authorization: ${challenge.getAuthorization}")
        logger.info(s"You need to make available: http://${auth.getIdentifier.getDomain}/.well-known/acme-challenge/${challenge.getToken}")
        logger.info(s"With content: ${challenge.getAuthorization}")

        if (autoValidateChallenges) {
          // Auto-validation mode (for testing with Pebble)
          logger.info("Auto-validation enabled - triggering challenge")
          challenge.trigger()
          logger.info("Challenge triggered, waiting for validation...")

          // Wait for challenge to be validated
          var attempts = 10
          var currentAuth = auth
          while (currentAuth.getStatus != Status.VALID && attempts > 0) {
            Thread.sleep(1000)
            currentAuth.update()
            logger.info(s"Authorization status: ${currentAuth.getStatus}, attempts left: $attempts")
            attempts -= 1
          }

          if (currentAuth.getStatus != Status.VALID) {
            throw new RuntimeException(s"Authorization failed with status: ${currentAuth.getStatus}")
          }
          logger.info("Challenge validated successfully")
        } else {
          // Manual mode - throw exception with instructions
          throw new RuntimeException(
            s"Please set up HTTP challenge:\n" +
            s"URL: http://${auth.getIdentifier.getDomain}/.well-known/acme-challenge/${challenge.getToken}\n" +
            s"Content: ${challenge.getAuthorization}\n" +
            s"Then call the endpoint again to continue."
          )
        }
      } else {
        throw new RuntimeException("HTTP-01 challenge not available")
      }
    }

    // Load or create domain key pair
    val domainKeyPair = loadOrCreateKeyPair(domainKeyFile)
    logger.info("Domain key pair loaded/created")

    // Create CSR
    val csrBuilder = new CSRBuilder()
    csrBuilder.addDomain(domain)
    csrBuilder.sign(domainKeyPair)
    val csr = csrBuilder.getEncoded
    logger.info("CSR created and signed")

    // Order certificate
    order.execute(csr)
    logger.info("Certificate order executed")

    // Wait for order to complete
    var attempts = 10
    var currentOrder = order
    while (currentOrder.getStatus != Status.VALID && attempts > 0) {
      Thread.sleep(3000)
      currentOrder.update()
      logger.info(s"Order status: ${currentOrder.getStatus}, attempts left: $attempts")
      attempts -= 1
    }

    if (currentOrder.getStatus != Status.VALID) {
      throw new RuntimeException(s"Order failed with status: ${currentOrder.getStatus}")
    }

    // Download certificate
    val certificate = currentOrder.getCertificate
    logger.info("Certificate downloaded")

    // Convert certificates to PEM strings
    val certPEM = new StringWriter()
    val chainPEM = new StringWriter()
    try {
      certificate.writeCertificate(certPEM)
      certificate.writeCertificate(chainPEM)
    } finally {
      certPEM.close()
      chainPEM.close()
    }
    
    // Convert private key to PEM string
    val keyPEM = new StringWriter()
    KeyPairUtils.writeKeyPair(domainKeyPair, keyPEM)
    keyPEM.close()

    // Save using storage backend (filesystem or vault)
    storage.saveCertificate(domain, certPEM.toString, keyPEM.toString, chainPEM.toString) match {
      case Success(_) =>
        logger.info(s"Certificate saved for domain: $domain")
      case Failure(e) =>
        logger.error(s"Failed to save certificate: ${e.getMessage}")
        throw e
    }

    logger.info(s"Certificate created and saved for domain: $domain")

    CertificateResult(
      domain = domain,
      certificatePath = certFile.getAbsolutePath,
      certificateChainPath = certChainFile.getAbsolutePath,
      status = "VALID"
    )
  }

  /**
   * Lists all available certificates
   */
  def listCertificates(): Try[List[CertificateSummary]] = Try {
    logger.info("Listing all certificates")
    
    storage.listCertificates() match {
      case Success(domains) =>
        logger.info(s"Found ${domains.size} certificate(s)")
        
        domains.map { domain =>
          try {
            storage.getCertificate(domain) match {
              case Success(stored) =>
                // Parse certificate to get details
                val certFactory = CertificateFactory.getInstance("X.509")
                val certStream = new java.io.StringReader(stored.certificate)
                val pemReader = certStream
                val x509Cert = certFactory.generateCertificate(
                  new java.io.ByteArrayInputStream(stored.certificate.getBytes)
                ).asInstanceOf[X509Certificate]
                
                CertificateSummary(
                  domain = domain,
                  notBefore = x509Cert.getNotBefore.toString,
                  notAfter = x509Cert.getNotAfter.toString,
                  serialNumber = x509Cert.getSerialNumber.toString(16),
                  certificatePath = stored.metadata.getOrElse("certPath", s"storage:$domain")
                )
              case Failure(e) =>
                logger.warn(s"Failed to read certificate for $domain: ${e.getMessage}")
                CertificateSummary(
                  domain = domain,
                  notBefore = "unknown",
                  notAfter = "unknown",
                  serialNumber = "unknown",
                  certificatePath = s"storage:$domain"
                )
            }
          } catch {
            case e: Exception =>
              logger.warn(s"Failed to parse certificate for $domain: ${e.getMessage}")
              CertificateSummary(
                domain = domain,
                notBefore = "unknown",
                notAfter = "unknown",
                serialNumber = "unknown",
                certificatePath = s"storage:$domain"
              )
          }
        }.sortBy(_.domain)
        
      case Failure(e) =>
        logger.error(s"Failed to list certificates: ${e.getMessage}")
        List.empty
    }
  }

  /**
   * Gets certificate information
   */
  def getCertificateInfo(domain: String): Try[CertificateInfo] = Try {
    logger.info(s"Getting certificate info for domain: $domain")
    
    storage.getCertificate(domain) match {
      case Success(stored) =>
        // Parse certificate
        val certFactory = CertificateFactory.getInstance("X.509")
        val x509Cert = certFactory.generateCertificate(
          new java.io.ByteArrayInputStream(stored.certificate.getBytes)
        ).asInstanceOf[X509Certificate]

        logger.info(s"Certificate info retrieved for domain: $domain")

        CertificateInfo(
          domain = domain,
          subject = x509Cert.getSubjectX500Principal.getName,
          issuer = x509Cert.getIssuerX500Principal.getName,
          serialNumber = x509Cert.getSerialNumber.toString(16),
          notBefore = x509Cert.getNotBefore.toString,
          notAfter = x509Cert.getNotAfter.toString,
          signatureAlgorithm = x509Cert.getSigAlgName,
          version = x509Cert.getVersion,
          certificatePath = stored.metadata.getOrElse("certPath", s"storage:$domain")
        )
      case Failure(e) =>
        throw new RuntimeException(s"Certificate not found for domain: $domain - ${e.getMessage}")
    }
  }

  /**
   * Revokes a certificate
   */
  def revokeCertificate(domain: String): Try[String] = Try {
    logger.info(s"Starting certificate revocation for domain: $domain")
    
    storage.getCertificate(domain) match {
      case Success(stored) =>
        // Load account key pair
        val accountKeyPair = loadOrCreateKeyPair(ACCOUNT_KEY_FILE)
        logger.info("Account key pair loaded")

        // Create a session with the ACME server
        val session = new Session(ACME_SERVER_URL)

        // Get account
        val account = findOrRegisterAccount(session, accountKeyPair)
        logger.info(s"Account loaded: ${account.getLocation}")

        // Parse X.509 certificate from PEM string
        val certFactory = CertificateFactory.getInstance("X.509")
        val x509Cert = certFactory.generateCertificate(
          new java.io.ByteArrayInputStream(stored.certificate.getBytes)
        ).asInstanceOf[X509Certificate]

        // Parse domain key pair from PEM string
        val domainKeyPair = KeyPairUtils.readKeyPair(new StringReader(stored.privateKey))

        // Revoke certificate using the domain key pair
        Certificate.revoke(session, domainKeyPair, x509Cert, RevocationReason.UNSPECIFIED)
        logger.info(s"Certificate revoked for domain: $domain")

        s"Certificate for domain $domain has been revoked successfully"
        
      case Failure(e) =>
        throw new RuntimeException(s"Certificate not found for domain: $domain - ${e.getMessage}")
    }
  }

  /**
   * Loads or creates a key pair
   * Made package-private for testing
   */
  def loadOrCreateKeyPair(file: File): KeyPair = {
    if (file.exists()) {
      val reader = new FileReader(file)
      try {
        KeyPairUtils.readKeyPair(reader)
      } finally {
        reader.close()
      }
    } else {
      val keyPair = KeyPairUtils.createKeyPair(2048)
      val writer = new FileWriter(file)
      try {
        KeyPairUtils.writeKeyPair(keyPair, writer)
      } finally {
        writer.close()
      }
      keyPair
    }
  }

  /**
   * Finds existing account or registers a new one
   */
  private def findOrRegisterAccount(session: Session, accountKeyPair: KeyPair): Account = {
    try {
      // Try to find existing account
      val account = new AccountBuilder()
        .agreeToTermsOfService()
        .useKeyPair(accountKeyPair)
        .create(session)

      logger.info("Existing account found")
      account
    } catch {
      case _: Exception =>
        // Register new account
        logger.info("Registering new account")
        new AccountBuilder()
          .agreeToTermsOfService()
          .useKeyPair(accountKeyPair)
          .create(session)
    }
  }
}

case class CertificateResult(
  domain: String,
  certificatePath: String,
  certificateChainPath: String,
  status: String
)

case class CertificateInfo(
  domain: String,
  subject: String,
  issuer: String,
  serialNumber: String,
  notBefore: String,
  notAfter: String,
  signatureAlgorithm: String,
  version: Int,
  certificatePath: String
)

case class CertificateSummary(
  domain: String,
  notBefore: String,
  notAfter: String,
  serialNumber: String,
  certificatePath: String
)
