name := "mvp-acme"

version := "0.1.0"

scalaVersion := "2.13.12"

libraryDependencies ++= Seq(
  // Spring Boot
  "org.springframework.boot" % "spring-boot-starter-web" % "3.2.0",
  "org.springframework.boot" % "spring-boot-starter" % "3.2.0",

  // ACME4J
  "org.shredzone.acme4j" % "acme4j-client" % "3.5.1",

  // JSON processing
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.16.0",

  // Logging
  "ch.qos.logback" % "logback-classic" % "1.4.14",

  // Bouncycastle for crypto operations
  "org.bouncycastle" % "bcprov-jdk18on" % "1.77",
  "org.bouncycastle" % "bcpkix-jdk18on" % "1.77",
  
  // HashiCorp Vault
  "com.bettercloud" % "vault-java-driver" % "5.1.0",

  // Testing
  "org.springframework.boot" % "spring-boot-starter-test" % "3.2.0" % Test,
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "org.scalatestplus" %% "mockito-4-11" % "3.2.17.0" % Test,
  "org.testcontainers" % "testcontainers" % "1.19.3" % Test,
  "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.41.0" % Test
)

// Enable Java annotation processing for Spring
enablePlugins(JavaAppPackaging)

Compile / mainClass := Some("com.acme.AcmeApplication")

// Enable forking for run command (reduces SBT class loader warnings)
run / fork := true
// Pass JVM options when forked (for Spring profiles, etc)
run / javaOptions ++= sys.props.get("spring.profiles.active")
  .map(p => s"-Dspring.profiles.active=$p")
  .toSeq

// Enable forking for tests and add JVM options to support newer Java versions with Mockito
Test / fork := true
Test / javaOptions ++= Seq(
  "-Dnet.bytebuddy.experimental=true"
)
