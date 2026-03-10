package com.supaphone.app.ads

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.supaphone.app.diagnostics.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AdConsentManager(activity: Activity) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

    private val _canRequestAds = MutableStateFlow(consentInformation.canRequestAds())
    val canRequestAds: StateFlow<Boolean> = _canRequestAds

    private val _privacyOptionsRequired = MutableStateFlow(
        consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    )
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired

    fun requestConsentUpdate(activity: Activity, onComplete: (Boolean) -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                updateState()
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        AppLog.w(
                            "ADS_CONSENT_FORM_FAIL",
                            "code=${formError.errorCode} message=${formError.message}"
                        )
                    }
                    updateState()
                    onComplete(_canRequestAds.value)
                }
            },
            { requestError ->
                AppLog.w(
                    "ADS_CONSENT_REFRESH_FAIL",
                    "code=${requestError.errorCode} message=${requestError.message}"
                )
                updateState()
                onComplete(_canRequestAds.value)
            }
        )
    }

    fun showPrivacyOptionsForm(activity: Activity, onComplete: (FormError?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                AppLog.w(
                    "ADS_PRIVACY_OPTIONS_FAIL",
                    "code=${error.errorCode} message=${error.message}"
                )
            }
            updateState()
            onComplete(error)
        }
    }

    private fun updateState() {
        _canRequestAds.value = consentInformation.canRequestAds()
        _privacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }
}
