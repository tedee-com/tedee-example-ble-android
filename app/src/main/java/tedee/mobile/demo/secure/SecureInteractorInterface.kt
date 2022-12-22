package tedee.mobile.demo.secure

interface SecureInteractorInterface {
  fun sendHello(message: ByteArray)
  fun sendServerVerify(message: ByteArray)
  fun sendClientVerify(message: ByteArray)
  fun sendClientVerifyEnd(message: ByteArray)
}