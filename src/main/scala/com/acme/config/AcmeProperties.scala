package com.acme.config

import org.springframework.boot.context.properties.ConfigurationProperties

import scala.beans.BeanProperty

@ConfigurationProperties(prefix = "acme")
class AcmeProperties {
  
  @BeanProperty var serverUrl: String = "acme://letsencrypt.org/staging"
  @BeanProperty var keyDir: String = "keys"
  @BeanProperty var autoValidate: Boolean = false
  @BeanProperty var disableSslVerification: Boolean = false
  
  @BeanProperty var storage: StorageProperties = new StorageProperties()
}

class StorageProperties {
  @BeanProperty var `type`: String = "filesystem"
  @BeanProperty var vault: VaultProperties = new VaultProperties()
}

class VaultProperties {
  @BeanProperty var address: String = "http://localhost:8200"
  @BeanProperty var token: String = ""
  @BeanProperty var path: String = "secret/data/acme"
}

