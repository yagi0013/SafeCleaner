package jp.yagi.safecleaner.util

import android.util.Log
import jp.yagi.safecleaner.BuildConfig

object AppLog {
    private const val TAG = "SafeCleaner"

    fun d(message: String) {
        if (BuildConfig.ENABLE_VERBOSE_LOGS) Log.d(TAG, message)
    }

    fun i(message: String) = Log.i(TAG, message)
    fun w(message: String, throwable: Throwable? = null) = Log.w(TAG, message, throwable)
    fun e(message: String, throwable: Throwable? = null) = Log.e(TAG, message, throwable)
}
