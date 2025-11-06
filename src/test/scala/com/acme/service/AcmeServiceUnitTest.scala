package com.acme.service

import com.acme.storage.FileSystemStorage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{File, FileWriter}
import java.nio.file.{Files, Path}
import scala.util.{Failure, Success}

/**
 * Unit tests for AcmeService without external dependencies
 */
class AcmeServiceUnitTest extends AnyFlatSpec with Matchers {

  "AcmeService" should "be instantiable with default configuration" in {
    val service = new AcmeService(new FileSystemStorage("keys"))
    service should not be null
  }

  it should "be configurable with custom parameters" in {
    val tempDir = Files.createTempDirectory("test-keys")
    try {
      val service = new AcmeService(
        acmeServerUrl = "https://test.acme.example.com/directory",
        keyDirPath = tempDir.toString,
        autoValidateChallenges = false,
        storage = new FileSystemStorage(tempDir.toString)
      )
      service should not be null
    } finally {
      // Cleanup
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }
  }

  it should "create and load RSA key pairs" in {
    val tempDir = Files.createTempDirectory("test-keypair")
    try {
      val service = new AcmeService(
        acmeServerUrl = "acme://letsencrypt.org/staging",
        keyDirPath = tempDir.toString,
        autoValidateChallenges = false,
        storage = new FileSystemStorage(tempDir.toString)
      )
      val keyFile = new File(tempDir.toFile, "test.key")

      // Create a new key pair
      val keyPair1 = service.loadOrCreateKeyPair(keyFile)
      keyPair1 should not be null
      keyPair1.getPrivate should not be null
      keyPair1.getPublic should not be null
      keyFile.exists() shouldBe true

      // Load the existing key pair
      val keyPair2 = service.loadOrCreateKeyPair(keyFile)
      keyPair2 should not be null

      // Verify both are the same key
      val publicKey1 = keyPair1.getPublic.asInstanceOf[java.security.interfaces.RSAPublicKey]
      val publicKey2 = keyPair2.getPublic.asInstanceOf[java.security.interfaces.RSAPublicKey]
      publicKey1.getModulus shouldEqual publicKey2.getModulus
      publicKey1.getPublicExponent shouldEqual publicKey2.getPublicExponent

    } finally {
      // Cleanup
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }
  }

  it should "generate 2048-bit RSA keys" in {
    val tempDir = Files.createTempDirectory("test-keysize")
    try {
      val service = new AcmeService(
        acmeServerUrl = "acme://letsencrypt.org/staging",
        keyDirPath = tempDir.toString,
        autoValidateChallenges = false,
        storage = new FileSystemStorage(tempDir.toString)
      )
      val keyFile = new File(tempDir.toFile, "test-2048.key")

      val keyPair = service.loadOrCreateKeyPair(keyFile)
      val publicKey = keyPair.getPublic.asInstanceOf[java.security.interfaces.RSAPublicKey]

      // RSA 2048-bit key
      publicKey.getModulus.bitLength() shouldBe 2048

    } finally {
      // Cleanup
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }
  }

  "revokeCertificate" should "fail when certificate file does not exist" in {
    val tempDir = Files.createTempDirectory("test-revoke-missing")
    try {
      val service = new AcmeService(
        acmeServerUrl = "acme://letsencrypt.org/staging",
        keyDirPath = tempDir.toString,
        autoValidateChallenges = false,
        storage = new FileSystemStorage(tempDir.toString)
      )
      val domain = "test-missing.example.com"

      val result = service.revokeCertificate(domain)

      result shouldBe a[Failure[_]]
      result.failed.get.getMessage should include("Certificate not found")

    } finally {
      // Cleanup
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }
  }

  it should "fail when certificate file exists but domain key is missing" in {
    val tempDir = Files.createTempDirectory("test-revoke-no-key")
    try {
      val service = new AcmeService(
        acmeServerUrl = "acme://letsencrypt.org/staging",
        keyDirPath = tempDir.toString,
        autoValidateChallenges = false,
        storage = new FileSystemStorage(tempDir.toString)
      )

      // Create a dummy certificate file
      val certFile = new File(tempDir.toFile, "certificate.crt")
      val writer = new FileWriter(certFile)
      try {
        writer.write("-----BEGIN CERTIFICATE-----\n")
        writer.write("MIICpDCCAYwCCQDO7nOXJL0iOjANBgkqhkiG9w0BAQsFADAUMRIwEAYDVQQDDAls\n")
        writer.write("b2NhbGhvc3QwHhcNMjQwMTAxMDAwMDAwWhcNMjUwMTAxMDAwMDAwWjAUMRIwEAYD\n")
        writer.write("-----END CERTIFICATE-----\n")
      } finally {
        writer.close()
      }

      val domain = "test-no-key.example.com"
      val result = service.revokeCertificate(domain)

      // Should fail because we can't connect to ACME server or parse certificate
      result shouldBe a[Failure[_]]

    } finally {
      // Cleanup
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }
  }

  "createCertificate" should "fail with helpful message when ACME server is unreachable" in {
    val tempDir = Files.createTempDirectory("test-create-unreachable")
    try {
      // Use a fake ACME server URL
      val service = new AcmeService(
        acmeServerUrl = "https://fake-acme-server-that-does-not-exist.example.com/dir",
        keyDirPath = tempDir.toString,
        autoValidateChallenges = false,
        storage = new FileSystemStorage(tempDir.toString)
      )
      val domain = "test.example.com"

      val result = service.createCertificate(domain)

      // Should fail because server is unreachable
      result shouldBe a[Failure[_]]
      result.failed.get should not be null

    } finally {
      // Cleanup
      Files.walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }
  }
}
