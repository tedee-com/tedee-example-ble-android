package com.tedee.flutter.api.data.model

data class MobileCertificateResponse(
  val certificate: String,
  val expirationDate: String,
  val devicePublicKey: String,
  val mobilePublicKey: String
)
