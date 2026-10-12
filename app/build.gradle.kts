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

android {
    namespace = "dev.pcoffline.meadow"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.pcoffline.meadow"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("boolean", "HAS_FIREBASE_CONFIG", hasFirebaseConfig.toString())
        manifestPlaceholders["inviteHost"] = inviteHost
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
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

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.konfetti.compose)
}
