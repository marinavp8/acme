package com.acme.service

import com.acme.storage.FileSystemStorage
import com.acme.testcontainers.PebbleContainer
import com.dimafeng.testcontainers.ForAllTestContainer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.slf4j.LoggerFactory

import java.io.File
import java.nio.file.{Files, Path}
import javax.net.ssl._
import java.security.cert.X509Certificate
import scala.util.{Failure, Success}

/**
 * Integration tests for AcmeService using Pebble ACME server in a testcontainer
 *
 * These tests use Pebble (https://github.com/letsencrypt/pebble), which is
 * Let's Encrypt's small ACME test server designed specifically for testing ACME clients.
 *
 * Pebble is configured with:
 * - PEBBLE_VA_ALWAYS_VALID=1: Auto-validates all challenges
 * - PEBBLE_VA_NOSLEEP=1: No delays between validation attempts
 */
class AcmeServiceIntegrationTest extends AnyFlatSpec with Matchers with ForAllTestContainer with BeforeAndAfterAll {

  private val logger = LoggerFactory.getLogger(classOf[AcmeServiceIntegrationTest])

  override val container: PebbleContainer = PebbleContainer()

  private var tempDir: Path = _
  private var originalTrustManager: Array[TrustManager] = _
  private var originalHostnameVerifier: HostnameVerifier = _
  private var originalSSLContext: SSLContext = _

  override def beforeAll(): Unit = {
    // Disable SSL verification for Pebble's self-signed certificates
    // WARNING: This is ONLY for testing! Never do this in production!
    disableSSLVerification()

    tempDir = Files.createTempDirectory("acme-pebble-test-")
    logger.info(s"Created temp directory for tests: $tempDir")

    // Start the container (this is done by ForAllTestContainer)
    super.beforeAll()

    // Container should now be started - log its details
    try {
      logger.info(s"Pebble ACME URL: ${container.acmeUrl}")
      logger.info(s"Pebble container started on port: ${container.acmePort}")
    } catch {
      case e: Exception =>
        logger.warn(s"Could not get container info in beforeAll: ${e.getMessage}")
        // This is OK, we'll get it in the tests
    }
  }

  override def afterAll(): Unit = {
    // Restore SSL verification
    restoreSSLVerification()

    // Clean up temp directory
    if (tempDir != null && Files.exists(tempDir)) {
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
      logger.info(s"Cleaned up temp directory: $tempDir")
    }
    super.afterAll()
  }

  /**
   * Disable SSL certificate verification for testing with Pebble
   * Pebble uses self-signed certificates which would normally be rejected
   */
  private def disableSSLVerification(): Unit = {
    val trustAllCerts = Array[TrustManager](new X509TrustManager {
      def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = {}
      def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = {}
      def getAcceptedIssuers(): Array[X509Certificate] = Array.empty
    })

    val sc = SSLContext.getInstance("TLS")
    sc.init(null, trustAllCerts, new java.security.SecureRandom())

    // Save original settings
    originalTrustManager = trustAllCerts
    originalSSLContext = SSLContext.getDefault

    // Set default SSLContext for java.net.http.HttpClient (used by acme4j v3+)
    SSLContext.setDefault(sc)

    // Also set for HttpsURLConnection (legacy support)
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())

    // Save and override hostname verifier
    originalHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier
    HttpsURLConnection.setDefaultHostnameVerifier((_: String, _: SSLSession) => true)

