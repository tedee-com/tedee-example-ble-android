package com.tedee.flutter.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

object DataStoreManager {
  private val Context.certificateStore by preferencesDataStore("certificate_store")
  private val CERTIFICATE_KEY = stringPreferencesKey("certificate_id")
  private val DEVICE_PUBLIC_KEY = stringPreferencesKey("device_public_key")
  private val MOBILE_PUBLIC_KEY = stringPreferencesKey("mobile_public_key")

  suspend fun saveCertificateData(
    context: Context,
    certificate: String,
    devicePublicKey: String,
    mobilePublicKey: String,
  ) {
    context.certificateStore.edit { settings ->
      Timber.d("saveCertificate - $certificate, devicePublicKey = $devicePublicKey, mobilePublicKey = $mobilePublicKey")
      settings[CERTIFICATE_KEY] = certificate
      settings[DEVICE_PUBLIC_KEY] = devicePublicKey
      settings[MOBILE_PUBLIC_KEY] = mobilePublicKey
    }
  }

  suspend fun getCertificate(context: Context): String {
    return context.certificateStore.data
      .catch {
        emit(emptyPreferences())
      }
      .map { preferences ->
        preferences[CERTIFICATE_KEY].orEmpty()
      }.first()
  }

  suspend fun getDevicePublicKey(context: Context): String {
    return context.certificateStore.data
      .catch {
        emit(emptyPreferences())
      }
      .map { preferences ->
        preferences[DEVICE_PUBLIC_KEY].orEmpty()
      }.first()
  }

  suspend fun getMobilePublicKey(context: Context): String? {
    return context.certificateStore.data
      .catch {
        emit(emptyPreferences())
      }
      .map { preferences ->
        preferences[MOBILE_PUBLIC_KEY]
      }.first()
  }
}
