package io.iskopasi.simplymotion.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import io.iskopasi.simplymotion.BuildConfig


class PreferencesManager(context: Application) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("${BuildConfig.APPLICATION_ID}_prefs", Context.MODE_PRIVATE)

    fun saveString(key: String, value: String) {
        val editor = sharedPreferences.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun getString(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    fun saveInt(key: String, value: Int) {
        val editor = sharedPreferences.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    companion object {
        val SENSO_KEY = "senso"
    }
}