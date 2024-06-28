package io.iskopasi.simplymotion.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import io.iskopasi.simplymotion.BuildConfig


class PreferencesManager(context: Application) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("${BuildConfig.APPLICATION_ID}_prefs", Context.MODE_PRIVATE)

    fun saveString(key: String, value: String) {
        sharedPreferences.edit().apply {
            putString(key, value)
            commit()
        }
    }

    fun getString(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    fun saveInt(key: String, value: Int) {
        sharedPreferences.edit().apply {
            putInt(key, value)
            commit()
        }
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    fun saveBool(key: String, value: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(key, value)
            commit()
        }
    }

    fun getBool(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    companion object {
        const val SENSO_KEY = "senso"
        const val IS_FRONT_KEY = "is_front"
        const val SHOW_DETECTION_KEY = "show_detect_key"
    }
}