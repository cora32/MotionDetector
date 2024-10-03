// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" // this version matches your Kotlin version

    id("com.google.dagger.hilt.android") version "2.51.1" apply false


    alias(libs.plugins.ksp) apply false
}