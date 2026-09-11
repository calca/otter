plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    // Da Kotlin 2.0+, il Compose Compiler è distribuito come plugin Kotlin
    // (sostituisce composeOptions.kotlinCompilerExtensionVersion): richiesto
    // per usare Compose BOM 2025.12.00 / Material3 1.4 (M3 Expressive).
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
