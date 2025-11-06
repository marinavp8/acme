package com.acme.storage

import com.bettercloud.vault.{Vault, VaultConfig}
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * HashiCorp Vault based certificate storage
 * 
 * Stores certificates in Vault's KV v2 secrets engine at:
 * {vaultPath}/{domain}
 * 
 * Note: For KV v2, don't include /data/ in the path - Vault adds it automatically
 * Example: Use "secret/acme" not "secret/data/acme"
 */
class VaultStorage(
  vaultAddress: String,
  vaultToken: String,
  vaultPath: String = "secret/acme"
) extends CertificateStorage {
  
  private val logger = LoggerFactory.getLogger(classOf[VaultStorage])
  
  private val config = new VaultConfig()
    .address(vaultAddress)
    .token(vaultToken)
    .build()
  
  private val vault = new Vault(config)
  
  logger.info(s"Vault storage initialized: $vaultAddress")
  
  override def saveCertificate(domain: String, certificate: String, privateKey: String, chain: String): Try[Unit] = Try {
    val secretPath = s"$vaultPath/$domain"
    
    val secrets: java.util.Map[String, Object] = Map[String, Object](
      "certificate" -> certificate,
      "private_key" -> privateKey,
      "chain" -> chain,
      "created_at" -> System.currentTimeMillis().toString,
      "domain" -> domain
    ).asJava
    
    vault.logical()
      .write(secretPath, secrets)
    
    logger.info(s"Certificate saved to Vault for domain: $domain at path: $secretPath")
  }
  
  override def getCertificate(domain: String): Try[StoredCertificate] = Try {
    val secretPath = s"$vaultPath/$domain"
    
    val response = vault.logical().read(secretPath)
    
    if (response.getData == null || response.getData.isEmpty) {
      throw new RuntimeException(s"Certificate not found in Vault for domain: $domain")
    }
    
    val data = response.getData.asScala.toMap
    
    StoredCertificate(
      domain = domain,
      certificate = data.getOrElse("certificate", ""),
      privateKey = data.getOrElse("private_key", ""),
      chain = data.getOrElse("chain", ""),
      metadata = Map(
        "storage" -> "vault",
        "vaultPath" -> secretPath,
        "createdAt" -> data.getOrElse("created_at", "unknown")
      )
    )
  }
  
  override def listCertificates(): Try[List[String]] = Try {
    // For KV v2, use vault.logical().list() with the data path (not metadata)
    // The library will handle the KV v2 semantics
    logger.info(s"Listing certificates from Vault path: $vaultPath")
    
    try {
      val response = vault.logical().list(vaultPath)
      
      logger.debug(s"List response status: ${response.getRestResponse.getStatus}")
      logger.debug(s"List data is null: ${response.getListData == null}")
      
      if (response.getListData == null || response.getListData.isEmpty) {
        logger.info("No certificates found in Vault")
        List.empty
      } else {
        val certs = response.getListData.asScala.toList.sorted
        logger.info(s"Found ${certs.size} certificates in Vault: ${certs.mkString(", ")}")
        certs
      }
    } catch {
      case e: Exception =>
        logger.error(s"Failed to list from Vault at path '$vaultPath': ${e.getMessage}", e)
        // If path doesn't exist yet, return empty list
        List.empty
    }
  }
  
  override def exists(domain: String): Try[Boolean] = Try {
    val secretPath = s"$vaultPath/$domain"
    
    try {
      val response = vault.logical().read(secretPath)
      response.getData != null && !response.getData.isEmpty
    } catch {
      case _: Exception => false
    }
  }
  
  override def deleteCertificate(domain: String): Try[Unit] = Try {
    val secretPath = s"$vaultPath/$domain"
    
    vault.logical().delete(secretPath)
    
    logger.info(s"Certificate deleted from Vault for domain: $domain")
  }
}

