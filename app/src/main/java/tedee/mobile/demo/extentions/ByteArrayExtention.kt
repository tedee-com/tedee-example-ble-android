package tedee.mobile.demo.extentions

fun ByteArray.copyFromFirstByte(): ByteArray = this.copyOfRange(1, this.size)
fun ByteArray.print(): String = this.joinToString(" ") { String.format("%02X", it) }