plugins {
    // AGP 9.4.0 + Gradle 9.7.0: richiesti da compose-bom-alpha (material3
    // 1.5.0-alpha28 compila contro API 37 e richiede AGP >= 9.1.0).
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    // Da Kotlin 2.0+, il Compose Compiler è distribuito come plugin Kotlin
    // (sostituisce composeOptions.kotlinCompilerExtensionVersion): richiesto
    // per usare Compose BOM 2025.12.00 / Material3 1.4 (M3 Expressive).
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
