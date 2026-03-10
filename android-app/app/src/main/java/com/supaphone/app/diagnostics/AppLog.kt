package com.supaphone.app.diagnostics

import com.supaphone.app.BuildConfig
import android.util.Log

object AppLog {
    const val TAG = "SupaPhoneFlow"
    private const val PREFIX = "SPH"
    private const val MAX_DETAIL_LENGTH = 180

    fun i(phrase: String, detail: String = "") {
        if (!BuildConfig.DEBUG) {
            return
        }
        Log.i(TAG, format(phrase, detail))
    }

    fun d(phrase: String, detail: String = "") {
        if (!BuildConfig.DEBUG) {
            return
        }
        Log.d(TAG, format(phrase, detail))
    }

    fun w(phrase: String, detail: String = "") {
        if (!BuildConfig.DEBUG) {
            return
        }
        Log.w(TAG, format(phrase, detail))
    }

    fun e(phrase: String, detail: String = "", error: Throwable? = null) {
        if (!BuildConfig.DEBUG) {
            Log.e(TAG, format(phrase, ""))
            return
        }
        if (error == null) {
            Log.e(TAG, format(phrase, detail))
            return
        }
        Log.e(TAG, format(phrase, detail), error)
    }

    private fun format(phrase: String, detail: String): String {
        val code = phrase.trim().ifEmpty { "UNKNOWN" }
        val info = sanitize(detail)
        return if (info.isEmpty()) {
            "$PREFIX|$code"
        } else {
            "$PREFIX|$code|$info"
        }
    }

    private fun sanitize(value: String): String {
        val compact = value.replace(Regex("\\s+"), " ").trim()
        if (compact.length <= MAX_DETAIL_LENGTH) {
            return compact
        }
        return compact.take(MAX_DETAIL_LENGTH - 3) + "..."
    }
}
