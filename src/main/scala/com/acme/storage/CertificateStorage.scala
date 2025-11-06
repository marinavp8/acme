package com.acme.storage

import java.io.File
import scala.util.Try

/**
 * Interface for certificate storage backends
 */
trait CertificateStorage {
  
  /**
   * Saves a certificate and its private key
   */
  def saveCertificate(domain: String, certificate: String, privateKey: String, chain: String): Try[Unit]
  
  /**
   * Retrieves a certificate
   */
  def getCertificate(domain: String): Try[StoredCertificate]
  
  /**
   * Lists all stored certificates
   */
  def listCertificates(): Try[List[String]]
  
  /**
   * Checks if a certificate exists
   */
  def exists(domain: String): Try[Boolean]
  
  /**
   * Deletes a certificate
   */
  def deleteCertificate(domain: String): Try[Unit]
}

case class StoredCertificate(
  domain: String,
  certificate: String,
  privateKey: String,
  chain: String,
  metadata: Map[String, String] = Map.empty
)

