package com.supaphone.app.data

import com.supaphone.app.BuildConfig

object BackendConfig {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trim()
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()
    val edgeBaseUrl: String = BuildConfig.SUPAPHONE_EDGE_BASE_URL.trim().trimEnd('/')

    fun missingConfigKeys(): List<String> {
        val missing = mutableListOf<String>()
        if (supabaseUrl.isBlank()) {
            missing += "SUPABASE_URL"
        }
        if (supabaseAnonKey.isBlank()) {
            missing += "SUPABASE_ANON_KEY"
        }
        if (edgeBaseUrl.isBlank()) {
            missing += "SUPAPHONE_EDGE_BASE_URL"
        }
        return missing
    }

    fun isConfigured(): Boolean = missingConfigKeys().isEmpty()
}
