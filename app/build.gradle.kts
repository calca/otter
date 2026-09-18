import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/**
 * Numero di build, da cui derivano `versionCode` e la patch di `versionName`
 * ("0.1.<numero>"). Lo passa `.github/workflows/android.yml` come
 * `OTTER_BUILD_NUMBER`, valorizzato con `github.run_number`; in locale non
 * c'è e vale 1, cioè esattamente i valori fissi di prima (`versionCode = 1`,
 * `versionName = "0.1.0"` diventa "0.1.1" — l'unica differenza visibile).
 *
 * Nasce da un problema concreto: due APK firmate uscite da due run diversi
 * si dichiaravano entrambe `0.1.0 (1)`, quindi dal telefono non c'era modo
 * di sapere quale delle due fosse installata — durante il debug di un bug
 * su S22 si è persa mezz'ora proprio su questo. Con `versionCode` crescente
 * un aggiornamento si installa anche sopra la build precedente senza
 * disinstallare, e la Play Store lo pretende comunque per ogni caricamento.
 *
 * Conseguenza da tenere a mente: una build locale vale sempre 1, quindi non
 * si installa *sopra* una build di CI (Android rifiuta il downgrade di
 * `versionCode`). Per sostituirla basta passare un numero più alto a mano:
 * `OTTER_BUILD_NUMBER=999 ./gradlew assembleBetaDebug`.
 *
 * **Variabile dedicata, non `GITHUB_RUN_NUMBER` diretta.** Quest'ultima
 * esiste in *qualunque* workflow: `ci.yml` (che compila solo debug) ha una
 * numerazione sua, più avanti di questa, e le sue build si sarebbero
 * dichiarate più recenti di quelle firmate. A timbrare la versione è solo il
 * workflow che produce artefatti installabili.
 */
val otterBuildNumber: Int =
    System.getenv("OTTER_BUILD_NUMBER")?.toIntOrNull()?.takeIf { it > 0 } ?: 1

android {
    namespace = "com.calmotter.app"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.calmotter.app"
        minSdk = 26
        targetSdk = 37
        versionCode = otterBuildNumber
        versionName = "0.1.$otterBuildNumber"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "debug.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // "beta" e "stable" possono convivere sullo stesso device (applicationId
    // diverso, vedi sotto) — così una build di test (Bluetooth/NFC dal vivo,
    // feature nuove) non costringe a disinstallare quella già in uso
    // quotidiano. "dev/staging/prod" non avrebbe senso qui: niente backend,
    // niente config remota da differenziare (vedi CLAUDE.md, "no network
    // calls") — l'unico vero bisogno è poter installare due build in
    // parallelo, non tre ambienti.
    flavorDimensions += "channel"
    productFlavors {
        create("stable") {
            dimension = "channel"
            // Nessun suffisso: resta com.calmotter.app, l'applicationId già
            // in uso — questa è la build "reale", non quella di test.
        }
        create("beta") {
            dimension = "channel"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            // Nome diverso in app/src/beta/res/values{,-en}/strings.xml
            // (sovrascrive solo app_name) — altrimenti due icone identiche
            // "Calm Otter" nel drawer sarebbero indistinguibili, vanificando
            // il motivo stesso di questo flavor.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = false
        compose = true
        buildConfig = true
    }

    // Il Compose Compiler è configurato dal plugin org.jetbrains.kotlin.plugin.compose
    // (Kotlin 2.0+): nessun composeOptions/kotlinCompilerExtensionVersion da
    // impostare qui, la versione del compiler segue quella di Kotlin.

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    // Storage cifrato per salt + hash della password (AES256 via Android Keystore)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Jetpack Compose. compose-bom-alpha porta material3 1.5.0-alpha28:
    // in material3 1.4.0 stabile MaterialExpressiveTheme e
    // MotionScheme.expressive()/standard() sono `internal` in Kotlin
    // (verificato decompilando il jar) — diventano pubblici solo da qui.
    // Nessuna release stabile di material3 le espone ancora: dipendenza
    // volutamente su canale alpha, può cambiare firma tra un aggiornamento
    // e l'altro — vedi ui/theme/CalmOtterTheme.kt.
    val composeBom = platform("androidx.compose:compose-bom-alpha:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Widget home screen in Compose (sostituisce RemoteViews/AppWidgetProvider)
    implementation("androidx.glance:glance-appwidget:1.2.0")

    // Pausa di gruppo: QR code (specs/group-pause/design.md). zxing:core è
    // puro Java (nessuna dipendenza Android/fotocamera) — usato sia per
    // generare il QR (QrCodeGenerator.kt) sia per decodificarlo dai
    // fotogrammi della fotocamera (GroupPauseJoinScreen.kt). CameraX invece
    // di ML Kit/zxing-android-embedded: niente Google Play Services, niente
    // Activity di scansione preconfezionata — l'anteprima resta una nostra
    // schermata Compose, coerente con lo stile del resto dell'app.
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.6.1")

    // Test di composizione su JVM (Robolectric, nessun dispositivo): servono
    // per l'unica invariante grafica di questa app che si è rotta più volte
    // da sola — l'otter deve stare **nello stesso punto** in Home e in
    // pausa, vedi OtterAnchoredScreen.kt e ui/screens/PersistentOtter.kt.
    // Il BOM va ripetuto qui: sopra è applicato a `implementation` e
    // `androidTestImplementation`, non alla configurazione dei test JVM.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
