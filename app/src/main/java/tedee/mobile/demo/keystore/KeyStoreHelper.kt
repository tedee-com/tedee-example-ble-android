package tedee.mobile.demo.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import tedee.mobile.demo.secure.PCrypto
import timber.log.Timber
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec

object KeyStoreHelper {
  private const val MOBILE_KEYS_ALIAS = "MobileKeyAlias"
  private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
  private const val EC_ALGORITHM = "secp256r1"

  fun generateMobileKeyPair(postPublic: (String) -> Unit, onError: () -> Unit) {
    try {
      val keyGenerator = KeyPairGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER)
      if (keyGenerator == null) {
        onError.invoke()
        return
      }
      initKeyGenerator(keyGenerator, true)
      val keyPair = try {
        keyGenerator.generateKeyPair()
      } catch (e: Exception) {
        Timber.w(e)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && e is StrongBoxUnavailableException) {
          initKeyGenerator(keyGenerator, false)
          keyGenerator.generateKeyPair()
        } else {
          throw e
        }
      }

      if (keyPair == null) {
        onError.invoke()
        return
      }

      postPublic.invoke(
        Base64.encodeToString(
          PCrypto.encodePublicKey(keyPair.public, true), Base64.DEFAULT
        )
      )
    } catch (e: Exception) {
      onError.invoke()
    }
  }

  fun getMobileKeyPair() =
    try {
      KeyStore.getInstance(KEYSTORE_PROVIDER).run {
        load(null)
        val privateKey = getKey(MOBILE_KEYS_ALIAS, null)
        val publicKey = getCertificate(MOBILE_KEYS_ALIAS).publicKey
        KeyPair(publicKey, privateKey as PrivateKey)
      }
    } catch (e: Exception) {
      Timber.w("Error in getting auth key par")
      null
    }

  fun getMobilePublicKey(): String? {
    getMobileKeyPair()?.public?.let {
      return Base64.encodeToString(PCrypto.encodePublicKey(it, true), Base64.DEFAULT)
    } ?: return null
  }

  fun generateTempKeyPair(): KeyPair =
    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC).run {
      initialize(256)
      generateKeyPair()
    }

  private fun initKeyGenerator(keyGenerator: KeyPairGenerator, shouldUseStrongBoxBacked: Boolean) {
    keyGenerator.initialize(
      KeyGenParameterSpec.Builder(
        MOBILE_KEYS_ALIAS,
        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
      ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(
          shouldUseStrongBoxBacked
        )
      }.setAlgorithmParameterSpec(ECGenParameterSpec(EC_ALGORITHM))
        .setDigests(KeyProperties.DIGEST_SHA256)
        .build()
    )
  }
}
