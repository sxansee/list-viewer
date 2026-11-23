plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "app.fileviewer"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.fileviewer"
        minSdk = 24
        targetSdk = 34
        // The CI run number keeps versionCode growing, so every build installs over the previous one.
        // A release takes its name from the git tag (the workflow passes it as APP_VERSION, e.g. 2.3.0); other builds are "dev".
        val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = 100 + run
        versionName = System.getenv("APP_VERSION")?.takeIf { it.isNotBlank() } ?: "dev-$run"
    }
    // The key that signs the APK. By default it is the public key stored in the repository (app/debug.keystore), the same for the
    // debug and the release build, so every APK installs over the previous one and keeps the list. A key of your own is used
    // instead when the build is given one: LIST_KEYSTORE (the path of the keystore file) with LIST_KEYSTORE_PASSWORD, and
    // optionally LIST_KEY_ALIAS (default "list") and LIST_KEY_PASSWORD (default: the password of the keystore). The GitHub
    // workflow fills these from the repository secrets; see tools/New-ReleaseKey.ps1 and the README.
    val ownKeystore = System.getenv("LIST_KEYSTORE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (ownKeystore != null) {
            create("own") {
                storeFile = file(ownKeystore)
                val password = System.getenv("LIST_KEYSTORE_PASSWORD") ?: ""
                storePassword = password
                keyAlias = System.getenv("LIST_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "list"
                keyPassword = System.getenv("LIST_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: password
            }
        }
    }
    val signing = signingConfigs.getByName(if (ownKeystore != null) "own" else "debug")
    buildTypes {
        debug {
            // The plain build that the workflow falls back to must be signed with the same key as the optimised one.
            signingConfig = signing
        }
        release {
            // The optimised build. R8 removes the code that is never used, optimises the rest and shrinks the resources, and the
            // build is no longer "debuggable": this is what makes Compose, scrolling and opening files run smoothly on a phone.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
    }
    // Lint runs as its own job in CI and only reports; a finding never stops the build (and release builds are not checked twice).
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // The unit tests (app/src/test) check the pure logic: the list, backups, sorting, names, PDF search, audio arithmetic. Android classes they touch return empty values.
    testOptions { unitTests.isReturnDefaultValues = true }
    // Several libraries ship files with the same name (licences, notices, indexes). One copy of a licence or notice is kept;
    // the lists that only describe how a library was built are left out. Without this such a clash stops the build.
    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES", "/META-INF/INDEX.LIST")
            pickFirsts += setOf("/META-INF/LICENSE*", "/META-INF/NOTICE*")
        }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // The playback service: the media session that keeps music going with the screen off and gives it the notification and lock-screen controls.
    implementation("androidx.media3:media3-session:1.4.1")
    // The session's API speaks in Guava's futures, so the compiler has to be able to see them.
    implementation("com.google.guava:guava:31.1-android")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Text search inside PDF files (reads the text of each page; the pages themselves are still drawn by the system's PdfRenderer).
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Installs the startup profile that comes with the Compose libraries, so the release build starts and scrolls faster.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    testImplementation("junit:junit:4.13.2")
}
