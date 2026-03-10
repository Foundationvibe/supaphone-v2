package com.supaphone.app.data

import com.supaphone.app.BuildConfig

object PublicLinks {
    private val websiteBaseUrl: String =
        BuildConfig.SUPAPHONE_WEBSITE_BASE_URL.trim().trimEnd('/')

    val privacyPolicyUrl: String
        get() = "$websiteBaseUrl/privacy.html"

    val termsUrl: String
        get() = "$websiteBaseUrl/terms.html"

    val supportUrl: String
        get() = "$websiteBaseUrl/support.html"
}
