package tedee.mobile.demo.bluetooth

import com.polidea.rxandroidble2.RxBleConnection
import io.reactivex.Single
import tedee.mobile.demo.secure.SecureConnectionConstants
import tedee.mobile.demo.secure.SecureException
import tedee.mobile.demo.secure.SecureSession
import tedee.mobile.demo.secure.SecurityData
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.*

fun RxBleConnection.updateCharacteristic(
  characteristic: String, message: ByteArray,
): Single<ByteArray> = writeCharacteristic(UUID.fromString(characteristic), message)

fun buildEncryptedMessageToSend(
  session: SecureSession,
  mainMessage: Byte,
  payload: ByteArray? = null,
  callback: (ByteArray) -> Unit,
) {
  var buffer = ByteBuffer.allocate(1 + (payload?.size ?: 0))
  buffer.put(mainMessage)
  payload?.let {
    buffer.put(it)
  }
  session.write(SecurityData(buffer.array(), { message ->
    if (message == null) throw SecureException()
    buffer = ByteBuffer.allocate(SecureConnectionConstants.LEN_HEADER + message.size)
    buffer.put(SecureConnectionConstants.DATA_ENCRYPTED)
    buffer.put(message)
    callback(buffer.array())
  }))
}

fun buildUnencryptedMessageToSend(mainMessage: Byte, payload: ByteArray? = null): ByteArray {
  val buffer = ByteBuffer.allocate(2 + (payload?.size ?: 0))
  buffer.put(SecureConnectionConstants.DATA_NOT_ENCRYPTED)
  buffer.put(mainMessage)
  payload?.let {
    buffer.put(payload)
  }
  return buffer.array()
}

fun buildSecureEstablishMessageToSend(header: Byte, message: ByteArray): ByteArray {
  val buffer = ByteBuffer.allocate(SecureConnectionConstants.LEN_HEADER + message.size)
  buffer.put(header)
  buffer.put(message)
  return buffer.array()
}

fun extractSerialNumber(uuids: List<String>?, vararg servicesToIgnore: String) =
  extractSerialNumber(uuids, servicesToIgnore.toList())

fun extractSerialNumber(uuids: List<String>?, servicesToIgnore: List<String>): String? {
  return uuids?.let { serviceUUIDs ->
    return if (serviceUUIDs.size == 2) {
      val uuid = if (servicesToIgnore.contains(serviceUUIDs[0])) {
        serviceUUIDs[1]
      } else {
        serviceUUIDs[0]
      }
      //serial number format XXXXXXXX-XXXXXX
      //serial number "encoded" in UUID xXXX0000-XXXX-XXXX-XX00-MMMMMMMMMMMM
      //x - part of serial, M - mac address (1.1 devices only)
      parseSerialNumber(uuid)
    } else {
      null
    }
  } ?: return null
}

fun parseSerialNumber(uuid: String): String? {
  return try {
    uuid.substring(0, 4) + uuid.substring(9, 13) + "-" + uuid.substring(
      14,
      18
    ) + uuid.substring(19, 21)
  } catch (e: Exception) {
    Timber.e(e, "Error in parsing serial number")
    null
  }
}
