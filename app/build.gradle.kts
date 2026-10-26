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
 * Numero di build, da cui derivano `versionCode` e il metadato di build di
 * `versionName` ("0.4.0+<numero>", vedi [otterVersionName]). Lo passa `.github/workflows/android.yml` come
 * `OTTER_BUILD_NUMBER`, valorizzato con `github.run_number`; in locale non
 * c'è e vale 1.
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
 *
 * Le stesse due cose si possono passare come proprietà Gradle
 * (`-PotterBuildNumber=176 -PotterVersionName=0.6.0`): la ricetta di F-Droid
 * (docs/fdroid/) compila da un tag senza la nostra CI e le usa per ottenere
 * lo stesso `versionCode` della build di Play. L'ambiente ha la precedenza.
 */
val otterBuildNumber: Int =
    (System.getenv("OTTER_BUILD_NUMBER") ?: providers.gradleProperty("otterBuildNumber").orNull)
        ?.toIntOrNull()?.takeIf { it > 0 } ?: 1

/**
 * Il nome di versione segue semver (vedi docs/store/README.md, "Versioni").
 * Su un tag di rilascio (`v0.5.0`) `android.yml` passa il nome del tag in
 * `OTTER_VERSION_NAME`, e la versione mostrata è "0.5.0". Il `versionCode`
 * resta il numero di run: è quello che Play pretende crescente, e un tag non
 * lo garantisce.
 *
 * Ogni altra build parte dall'ultimo tag e aggiunge il numero di build come
 * metadato semver: "0.4.0+171" = la 0.4.0 più le modifiche arrivate dopo.
 * Prima era "0.1.<numero>", che dopo la 0.4.0 sembrava una versione più
 * vecchia. Il tag si legge da git (in CI serve un checkout con i tag,
 * `fetch-depth: 0`); senza git o senza tag vale "0.0.0".
 */
val lastReleaseVersion: String = runCatching {
    providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*.[0-9]*.[0-9]*")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().removePrefix("v")
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.0.0"

val otterVersionName: String =
    (System.getenv("OTTER_VERSION_NAME") ?: providers.gradleProperty("otterVersionName").orNull)
        ?.removePrefix("v")?.takeIf { it.isNotBlank() }
        ?: "$lastReleaseVersion+$otterBuildNumber"

android {
    namespace = "com.calmotter.app"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.calmotter.app"
        minSdk = 26
        targetSdk = 37
        versionCode = otterBuildNumber
        versionName = otterVersionName
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
            // R8: Play segnalava "DEX code optimization below threshold —
            // Obfuscation 1%". Il mapping finisce dentro l'AAB
            // (BUNDLE-METADATA), Play lo usa da solo per deoffuscare i crash.
            isMinifyEnabled = true
            isShrinkResources = true
            // Senza KEYSTORE_PATH (build di F-Droid, che firma con la propria
            // chiave, o chi compila in locale) esce un APK non firmato invece
            // di fallire su un keystore che non c'è.
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
            // AID NFC di Tempo insieme: uno per flavor, vedi
            // app/src/beta/res/xml/apduservice.xml per il perché.
            buildConfigField("String", "NFC_AID", "\"F0010203040506\"")
            // Nessun suffisso: resta com.calmotter.app, l'applicationId già
            // in uso — questa è la build "reale", non quella di test.
        }
        create("beta") {
            dimension = "channel"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            buildConfigField("String", "NFC_AID", "\"F0010203040507\"")
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
            // Screenshot test (app/src/test/java/.../screenshots/): con
            // -Pscreenshots.record=true riscrivono i riferimenti invece di
            // confrontarli. I riferimenti sono un input dichiarato, così un
            // riferimento cambiato rimette in esecuzione i test invece di
            // lasciarli "up-to-date".
            all { test ->
                test.systemProperty("screenshots.record", project.findProperty("screenshots.record") ?: "false")
                test.systemProperty("store.graphics", project.findProperty("store.graphics") ?: "false")
                project.findProperty("a11y.fontscale")?.let { test.systemProperty("a11y.fontscale", it) }
                project.findProperty("a11y.report")?.let { test.systemProperty("a11y.report", it) }
                test.inputs.files(project.fileTree("src/test/screenshots"))
                    .withPropertyName("screenshotGoldens")
                    .optional()
            }
        }
    }
}

// Snapshot JSON dello schema Room per versione, in app/schemas/ (committato,
// vedi CalmOtterDatabase.kt) — richiesto da `exportSchema = true`. Deve
// essere una closure separata da `android { }`: `ksp` è un blocco top-level
// del suo stesso plugin, non un'opzione dentro `android`.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    // Deprecata dalla 1.1.0. La password ora la cifra KeystoreCipher; questa
    // serve solo a PasswordManager.migrateLegacy() per leggere il file
    // vecchio (EncryptedSharedPreferences) al primo avvio dopo
    // l'aggiornamento. Da togliere insieme a quella migrazione (TODO.md "4").
    implementation("androidx.security:security-crypto:1.1.0")
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
    implementation("androidx.activity:activity-compose:1.13.0")

    // Widget home screen in Compose (sostituisce RemoteViews/AppWidgetProvider)
    implementation("androidx.glance:glance-appwidget:1.2.0")

    // Pausa di gruppo: QR code (specs/group-pause/design.md). zxing:core è
    // puro Java (nessuna dipendenza Android/fotocamera) — usato sia per
    // generare il QR (QrCodeGenerator.kt) sia per decodificarlo dai
    // fotogrammi della fotocamera (GroupPauseJoinScreen.kt). CameraX invece
    // di ML Kit/zxing-android-embedded: niente Google Play Services, niente
    // Activity di scansione preconfezionata — l'anteprima resta una nostra
    // schermata Compose, coerente con lo stile del resto dell'app.
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")

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
