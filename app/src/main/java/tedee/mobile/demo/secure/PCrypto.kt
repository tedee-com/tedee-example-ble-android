package tedee.mobile.demo.secure

import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

object PCrypto {

  fun encodePublicKey(publicKey: PublicKey, asStructure: Boolean): ByteArray {
    val ecPublicKey = publicKey as ECPublicKey
    val ecPoint = ecPublicKey.w

    val n = ecPublicKey.params.curve.field.fieldSize / 8
    val publicKeyBytes = ByteArray((if (asStructure) 1 else 0) + 2 * n)

    publicKeyBytes[0] = 0x04 // uncompressed form
    val x = ecPoint.affineX.toByteArray()
    val y = ecPoint.affineY.toByteArray()

    var iOff = if (x.size - n > 0) x.size - n else 0
    var pOff = if (x.size - n < 0) n - x.size else 0
    System.arraycopy(x, iOff, publicKeyBytes, (if (asStructure) 1 else 0) + pOff, n - pOff)
    iOff = if (y.size - n > 0) y.size - n else 0
    pOff = if (y.size - n < 0) n - y.size else 0
    System.arraycopy(y, iOff, publicKeyBytes, (if (asStructure) 1 else 0) + pOff + n, n - pOff)

    return publicKeyBytes
  }

  fun decodePublicKey(publicKeyBytes: ByteArray, params: ECParameterSpec): ECPublicKey {
    val factory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
    val n = (publicKeyBytes.size - 1) / 2
    val nBytes = ByteArray(n)
    System.arraycopy(publicKeyBytes, 1, nBytes, 0, n)
    val x = BigInteger(1, nBytes)
    System.arraycopy(publicKeyBytes, 1 + n, nBytes, 0, n)
    val y = BigInteger(1, nBytes)
    val w = ECPoint(x, y)

    return factory.generatePublic(ECPublicKeySpec(w, params)) as ECPublicKey
  }
}
