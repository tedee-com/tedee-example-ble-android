package tedee.mobile.demo.secure

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import tedee.mobile.demo.extentions.print
import tedee.mobile.demo.keystore.KeyStoreHelper
import tedee.mobile.demo.secure.SecureConnectionConstants.CLIENT_AP_TRAFFIC
import tedee.mobile.demo.secure.SecureConnectionConstants.DIGEST_ALGORITHM
import tedee.mobile.demo.secure.SecureConnectionConstants.LEN_AUTH_TAG
import tedee.mobile.demo.secure.SecureConnectionConstants.LEN_HEADER
import tedee.mobile.demo.secure.SecureConnectionConstants.LEN_LENGTH
import tedee.mobile.demo.secure.SecureConnectionConstants.OFFSET_DATA
import tedee.mobile.demo.secure.SecureConnectionConstants.SERVER_AP_TRAFFIC
import timber.log.Timber
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.ECParameterSpec
import java.util.*
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

val EMPTY_SESSION_ID = byteArrayOf(0, 0, 0, 0)

class SecureSession(
  private val authData: ByteArray,
  private val privateKey: PrivateKey,
  private val reconnect: () -> Unit,
) {
  private var randomData: ByteArray = ByteArray(SecureConnectionConstants.LEN_NEW_HELLO)
  private var peerRandomData: ByteArray = ByteArray(SecureConnectionConstants.LEN_NEW_HELLO)
  private var packageSize = 0
  private lateinit var peerEcdhPublicKey: ECPublicKey
  private lateinit var ecdhKeyPair: KeyPair
  private var sharedSecret: SecretKey? = null
  private var helloHash: ByteArray? = null
  private val hsDigest = MessageDigest.getInstance(DIGEST_ALGORITHM)
  private var protector: MessageCipher? = null
  private var peerProtector: MessageCipher? = null
  private var peerAuthData: ByteArray? = null
  private var peerVerifyBytes: ByteArray? = null
  private var helloVerifyHash: ByteArray? = null
  private var hsHash: ByteArray? = null
  private lateinit var peerEcdhPublicKeyBytes: ByteArray
  private lateinit var ecdhPublicKeyBytes: ByteArray
  private lateinit var verifyBytes: ByteArray
  private val ecdsa: Signature = Signature.getInstance("SHA256WithECDSA")
  private val encryptor = PublishProcessor.create<SecurityData>()
  private val decryptor = PublishProcessor.create<SecurityData>()
  private val disposables = CompositeDisposable()
  private var hasFinished = false
  private var additionalRandomData: ByteArray = ByteArray(SecureConnectionConstants.LEN_RANDOM_ENCRYPTED)
  private var cacheDataKey: SecretKey? = null
  private var sessionIdArray: ByteArray = ByteArray(SecureConnectionConstants.LEN_SESSION_ID)
  private val cacheDisposables = CompositeDisposable()
  private var sessionId: ByteArray = EMPTY_SESSION_ID

  init {
    setupEncryptor()
    setupDecryptor()
  }

  private fun setupEncryptor() {
    encryptor
      .observeOn(Schedulers.from(Executors.newFixedThreadPool(1)))
      .map { it.apply { newData = encrypt(data) } }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        {
          if (hasFinished) {
            it.callback(it.newData)
          } else {
            Timber.e("SESSION: Attempt to encrypt data when no secure session established")
          }
        },
        {
          Timber.e(it, "SESSION: error in setupEncryptor")
          clearSession()
        }
      ).addTo(disposables)
  }

  private fun setupDecryptor() {
    decryptor
      .observeOn(Schedulers.from(Executors.newFixedThreadPool(1)))
      .map { it.apply { newData = decrypt(data) } }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        {
          if (hasFinished) {
            it.callback(it.newData)
          } else {
            Timber.e("SESSION: Attempt to decrypt data when no secure session established")
          }
        },
        {
          Timber.e(it, "SESSION: error in setupDecryptor")
          clearSession()
        }
      ).addTo(disposables)
  }


  private fun encrypt(data: ByteArray): ByteArray? {
    if (!hasFinished) { // is handshake finished
      return null
    }
    val message = ByteArray(data.size + LEN_AUTH_TAG)
    System.arraycopy(data, 0, message, OFFSET_DATA, data.size)
    val encryptedMessage: ByteArray
    try {
      encryptedMessage =
        protector?.transform(message, message.size - LEN_AUTH_TAG) ?: ByteArray(0)
    } catch (e: Exception) {
      Timber.e(e, "SESSION: Error in encrypt data")
      clearSession()
      reconnect()
      return null
    }
    System.arraycopy(encryptedMessage, 0, message, OFFSET_DATA, message.size)
    return message
  }

  private fun decrypt(message: ByteArray): ByteArray? {
    if (!hasFinished) { // is handshake finished
      return null
    }

    return try {
      peerProtector?.transform(message, message.size) ?: ByteArray(0)
    } catch (e: Exception) {
      Timber.e(e, "SESSION: Error in decrypt data")
      clearSession()
      reconnect()
      null
    }
  }

  private fun clearSession() {
    cacheDisposables.clear()
    sessionId = EMPTY_SESSION_ID
    cacheDataKey = null
    randomData = ByteArray(SecureConnectionConstants.LEN_NEW_HELLO)
    peerRandomData = ByteArray(SecureConnectionConstants.LEN_NEW_HELLO)
    additionalRandomData = ByteArray(SecureConnectionConstants.LEN_RANDOM_ENCRYPTED)
    sessionIdArray = ByteArray(SecureConnectionConstants.LEN_SESSION_ID)
    resetSession()
  }

  fun resetSession() {
    disposables.clear()
    Arrays.fill(randomData, 0.toByte())
    Arrays.fill(peerRandomData, 0.toByte())
    sharedSecret = null // hope it will be cleared
    protector = null
    peerProtector = null
    hsDigest.reset()
    peerAuthData = null
    peerVerifyBytes = null
    helloHash = null
    helloVerifyHash = null
    hsHash = null
    hasFinished = false
  }

  fun buildHello(): ByteArray {
    prepareSession()
    val generatedRandomData = ByteArray(SecureConnectionConstants.LEN_RANDOM)
    SecureRandom().nextBytes(generatedRandomData)
    val helloLen =
      SecureConnectionConstants.LEN_NEW_HELLO_HEADER + SecureConnectionConstants.LEN_RANDOM + ecdhPublicKeyBytes.size +
          SecureConnectionConstants.LEN_RANDOM_ENCRYPTED + SecureConnectionConstants.LEN_SESSION_ID
    val message = ByteArray(helloLen)
    val sessionId = sessionId
    val additionalV2Data = protector?.transform(generatedRandomData, generatedRandomData.size)
      ?: ByteArray(SecureConnectionConstants.LEN_RANDOM_ENCRYPTED)
    System.arraycopy(
      SecureConnectionConstants.NEW_HEADER_V2 + generatedRandomData + ecdhPublicKeyBytes + additionalV2Data + sessionId,
      0,
      message,
      0,
      helloLen
    )
    saveHelloData(message, additionalV2Data, sessionId)
    hsDigest.update(message, 0, message.size)
    return message
  }

  private fun saveHelloData(
    message: ByteArray,
    additionalV2Data: ByteArray,
    sessionId: ByteArray,
  ) {
    System.arraycopy(message, 0, randomData, 0, SecureConnectionConstants.LEN_NEW_HELLO)
    System.arraycopy(
      additionalV2Data,
      0,
      additionalRandomData,
      0,
      SecureConnectionConstants.LEN_RANDOM_ENCRYPTED
    )

    System.arraycopy(sessionId, 0, sessionIdArray, 0, SecureConnectionConstants.LEN_SESSION_ID)
  }

  @Throws(InvalidAlgorithmParameterException::class, NoSuchAlgorithmException::class)
  private fun prepareSession() {
    ecdhKeyPair = KeyStoreHelper.generateTempKeyPair()
    ecdhPublicKeyBytes = PCrypto.encodePublicKey(ecdhKeyPair.public, true)
    resetSession()
  }

  fun parseHello(message: ByteArray) {
    System.arraycopy(message, 0, peerRandomData, 0, SecureConnectionConstants.LEN_NEW_HELLO) // get peer RANDOM
    peerEcdhPublicKeyBytes = ByteArray(message.size - SecureConnectionConstants.LEN_NEW_HELLO)
    System.arraycopy(
      message,
      SecureConnectionConstants.LEN_NEW_HELLO,
      peerEcdhPublicKeyBytes,
      0,
      peerEcdhPublicKeyBytes.size
    )
    packageSize = message[SecureConnectionConstants.NEW_MTU_INDEX].toInt() and 0xFF
    try {
      peerEcdhPublicKey =
        PCrypto.decodePublicKey(
          peerEcdhPublicKeyBytes,
          getECParameterSpec()
        )
    } catch (e: GeneralSecurityException) {
      Timber.e(e, "SESSION: error in parseHello")
      clearSession()
    }

    hsDigest.update(message, 0, message.size)

    val secret = generateSharedSecret(ecdhKeyPair.private, peerEcdhPublicKey)
    sharedSecret = SecretKeySpec(secret, SecureConnectionConstants.SECRET_KEY_ALGORITHM)
    Arrays.fill(secret, 0.toByte())
    helloHash =
      (hsDigest.clone() as MessageDigest).digest() // hash from CLIENT HELLO and SERVER HELLO
    protector = MessageCipher(
      sharedSecret,
      SecureConnectionConstants.CLIENT_HS_TRAFFIC,
      helloHash,
      Cipher.ENCRYPT_MODE
    )
    peerProtector = MessageCipher(
      sharedSecret,
      SecureConnectionConstants.SERVER_HS_TRAFFIC,
      helloHash,
      Cipher.DECRYPT_MODE
    )
  }

  fun getECParameterSpec(): ECParameterSpec = (ecdhKeyPair.public as ECPublicKey).params

  private fun generateSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
    val keyAgreement = KeyAgreement.getInstance("ECDH")
    keyAgreement.init(privateKey)
    keyAgreement.doPhase(publicKey, true)
    return keyAgreement.generateSecret()
  }

  fun parseVerify(incomingMessage: ByteArray) {
    try {
      peerProtector?.let { peerProtector ->
        // read peer authentication data, verify bytes and hash
        val message = peerProtector.transform(incomingMessage, incomingMessage.size)
        var offset = 0
        var len = MessageCipher.getLength(message, offset)
        peerAuthData = ByteArray(len)
        peerAuthData?.let { peerAuthData ->
          offset += LEN_LENGTH
          System.arraycopy(message, offset, peerAuthData, 0, peerAuthData.size)
          offset += peerAuthData.size
          len = MessageCipher.getLength(message, offset)
          peerVerifyBytes = ByteArray(len)
          peerVerifyBytes?.let { peerVerifyBytes ->
            offset += LEN_LENGTH
            System.arraycopy(message, offset, peerVerifyBytes, 0, peerVerifyBytes.size)
            offset += peerVerifyBytes.size
            len = MessageCipher.getLength(message, offset)
            offset += LEN_LENGTH
            if (len != 32) throw SecureException()
            helloHash?.let { helloHash ->
              // check hash value
              while (len-- > 0) {
                if (helloHash[len] != message[offset + len]) throw SecureException()
              }
              offset += helloHash.size

              // add message to current hash calculation
              hsDigest.update(message, 0, offset)
              // hash from CLIENT HELLO / SERVER HELLO / SERVER VERIFY
              helloVerifyHash = (hsDigest.clone() as MessageDigest).digest()
            } ?: throw SecureException()
          } ?: throw SecureException()
        } ?: throw SecureException()
      } ?: throw SecureException()
    } catch (e: Exception) {
      Timber.e(e, "SESSION: error in parseVerify")
      clearSession()
      throw SecureException()
    }
  }

  fun ready(data: ByteArray) {
    hasFinished = true
    if (data.isNotEmpty()) {
      sessionId = data
      Timber.d("SESSION: session ready, id = ${data.print()}")
    }
  }

  @Throws(SecureException::class)
  fun peerVerify(publicKey: PublicKey): Boolean {
    try {
      ecdsa.initVerify(publicKey)
      ecdsa.update(randomData)
      ecdsa.update(ecdhPublicKeyBytes)
      ecdsa.update(additionalRandomData)
      ecdsa.update(sessionIdArray)
      ecdsa.update(peerRandomData)
      ecdsa.update(peerEcdhPublicKeyBytes)
      val dataLen = ByteArray(LEN_LENGTH)
      peerAuthData?.let { MessageCipher.setLength(dataLen, 0, it.size) }
      ecdsa.update(dataLen)
      ecdsa.update(peerAuthData)
      return ecdsa.verify(peerVerifyBytes)
    } catch (e: SignatureException) {
      clearSession()
      throw SecureException()
    } catch (e: InvalidKeyException) {
      clearSession()
      throw SecureException()
    }
  }

  @Throws(SecureException::class)
  fun verify(): List<List<Byte>> {
    val verify = buildVerify()
    apStartEncryption(CLIENT_AP_TRAFFIC, SERVER_AP_TRAFFIC)
    return verify.asIterable().chunked(packageSize - LEN_HEADER)
  }

  @Throws(SecureException::class)
  private fun buildVerify(): ByteArray {
    try {
      checkSignature()
      val message = ByteArray(
        3 * LEN_LENGTH + authData.size + verifyBytes.size + (helloHash?.size
          ?: 0) + LEN_AUTH_TAG
      )
      var offset = OFFSET_DATA
      MessageCipher.setLength(message, offset, authData.size)
      offset += LEN_LENGTH
      System.arraycopy(authData, 0, message, offset, authData.size)
      offset += authData.size
      MessageCipher.setLength(message, offset, verifyBytes.size)
      offset += LEN_LENGTH
      System.arraycopy(verifyBytes, 0, message, offset, verifyBytes.size)
      offset += verifyBytes.size
      helloVerifyHash?.let {
        MessageCipher.setLength(message, offset, it.size)
        offset += LEN_LENGTH
        System.arraycopy(it, 0, message, offset, it.size)
        offset += it.size
      }
      hsDigest.update(message, OFFSET_DATA, offset - OFFSET_DATA)
      val encryptedMessage =
        protector?.transform(message, message.size - LEN_AUTH_TAG)
      System.arraycopy(
        encryptedMessage ?: byteArrayOf(0, 0, 0, 0),
        0,
        message,
        OFFSET_DATA,
        message.size
      )
      return message
    } catch (e: Exception) {
      clearSession()
      throw SecureException()
    }
  }

  private fun checkSignature() {
    ecdsa.initSign(privateKey)
    ecdsa.update(randomData)
    ecdsa.update(ecdhPublicKeyBytes)
    ecdsa.update(additionalRandomData)
    ecdsa.update(sessionIdArray)
    ecdsa.update(peerRandomData)
    ecdsa.update(peerEcdhPublicKeyBytes)
    val dataLen = ByteArray(LEN_LENGTH)
    peerAuthData?.let { MessageCipher.setLength(dataLen, 0, it.size) }
    ecdsa.update(dataLen)
    ecdsa.update(peerAuthData)
    peerVerifyBytes?.let { MessageCipher.setLength(dataLen, 0, it.size) }
    ecdsa.update(dataLen)
    ecdsa.update(peerVerifyBytes)
    helloHash?.let { MessageCipher.setLength(dataLen, 0, it.size) }
    ecdsa.update(dataLen)
    ecdsa.update(helloHash)
    val authDataLen = ByteArray(2)
    MessageCipher.setLength(authDataLen, 0, authData.size)
    ecdsa.update(authDataLen)
    ecdsa.update(authData)
    verifyBytes = ecdsa.sign()
  }

  /**
   * Start application data encryption.
   *
   * @param label     Label for key diversification.
   * @param peerLabel Label for peer key diversification.
   * @throws SecureException
   */
  @Throws(SecureException::class)
  private fun apStartEncryption(label: ByteArray, peerLabel: ByteArray) {
    try {
      hsHash =
        hsDigest.digest() // hash from CLIENT HELLO / SERVER HELLO / SERVER VERIFY / CLIENT VERIFY
      protector = MessageCipher(sharedSecret, label, hsHash, Cipher.ENCRYPT_MODE)
      peerProtector = MessageCipher(sharedSecret, peerLabel, hsHash, Cipher.DECRYPT_MODE)
    } catch (e: Exception) {
      clearSession()
      throw SecureException()
    }
  }

  fun write(data: SecurityData) {
    if (!encryptor.hasSubscribers()) {
      setupEncryptor()
    }
    encryptor.onNext(data)
  }

  fun read(data: SecurityData) {
    if (!decryptor.hasSubscribers()) {
      setupDecryptor()
    }
    decryptor.onNext(data)
  }
}

data class SecurityData(
  val data: ByteArray,
  val callback: (ByteArray?) -> Unit,
  var newData: ByteArray? = null,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SecurityData

    if (!data.contentEquals(other.data)) return false
    if (callback != other.callback) return false
    if (newData != null) {
      if (other.newData == null) return false
      if (!newData.contentEquals(other.newData)) return false
    } else if (other.newData != null) return false

    return true
  }

  override fun hashCode(): Int {
    var result = data.contentHashCode()
    result = 31 * result + callback.hashCode()
    result = 31 * result + (newData?.contentHashCode() ?: 0)
    return result
  }
}
