package tedee.mobile.demo.api.data.model

data class MobileRegistrationBody(
  var name: String,
  var operatingSystem: Int = OS_ANDROID,
  var publicKey: String,
) {

  companion object {
    private const val OS_ANDROID = 3
  }
}