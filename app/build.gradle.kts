plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val apiBaseUrl = providers.gradleProperty("VPN_API_BASE_URL").orElse("").get()
val appApiKey = providers.gradleProperty("VPN_APP_API_KEY").orElse("").get()

android {
    namespace = "ir.omid.vpnman"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.omid.vpnman"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"

        // Most current Android phones are arm64. The AAR is also slimmed in prepare-deps.sh.
        ndk {
            abiFilters += "arm64-v8a"
        }

        buildConfigField("String", "VPN_API_BASE_URL", apiBaseUrl.asBuildConfigString())
        buildConfigField("String", "VPN_APP_API_KEY", appApiKey.asBuildConfigString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation(files("libs/libv2ray.aar"))
}
