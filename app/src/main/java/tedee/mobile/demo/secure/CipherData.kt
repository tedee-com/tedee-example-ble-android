package tedee.mobile.demo.secure

data class CipherData(
  var iv: ByteArray? = null,
  var ivCounterBase: ByteArray? = null,
  var counter: Int = 0,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CipherData

    if (iv != null) {
      if (other.iv == null) return false
      if (!iv!!.contentEquals(other.iv!!)) return false
    } else if (other.iv != null) return false
    if (ivCounterBase != null) {
      if (other.ivCounterBase == null) return false
      if (!ivCounterBase!!.contentEquals(other.ivCounterBase!!)) return false
    } else if (other.ivCounterBase != null) return false
    if (counter != other.counter) return false

    return true
  }

  override fun hashCode(): Int {
    var result = iv?.contentHashCode() ?: 0
    result = 31 * result + (ivCounterBase?.contentHashCode() ?: 0)
    result = 31 * result + counter
    return result
  }
}
