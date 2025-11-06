package com.acme.config

import com.acme.service.AcmeService
import com.acme.storage.{CertificateStorage, FileSystemStorage, VaultStorage}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.core.env.Environment
import org.slf4j.LoggerFactory

import javax.net.ssl._
import java.security.cert.X509Certificate

@Configuration
class AcmeConfig @Autowired()(env: Environment) {

  private val logger = LoggerFactory.getLogger(classOf[AcmeConfig])

  @Bean
  def certificateStorage(): CertificateStorage = {
    val storageType = env.getProperty("acme.storage.type", "filesystem")
    logger.info(s"Configuring certificate storage: $storageType")
    
    storageType.toLowerCase match {
      case "vault" =>
        val vaultToken = env.getProperty("acme.storage.vault.token", "")
        if (vaultToken.isEmpty) {
          logger.error("Vault token not configured! Check VAULT_TOKEN environment variable or acme.storage.vault.token")
          throw new RuntimeException("Vault token is required when using vault storage")
        }
        val vaultAddress = env.getProperty("acme.storage.vault.address", "http://localhost:8200")
        val vaultPath = env.getProperty("acme.storage.vault.path", "secret/data/acme")
        logger.info(s"  - Vault address: $vaultAddress")
        logger.info(s"  - Vault path: $vaultPath")
        new VaultStorage(vaultAddress, vaultToken, vaultPath)
        
      case "filesystem" =>
        val keyDir = env.getProperty("acme.key-dir", "keys")
        logger.info(s"  - Storage directory: $keyDir")
        new FileSystemStorage(keyDir)
        
      case unknown =>
        logger.error(s"Unknown storage type: $unknown. Using filesystem as fallback.")
        val keyDir = env.getProperty("acme.key-dir", "keys")
        new FileSystemStorage(keyDir)
    }
  }

  @Bean
  def acmeService(storage: CertificateStorage): AcmeService = {
    val serverUrl = env.getProperty("acme.server-url", "acme://letsencrypt.org/staging")
    val keyDir = env.getProperty("acme.key-dir", "keys")
    val autoValidate = env.getProperty("acme.auto-validate", classOf[Boolean], false)
    val disableSslVerification = env.getProperty("acme.disable-ssl-verification", classOf[Boolean], false)
    val storageType = env.getProperty("acme.storage.type", "filesystem")
    
    logger.info(s"Configuring AcmeService with:")
    logger.info(s"  - Server URL: $serverUrl")
    logger.info(s"  - Key directory: $keyDir")
    logger.info(s"  - Auto-validate challenges: $autoValidate")
    logger.info(s"  - Disable SSL verification: $disableSslVerification")
    logger.info(s"  - Storage backend: $storageType")

    if (disableSslVerification) {
      disableSSLCertificateVerification()
      logger.warn("⚠️  SSL certificate verification DISABLED - Only for testing!")
    }

    new AcmeService(serverUrl, keyDir, autoValidate, storage)
  }

  /**
   * Disables SSL certificate verification for testing with Pebble
   * WARNING: This should ONLY be used in testing/development environments!
   * NEVER use this in production!
   */
  private def disableSSLCertificateVerification(): Unit = {
    val trustAllCerts = Array[TrustManager](new X509TrustManager {
      def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = {}
      def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = {}
      def getAcceptedIssuers(): Array[X509Certificate] = Array.empty
    })

    val sc = SSLContext.getInstance("TLS")
    sc.init(null, trustAllCerts, new java.security.SecureRandom())

    // Set default SSLContext for java.net.http.HttpClient (used by acme4j v3+)
    SSLContext.setDefault(sc)

    // Also set for HttpsURLConnection (legacy support)
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())

    // Disable hostname verification
    HttpsURLConnection.setDefaultHostnameVerifier((_: String, _: SSLSession) => true)

    logger.info("SSL certificate verification disabled for testing")
  }
}

