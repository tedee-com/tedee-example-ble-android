package tedee.mobile.demo.bluetooth

interface BluetoothWrapperListener {
  fun onConnectionChanged(connected: Boolean, isSecure: Boolean)
  fun onIndicationChanged(message: ByteArray)
  fun onError(throwable: Throwable)
}