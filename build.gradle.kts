// Every plugin is declared once here so Gradle loads it with a single classloader.
//
// This matters more than it looks. AGP 9 provides Kotlin support itself, and the
// Kotlin version a module compiles with is whichever Kotlin Gradle Plugin sits on
// the shared buildscript classpath. Without this block, modules that apply no
// Kotlin-versioned plugin silently fall back to the Kotlin bundled inside AGP,
// and any module that consumes their output fails to compile with a metadata
// version mismatch.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
