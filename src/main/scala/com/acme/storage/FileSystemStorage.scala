package com.acme.storage

import org.slf4j.LoggerFactory

import java.io.{File, FileReader, FileWriter}
import scala.io.Source
import scala.util.{Try, Using}

/**
 * File system based certificate storage (current implementation)
 */
class FileSystemStorage(keyDirPath: String) extends CertificateStorage {
  
  private val logger = LoggerFactory.getLogger(classOf[FileSystemStorage])
  private val KEY_DIR = new File(keyDirPath)
  
  // Create key directory if it doesn't exist
  if (!KEY_DIR.exists()) {
    KEY_DIR.mkdirs()
    logger.info(s"Created key directory: ${KEY_DIR.getAbsolutePath}")
  }
  
  override def saveCertificate(domain: String, certificate: String, privateKey: String, chain: String): Try[Unit] = Try {
    val certFile = new File(KEY_DIR, s"$domain.crt")
    val keyFile = new File(KEY_DIR, s"$domain.key")
    val chainFile = new File(KEY_DIR, s"$domain-chain.crt")
    
    // Save certificate
    val certWriter = new FileWriter(certFile)
    try {
      certWriter.write(certificate)
    } finally {
      certWriter.close()
    }
    
    // Save private key
    val keyWriter = new FileWriter(keyFile)
    try {
      keyWriter.write(privateKey)
    } finally {
      keyWriter.close()
    }
    
    // Save chain
    val chainWriter = new FileWriter(chainFile)
    try {
      chainWriter.write(chain)
    } finally {
      chainWriter.close()
    }
    
    logger.info(s"Certificate saved to filesystem for domain: $domain")
  }
  
  override def getCertificate(domain: String): Try[StoredCertificate] = Try {
    val certFile = new File(KEY_DIR, s"$domain.crt")
    val keyFile = new File(KEY_DIR, s"$domain.key")
    val chainFile = new File(KEY_DIR, s"$domain-chain.crt")
    
    if (!certFile.exists()) {
      throw new RuntimeException(s"Certificate not found for domain: $domain")
    }
    
    val certificate = Using(Source.fromFile(certFile))(_.mkString).get
    val privateKey = Using(Source.fromFile(keyFile))(_.mkString).get
    val chain = Using(Source.fromFile(chainFile))(_.mkString).get
    
    StoredCertificate(
      domain = domain,
      certificate = certificate,
      privateKey = privateKey,
      chain = chain,
      metadata = Map(
        "storage" -> "filesystem",
        "certPath" -> certFile.getAbsolutePath
      )
    )
  }
  
  override def listCertificates(): Try[List[String]] = Try {
    if (!KEY_DIR.exists()) {
      List.empty
    } else {
      KEY_DIR.listFiles()
        .filter(f => f.getName.endsWith(".crt") && !f.getName.endsWith("-chain.crt"))
        .map(_.getName.stripSuffix(".crt"))
        .toList
        .sorted
    }
  }
  
  override def exists(domain: String): Try[Boolean] = Try {
    new File(KEY_DIR, s"$domain.crt").exists()
  }
  
  override def deleteCertificate(domain: String): Try[Unit] = Try {
    val certFile = new File(KEY_DIR, s"$domain.crt")
    val keyFile = new File(KEY_DIR, s"$domain.key")
    val chainFile = new File(KEY_DIR, s"$domain-chain.crt")
    
    certFile.delete()
    keyFile.delete()
    chainFile.delete()
    
    logger.info(s"Certificate deleted from filesystem for domain: $domain")
  }
}

