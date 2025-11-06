package com.acme.controller

import com.acme.service.{AcmeService, CertificateResult, CertificateInfo, CertificateSummary}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, ResponseEntity}
import org.springframework.web.bind.annotation._

import scala.util.{Failure, Success}

@RestController
@RequestMapping(Array("/api/certificates"))
class CertificateController @Autowired()(acmeService: AcmeService) {

  @PostMapping(Array("/create"))
  def createCertificado(@RequestBody request: CreateCertificateRequest): ResponseEntity[ApiResponse] = {
    if (request.domain == null || request.domain.isEmpty) {
      return ResponseEntity
        .badRequest()
        .body(ErrorResponse("Domain parameter is required"))
    }

    acmeService.createCertificate(request.domain) match {
      case Success(result) =>
        ResponseEntity.ok(CertificateResponse(
          success = true,
          message = s"Certificate created successfully for domain: ${result.domain}",
          data = Some(result)
        ))

      case Failure(exception) =>
        ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ErrorResponse(
            s"Failed to create certificate: ${exception.getMessage}"
          ))
    }
  }

  @PostMapping(Array("/revoke"))
  def revokeCertificate(@RequestBody request: RevokeCertificateRequest): ResponseEntity[ApiResponse] = {
    if (request.domain == null || request.domain.isEmpty) {
      return ResponseEntity
        .badRequest()
        .body(ErrorResponse("Domain parameter is required"))
    }

    acmeService.revokeCertificate(request.domain) match {
      case Success(message) =>
        ResponseEntity.ok(SuccessResponse(
          success = true,
          message = message
        ))

      case Failure(exception) =>
        ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ErrorResponse(
            s"Failed to revoke certificate: ${exception.getMessage}"
          ))
    }
  }

  @GetMapping(Array("/health"))
  def health(): ResponseEntity[ApiResponse] = {
    ResponseEntity.ok(SuccessResponse(
      success = true,
      message = "ACME service is running"
    ))
  }

  @GetMapping(Array("/config"))
  def config(): ResponseEntity[ConfigResponse] = {
    ResponseEntity.ok(ConfigResponse(
      serverUrl = acmeService.getServerUrl,
      keyDir = acmeService.getKeyDir,
      autoValidate = acmeService.getAutoValidate
    ))
  }

  @PostMapping(Array("/info"))
  def getCertificateInfo(@RequestBody request: CertificateInfoRequest): ResponseEntity[ApiResponse] = {
    if (request.domain == null || request.domain.isEmpty) {
      return ResponseEntity
        .badRequest()
        .body(ErrorResponse("Domain parameter is required"))
    }

    acmeService.getCertificateInfo(request.domain) match {
      case Success(info) =>
        ResponseEntity.ok(CertificateInfoResponse(
          success = true,
          message = s"Certificate info retrieved for domain: ${request.domain}",
          data = Some(info)
        ))

      case Failure(exception) =>
        ResponseEntity
          .status(HttpStatus.NOT_FOUND)
          .body(ErrorResponse(
            s"Failed to get certificate info: ${exception.getMessage}"
          ))
    }
  }

  @GetMapping(Array("/list"))
  def listCertificates(): ResponseEntity[ApiResponse] = {
    acmeService.listCertificates() match {
      case Success(certificates) =>
        ResponseEntity.ok(CertificateListResponse(
          success = true,
          message = s"Found ${certificates.size} certificate(s)",
          count = certificates.size,
          certificates = certificates
        ))

      case Failure(exception) =>
        ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ErrorResponse(
            s"Failed to list certificates: ${exception.getMessage}"
          ))
    }
  }
}

// Request models
case class CreateCertificateRequest(domain: String)
case class RevokeCertificateRequest(domain: String)
case class CertificateInfoRequest(domain: String)

// Response models
sealed trait ApiResponse
case class SuccessResponse(success: Boolean, message: String) extends ApiResponse
case class ErrorResponse(error: String) extends ApiResponse {
  val success: Boolean = false
}
case class CertificateResponse(
  success: Boolean,
  message: String,
  data: Option[CertificateResult]
) extends ApiResponse
case class ConfigResponse(
  serverUrl: String,
  keyDir: String,
  autoValidate: Boolean
)
case class CertificateInfoResponse(
  success: Boolean,
  message: String,
  data: Option[CertificateInfo]
) extends ApiResponse
case class CertificateListResponse(
  success: Boolean,
  message: String,
  count: Int,
  certificates: List[CertificateSummary]
) extends ApiResponse
