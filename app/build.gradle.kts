plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The Firebase config is per-developer and never committed. Without it the app
// still builds and shows a "connect your backend" screen at launch.
val firebaseConfig = file("google-services.json")
val hasFirebaseConfig = firebaseConfig.exists()
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Invite links are served by Firebase Hosting at https://<project-id>.web.app/i/<token>.
val firebaseProjectId: String? = if (hasFirebaseConfig) {
    @Suppress("UNCHECKED_CAST")
    val json = groovy.json.JsonSlurper().parse(firebaseConfig) as Map<String, Any?>
    (json["project_info"] as? Map<String, Any?>)?.get("project_id") as? String
} else {
    null
}
val inviteHost = firebaseProjectId?.let { "$it.web.app" } ?: "invites.meadow.invalid"

// Release builds take their version from the git tag (-Pmeadow.version=1.2.3), so the in-app
// updater can compare it with GitHub releases. versionCode must grow with every release.
val appVersion = (findProperty("meadow.version") as String?)?.removePrefix("v") ?: "0.1.0"
val appVersionCode = appVersion.substringBefore('-').split('.').map { it.toInt() }.let { parts ->
    parts.getOrElse(0) { 0 } * 1_000_000 + parts.getOrElse(1) { 0 } * 1_000 + parts.getOrElse(2) { 0 }
}

// Release signing comes from environment variables (CI secrets). Without them, release builds
// fall back to the debug key, which is fine for testing but can't update a published install.
val releaseKeystore = System.getenv("MEADOW_KEYSTORE_PATH")?.let(::file)?.takeIf { it.exists() }

android {
    namespace = "dev.pcoffline.meadow"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.pcoffline.meadow"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("boolean", "HAS_FIREBASE_CONFIG", hasFirebaseConfig.toString())
        buildConfigField("String", "UPDATE_REPO", "\"${findProperty("meadow.updateRepo") ?: "PCOffline/time-limit-app"}\"")
        manifestPlaceholders["inviteHost"] = inviteHost
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("MEADOW_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MEADOW_KEY_ALIAS")
                keyPassword = System.getenv("MEADOW_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // The privacy notice, terms and licences live in /legal, shared with the hosted web pages.
    sourceSets["main"].assets.srcDir("../legal")

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.konfetti.compose)
}
