-keep class tedee.mobile.sdk.ble.** { *; }
-keep interface tedee.mobile.sdk.ble.** { *; }
-keep class com.polidea.rxandroidble2.** { *; }
-keep interface com.polidea.rxandroidble2.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep default impls for Kotlin interfaces
-keep class **$DefaultImpls { *; }
