import java.io.BufferedReader
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.thirutricks.tllplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thirutricks.tllplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = if (project.hasProperty("versionCodeOverride")) {
            project.property("versionCodeOverride").toString().toInt()
        } else {
            getVersionCode()
        }
        versionName = if (project.hasProperty("versionNameOverride")) {
            project.property("versionNameOverride").toString()
        } else {
            getVersionName()
        }
        println("Building with VersionCode: $versionCode, VersionName: $versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ABI split: arm64-v8a + armeabi-v7a for real TV hardware; x86_64 for emulators only.
    flavorDimensions += "abi"
    productFlavors {
        create("standard") {
            dimension = "abi"
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters += listOf("x86_64") }
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                keepRules {
                    files("proguard-rules.pro")
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["androidTest"].assets.directories.add("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

fun getVersionFromJSON(): Pair<Int, String>? {
    try {
        val jsonFile = rootProject.file("version.json")
        if (jsonFile.exists()) {
            val text = jsonFile.readText()
            val codeRegex = "\"version_code\"\\s*:\\s*(\\d+)".toRegex()
            val nameRegex = "\"version_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val codeMatch = codeRegex.find(text)
            val nameMatch = nameRegex.find(text)
            if (codeMatch != null && nameMatch != null) {
                val code = codeMatch.groupValues[1].toInt()
                val name = nameMatch.groupValues[1]
                return Pair(code, name)
            }
        }
    } catch (ignored: Exception) {
    }
    return null
}

fun getVersionCode(): Int {
    val fromJson = getVersionFromJSON()
    if (fromJson != null) return fromJson.first
    return try {
        val process = Runtime.getRuntime().exec("git describe --tags --always")
        process.waitFor()
        val arr = (process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
            .replace("v", "").replace(".", " ").replace("-", " ") + " 0").split(" ")
        val versionCode =
            arr[0].toInt() * 16777216 + arr[1].toInt() * 65536 + arr[2].toInt() * 256 + arr[3].toInt()
        versionCode
    } catch (ignored: Exception) {
        1
    }
}

fun getVersionName(): String {
    val fromJson = getVersionFromJSON()
    if (fromJson != null) return fromJson.second.removePrefix("v").removePrefix("V")
    return try {
        val process = Runtime.getRuntime().exec("git describe --tags --always")
        process.waitFor()
        val versionName = process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
            .removePrefix("v")
        versionName.ifEmpty {
            "1.0.0"
        }
    } catch (ignored: Exception) {
        "1.0.0"
    }
}

dependencies {
    // Core Compose and AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)

    // Compose for TV
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tvprovider)

    // Lifecycle / Navigation
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Preferences
    implementation(libs.androidx.datastore.preferences)

    // WorkManager (durable background sync)
    implementation(libs.androidx.work.runtime)

    // Baseline profile installer (sideloaded apps need this for bundled profiles)
    implementation(libs.androidx.profileinstaller)

    // Database (Room, via KSP) + Paging
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.zxing.core) // QR generation for companion add-source flow
    implementation(libs.juniversalchardet) // Local subtitle charset detection

    // Media playback — libmpv (FFmpeg) engine
    implementation(libs.libmpv)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.datasource.rtmp)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)

    // In-app YouTube trailer playback — WebView-backed IFrame player
    implementation(libs.youtube.player)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Dependency injection
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Firebase (from legacy app)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    // Legacy dependencies
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    val retrofit2Version = "2.11.0"
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:converter-gson:$retrofit2Version") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation("com.squareup.retrofit2:retrofit:$retrofit2Version")

    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("io.github.lizongying:gua64:1.4.5")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.webkit:webkit:1.11.0")

    // Debug tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Test dependencies
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("androidx.test.ext:junit-ktx:1.1.5")
    testImplementation("org.mockito:mockito-core:5.8.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

configurations.configureEach {
    resolutionStrategy {
        force("com.google.code.gson:gson:2.10.1")
    }
}