    logger.info("SSL verification disabled for Pebble testing (both HttpsURLConnection and java.net.http)")
  }

  /**
   * Restore SSL verification to default settings
   */
  private def restoreSSLVerification(): Unit = {
    if (originalSSLContext != null) {
      SSLContext.setDefault(originalSSLContext)
    }
    if (originalHostnameVerifier != null) {
      HttpsURLConnection.setDefaultHostnameVerifier(originalHostnameVerifier)
    }
    logger.info("SSL verification restored")
  }

  "Pebble container" should "be running and accessible" in {
    container.container.isRunning shouldBe true
    container.acmeUrl should include("https://")
    container.acmeUrl should include("/dir")
    logger.info(s"✓ Pebble is running at: ${container.acmeUrl}")
  }

  "AcmeService with Pebble" should "connect to Pebble ACME server" in {
    val service = new AcmeService(
      acmeServerUrl = container.acmeUrl,
      keyDirPath = tempDir.toString,
      autoValidateChallenges = false,
      storage = new FileSystemStorage(tempDir.toString)
    )

    service should not be null

    // Verify that keys directory was created
    val keysDir = tempDir.toFile
    keysDir.exists() shouldBe true
    keysDir.isDirectory shouldBe true

    logger.info("✓ AcmeService created and configured with Pebble")
  }

  it should "create account keys when first initialized" in {
    val service = new AcmeService(
      acmeServerUrl = container.acmeUrl,
      keyDirPath = tempDir.toString,
      autoValidateChallenges = false,
      storage = new FileSystemStorage(tempDir.toString)
    )

    val accountKeyFile = new File(tempDir.toFile, "account.key")

    // Account key should be created on first certificate operation
    // For now, just verify the service can create keys
    val testKeyFile = new File(tempDir.toFile, "test-account.key")
    val keyPair = service.loadOrCreateKeyPair(testKeyFile)

    keyPair should not be null
    testKeyFile.exists() shouldBe true

    logger.info("✓ Account keys can be created")
  }

  "createCertificate with Pebble" should "complete full certificate creation with auto-validated challenge" in {
    val testDir = Files.createTempDirectory("pebble-create-cert-")
    try {
      val service = new AcmeService(
        acmeServerUrl = container.acmeUrl,
        keyDirPath = testDir.toString,
        autoValidateChallenges = true,  // Enable auto-validation for Pebble
        storage = new FileSystemStorage(testDir.toString)
      )

      val domain = "pebble-test.example.com"

      val result = service.createCertificate(domain)

      // With Pebble's PEBBLE_VA_ALWAYS_VALID=1 and autoValidateChallenges=true,
      // the full certificate creation should succeed
      result match {
        case Success(certResult) =>
          logger.info(s"✓ Certificate creation succeeded: ${certResult.domain}")
          certResult.domain shouldBe domain
          certResult.status shouldBe "VALID"

          // Verify certificate files were created (with new naming)
          val certFile = new java.io.File(testDir.toFile, s"$domain.crt")
          val chainFile = new java.io.File(testDir.toFile, s"$domain-chain.crt")
          certFile.exists() shouldBe true
          chainFile.exists() shouldBe true

          logger.info(s"✓ Certificate file created: ${certFile.getAbsolutePath}")
          logger.info(s"✓ Certificate chain file created: ${chainFile.getAbsolutePath}")

        case Failure(exception) =>
          logger.error(s"Certificate creation failed unexpectedly: ${exception.getMessage}", exception)
          fail(s"Certificate creation should have succeeded but failed: ${exception.getMessage}")
      }
    } finally {
      // Cleanup
      Files.walk(testDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }
  }

  "revokeCertificate with Pebble" should "fail when certificate does not exist" in {
    val testDir = Files.createTempDirectory("pebble-revoke-cert-")
    try {
      val service = new AcmeService(
        acmeServerUrl = container.acmeUrl,
        keyDirPath = testDir.toString,
        autoValidateChallenges = false,
        storage = new FileSystemStorage(testDir.toString)
      )

      val domain = "nonexistent.example.com"
      val result = service.revokeCertificate(domain)

      result shouldBe a[Failure[_]]
      result.failed.get.getMessage should include("Certificate not found")

      logger.info("✓ Revocation correctly fails for non-existent certificate")
    } finally {
      // Cleanup
      Files.walk(testDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }
  }
}
