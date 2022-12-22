package tedee.mobile.demo.secure

object SecureConnectionConstants {
  const val LEN_AUTH_TAG = 16
  const val LEN_LENGTH = 2
  const val OFFSET_DATA = 0
  const val LEN_RANDOM = 32
  const val LEN_RANDOM_ENCRYPTED = 48
  const val LEN_SESSION_ID = 4
  const val LEN_NEW_HELLO_HEADER = 3
  const val LEN_NEW_HELLO = LEN_RANDOM + LEN_NEW_HELLO_HEADER
  val NEW_HEADER_V2 = byteArrayOf(2, 0, 0)
  const val NEW_MTU_INDEX = 1
  const val DIGEST_ALGORITHM = "SHA-256"
  const val SECRET_KEY_ALGORITHM = "HmacSHA256"

  val CLIENT_HS_TRAFFIC = "ptlsc hs traffic".toByteArray()
  val SERVER_HS_TRAFFIC = "ptlss hs traffic".toByteArray()
  val CLIENT_AP_TRAFFIC = "ptlsc ap traffic".toByteArray()
  val SERVER_AP_TRAFFIC = "ptlss ap traffic".toByteArray()

  const val LEN_HEADER = 1
  const val DATA_NOT_ENCRYPTED = 0.toByte()
  const val DATA_ENCRYPTED = 1.toByte()
  const val MESSAGE_HELLO = 3.toByte()
  const val ALERT = 4.toByte()
  const val SERVER_VERIFY = 5.toByte()
  const val CLIENT_VERIFY = 6.toByte()
  const val CLIENT_VERIFY_END = 7.toByte()
  const val SESSION_INITIALIZED = 8.toByte()
}

class SecureException : Error()