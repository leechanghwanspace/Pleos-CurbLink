plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.curblink.demo"
    compileSdk = 35
    // Pleos exposes AAOS car APIs on its boot classpath rather than an android.car shared library.
    useLibrary("android.car", false)
    defaultConfig {
        applicationId = "io.curblink.demo"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "0.4-pleos-connected-preview"
        testInstrumentationRunner = "io.curblink.demo.VehicleAppInstrumentation"
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

