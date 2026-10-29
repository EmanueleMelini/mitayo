import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * MAL client ID and release signing: from environment variables in CI (GitHub Actions secrets)
 * or from local.properties on this machine. Never in the repository.
 */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localValue(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: localProperties.getProperty(property)?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Client ID of the MAL API client (myanimelist.net/apiconfig, type "android": no secret).
 * When missing a debug build still works, and the login screen says it's missing.
 */
val malClientId: String = localValue("MITAYO_MAL_CLIENT_ID", "mal.client.id").orEmpty()

/**
 * Client ID of the AniList API client (anilist.co/settings/developer, redirect mitayo://anilist;
 * the implicit grant needs no secret). Optional in every build: without it the AniList link is
 * off and hidden, which is also how it's switched off again in a release.
 */
val anilistClientId: String = localValue("MITAYO_ANILIST_CLIENT_ID", "anilist.client.id").orEmpty()

val releaseKeystore: String? = localValue("MITAYO_KEYSTORE_PATH", "mitayo.keystore.path")
if (releaseKeystore != null && malClientId.isEmpty()) {
    throw GradleException("Signed builds need mal.client.id in local.properties (or MITAYO_MAL_CLIENT_ID)")
}

/**
 * Firebase (statistics, crash reports, news): app/google-services.json from the Firebase console,
 * kept out of the repository; in CI it comes from the MITAYO_GOOGLE_SERVICES_JSON secret. Without
 * it the app builds and works, with Firebase off: forks and contributors don't need a project.
 */
val firebaseConfigured: Boolean = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else if (releaseKeystore != null) {
    throw GradleException("Signed builds need app/google-services.json (from the Firebase console)")
}

/** Site with the presentation, the downloads and the privacy policy (Cloudflare Pages, site/). Public. */
val siteHost: String = providers.gradleProperty("mitayo.site.host").get()

android {
    namespace = "it.emanuelemelini.mitayo"
    compileSdk = 37

    defaultConfig {
        applicationId = "it.emanuelemelini.mitayo"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "1.3.0"

        buildConfigField("String", "MAL_CLIENT_ID", "\"$malClientId\"")
        buildConfigField("String", "ANILIST_CLIENT_ID", "\"$anilistClientId\"")
        buildConfigField("String", "SITE_URL", "\"https://$siteHost\"")
        buildConfigField("boolean", "FIREBASE", "$firebaseConfigured")
    }

    androidResources {
        // Declares the supported languages to the system ("App language" on Android 13+)
        generateLocaleConfig = true
    }

    // The app language can change inside the app: an App Bundle must keep every language in the APK
    bundle {
        language {
            enableSplit = false
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = localValue("MITAYO_KEYSTORE_PASSWORD", "mitayo.keystore.password")
                keyAlias = localValue("MITAYO_KEY_ALIAS", "mitayo.key.alias")
                // Usually the same password as the keystore
                keyPassword = localValue("MITAYO_KEY_PASSWORD", "mitayo.key.password") ?: storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Unsigned release APK when no keystore is configured (it can't be installed)
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    // Exported schemas (committed) document every database version and back the migrations
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Only for the in-app language before Android 13 (AppCompatDelegate.setApplicationLocales)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Used only when google-services.json is there (BuildConfig.FIREBASE)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
}
