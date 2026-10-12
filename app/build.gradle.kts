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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        buildConfigField("String", "UPDATE_REPO", "\"${findProperty("meadow.updateRepo") ?: "PCOffline/meadow"}\"")
        manifestPlaceholders["inviteHost"] = inviteHost
        // Passwordless sign-in links arrive on the same Hosting domain as invite links.
        buildConfigField("String", "LINK_HOST", "\"$inviteHost\"")
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
        debug {
            // -Pmeadow.emulatorHost=10.0.2.2 points debug builds at the Firebase emulators (end-to-end tests).
            buildConfigField("String", "EMULATOR_HOST", "\"${findProperty("meadow.emulatorHost") ?: ""}\"")
        }
        release {
            buildConfigField("String", "EMULATOR_HOST", "\"\"")
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
    // The privacy notice, terms and licences live in /legal, shared with the hosted web pages.
    sourceSets["main"].assets.srcDir("../legal")
    // Sample data and helpers used by both JVM (Robolectric) and on-device tests.
    sourceSets["test"].java.srcDir("src/sharedTest/java")
    sourceSets["androidTest"].java.srcDir("src/sharedTest/java")

    testOptions {
        // Robolectric tests see the merged resources and assets (strings, the legal documents).
        unitTests.isIncludeAndroidResources = true
        // Each end-to-end test starts from a freshly cleared app.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
    testImplementation(libs.robolectric)
    testImplementation(libs.jetbrains.markdown)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestUtil(libs.androidx.test.orchestrator)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.konfetti.compose)
    implementation(libs.markdown.renderer.m3)
}
