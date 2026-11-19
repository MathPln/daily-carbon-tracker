// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Non appliqué : sert uniquement à mettre KGP 2.4.0 sur le classpath (AGP 9 = Kotlin intégré)
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
