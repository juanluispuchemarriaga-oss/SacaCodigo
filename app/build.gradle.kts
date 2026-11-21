plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sacacodigo.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.sacacodigo.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/INDEX.LIST", "META-INF/versions/**", "**/module-info.class"
            )
        }
    }
}

dependencies {
    implementation("io.github.skylot:jadx-core:1.4.7")
    implementation("io.github.skylot:jadx-dex-input:1.4.7")
}
