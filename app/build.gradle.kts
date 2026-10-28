import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    jacoco
    alias(libs.plugins.android.application)
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
    val json = groovy.json.JsonSlurper().parse(firebaseConfig) as Map<*, *>
    (json["project_info"] as? Map<*, *>)?.get("project_id") as? String
} else {
    null
}
val inviteHost = firebaseProjectId?.let { "$it.web.app" } ?: "invites.meadow.invalid"
// Passwordless sign-in links arrive on the project's other default Hosting domain: Firebase only
// sends them through a custom domain if one is given, and a default domain can't be.
val authLinkHost = firebaseProjectId?.let { "$it.firebaseapp.com" } ?: "auth.meadow.invalid"

// Release builds take their version from the git tag (-Pmeadow.version=1.2.3), so the in-app
// updater can compare it with GitHub releases. versionCode must grow with every release.
val appVersion = (findProperty("meadow.version") as String?)?.removePrefix("v") ?: "0.1.0"
val appVersionCode = appVersion.substringBefore('-').split('.').map { it.toInt() }.let { parts ->
    val (major, minor, patch) = List(3) { parts.getOrElse(it) { 0 } }
    // Minor and patch get three digits each; at 1000 they'd run into the next part.
    require(minor < 1_000 && patch < 1_000) {
        "Version $appVersion: minor and patch must each be below 1000, " +
            "because versionCode is major * 1000000 + minor * 1000 + patch"
    }
    major * 1_000_000 + minor * 1_000 + patch
}

// The Cloud Functions region, set once in backend/functions/.env, which the backend reads too.
val functionsRegion = rootProject.file("backend/functions/.env").takeIf { it.exists() }?.readLines()
    ?.firstNotNullOfOrNull { line -> line.trim().takeIf { it.startsWith("MEADOW_REGION=") }?.substringAfter('=')?.trim()?.trim('"') }
    .let { requireNotNull(it) { "Set MEADOW_REGION in backend/functions/.env, e.g. MEADOW_REGION=europe-west1" } }
    .also { require(Regex("^[a-z]+-[a-z]+[0-9]+$").matches(it)) { "MEADOW_REGION in backend/functions/.env isn't a region: $it" } }

// Release signing comes from environment variables (CI secrets). Without them, release builds
// fall back to the debug key, which is fine for testing but can't update a published install.
val releaseKeystore = System.getenv("MEADOW_KEYSTORE_PATH")?.let(::file)?.takeIf { it.exists() }

android {
    namespace = "dev.pcoffline.meadow"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.pcoffline.meadow"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("boolean", "HAS_FIREBASE_CONFIG", hasFirebaseConfig.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        buildConfigField("String", "FUNCTIONS_REGION", "\"$functionsRegion\"")
        buildConfigField("String", "UPDATE_REPO", "\"${findProperty("meadow.updateRepo") ?: "PCOffline/meadow"}\"")
        manifestPlaceholders["inviteHost"] = inviteHost
        manifestPlaceholders["authLinkHost"] = authLinkHost
        buildConfigField("String", "LINK_HOST", "\"$inviteHost\"")
        buildConfigField("String", "AUTH_LINK_HOST", "\"$authLinkHost\"")
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
            // createDebugUnitTestCoverageReport measures the Robolectric tests; CI enforces a floor.
            enableUnitTestCoverage = true
            // -Pmeadow.emulatorHost=10.0.2.2 points debug builds at the Firebase emulators (end-to-end tests).
            buildConfigField("String", "EMULATOR_HOST", "\"${findProperty("meadow.emulatorHost") ?: ""}\"")
            // -Pmeadow.trace=true logs every request to Firebase and its answer (see docs/debugging.md).
            buildConfigField("boolean", "TRACE", "${findProperty("meadow.trace") == "true"}")
        }
        release {
            buildConfigField("String", "EMULATOR_HOST", "\"\"")
            // The trace includes people's data, so release builds never have it.
            buildConfigField("boolean", "TRACE", "false")
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
    sourceSets["main"].assets.directories.add("../legal")
    // Sample data and helpers used by both JVM (Robolectric) and on-device tests.
    // Kotlin sources, so they're added as Kotlin source dirs (AGP's built-in Kotlin).
    sourceSets["test"].kotlin.directories.add("src/sharedTest/java")
    sourceSets["androidTest"].kotlin.directories.add("src/sharedTest/java")
    // Made-up data for the screen gallery (debug builds only), which the tests draw on too.
    sourceSets["debug"].kotlin.directories.add("src/demo/java")
    sourceSets["testRelease"].kotlin.directories.add("src/demo/java")

    testCoverage {
        jacocoVersion = libs.versions.jacoco.get()
    }

    lint {
        // Lint always writes a text report (build/reports/lint-results-debug.txt); CI prints it.
        abortOnError = true
        checkDependencies = true
    }

    testOptions {
        // Robolectric tests see the merged resources and assets (strings, the legal documents).
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric loads a whole Android framework per test class.
            it.maxHeapSize = "3g"
            // Conscrypt (Robolectric's TLS) loads a native library; JDK 24+ warns unless allowed.
            it.jvmArgs("--enable-native-access=ALL-UNNAMED")
            // Robolectric loads app classes through its own class loader, which JaCoCo must follow.
            it.extensions.configure<JacocoTaskExtension> {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
            it.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
        // Each end-to-end test starts from a freshly cleared app.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.emoji2.bundled)
    implementation(libs.androidx.exifinterface)

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
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestUtil(libs.androidx.test.orchestrator)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.installations)
    implementation(libs.firebase.storage)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.coil.gif)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.konfetti.compose)
    implementation(libs.markdown.renderer.m3)
}
