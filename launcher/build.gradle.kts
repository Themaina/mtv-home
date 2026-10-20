import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing key lives outside the repo (~/.mtv/keystore.properties) so it is never committed.
// Without it, release builds fall back to the debug key, which is fine for local testing.
val keystoreProps = Properties().apply {
    val f = file("${System.getProperty("user.home")}/.mtv/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.mtv.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mtv.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.0"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Home screen is always running on a 1 GB TV: ship the shrunk build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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

    testOptions {
        unitTests.isReturnDefaultValues = true // android.util.Log etc. become no-ops in JVM tests
        unitTests.all { test ->
            // Pass -Plive to run the test that fetches the real feeds.
            test.systemProperty("live", (project.findProperty("live") != null).toString())
            test.testLogging { showStandardStreams = true }
        }
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0") // XmlPullParser implementation for JVM tests
    testImplementation("org.json:json:20240303") // real org.json on the JVM (Android's is a stub there)
}
