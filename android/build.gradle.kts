// Root build file — konfigurasi plugin bersama (apply false per modul).
// Catatan AGP9: modul Android memakai built-in Kotlin (tanpa plugin kotlin-android).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
