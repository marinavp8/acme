package com.acme.testcontainers

import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

import java.time.Duration

/**
 * Testcontainer for Pebble ACME server
 * Pebble is a small ACME test server designed for testing ACME clients
 *
 * GitHub: https://github.com/letsencrypt/pebble
 */
class PebbleContainer(dockerImageName: String = "letsencrypt/pebble:latest")
  extends GenericContainer(
    dockerImage = dockerImageName,
    exposedPorts = Seq(14000, 15000), // 14000: ACME API (HTTPS), 15000: Management API (HTTPS)
    waitStrategy = Some(
      // Wait for Pebble to be ready - looks for HTTP server startup message
      // Using longer timeout for emulated containers (amd64 on arm64)
      Wait.forLogMessage(".*Listening on.*", 1)
        .withStartupTimeout(Duration.ofMinutes(3))
    )
  ) {

  // Configure Pebble with environment variables
  container.withEnv("PEBBLE_VA_NOSLEEP", "1")  // Don't sleep between validation attempts
  container.withEnv("PEBBLE_VA_ALWAYS_VALID", "1")  // Always validate challenges (for testing)
  container.withEnv("PEBBLE_WFE_NONCEREJECT", "0")  // Don't reject nonces

  /**
   * Get the ACME directory URL for this Pebble instance
   * Note: Pebble uses HTTPS with self-signed certificates
   */
  def acmeUrl: String = s"https://${container.getHost}:${container.getMappedPort(14000)}/dir"

  /**
   * Get the management API URL for this Pebble instance
   */
  def managementUrl: String = s"https://${container.getHost}:${container.getMappedPort(15000)}"

  /**
   * Get the mapped ACME port (14000 internal -> random external)
   */
  def acmePort: Int = container.getMappedPort(14000)

  /**
   * Get the mapped management port (15000 internal -> random external)
   */
  def managementPort: Int = container.getMappedPort(15000)
}

object PebbleContainer {
  def apply(): PebbleContainer = new PebbleContainer()

  def apply(dockerImageName: String): PebbleContainer = new PebbleContainer(dockerImageName)
}
