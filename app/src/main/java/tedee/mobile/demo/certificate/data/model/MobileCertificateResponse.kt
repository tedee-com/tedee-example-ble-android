package tedee.mobile.demo.certificate.data.model

data class MobileCertificateResponse(
  val certificate: String,
  val expirationDate: String,
  val devicePublicKey: String,
  val mobilePublicKey: String
)