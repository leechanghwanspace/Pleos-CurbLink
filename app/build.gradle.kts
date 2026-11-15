import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from an uncommitted keystore.properties at the repository root
// (storeFile, storePassword, keyAlias, keyPassword). Without it, the release build is
// signed with the local debug key so it still installs for review; it is not an official signature.
val releaseKeystore = rootProject.file("keystore.properties")
val releaseSigning = Properties().apply { if (releaseKeystore.isFile) releaseKeystore.inputStream().use(::load) }

android {
    namespace = "io.curblink.demo"
    compileSdk = 35
    // Pleos exposes AAOS car APIs on its boot classpath rather than an android.car shared library.
    useLibrary("android.car", false)
    defaultConfig {
        applicationId = "io.curblink.demo"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "0.5.2-arrival-preview"
        testInstrumentationRunner = "io.curblink.demo.VehicleAppInstrumentation"
    }
    signingConfigs {
        if (releaseKeystore.isFile) create("release") {
            storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            // Car/NaviHelper APIs are resolved from the platform classpath; keep shrinking off.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true }
    sourceSets {
        getByName("test").java.srcDir("src/testSupport/java")
        getByName("androidTest").java.srcDir("src/testSupport/java")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    testImplementation("junit:junit:4.13.2")
}

