package com.acme

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class AcmeApplication

object AcmeApplication {
  def main(args: Array[String]): Unit = {
    SpringApplication.run(classOf[AcmeApplication], args: _*)
  }
}
