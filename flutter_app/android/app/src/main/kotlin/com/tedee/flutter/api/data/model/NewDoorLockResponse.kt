package com.tedee.flutter.api.data.model

data class NewDoorLockResponse(
  val id: Int,
  val revision: Int,
  val targetDeviceRevision: Int,
  val authPublicKey: String
)
