import java.io.BufferedReader
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.thirutricks.tllplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.thirutricks.tllplayer"
        minSdk = 21
        targetSdk = 34
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
    }

    buildFeatures {
        viewBinding = true
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
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

fun getVersionCode(): Int {
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // 21:2.11.0 17:2.6.4
    val retrofit2Version = "2.6.4"
    // Gson 2.10.1 and older: API level 19
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:converter-gson:$retrofit2Version") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation("com.squareup.retrofit2:retrofit:$retrofit2Version")

    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("io.github.lizongying:gua64:1.4.5")

    implementation("org.nanohttpd:nanohttpd:2.3.1")

    implementation("com.google.zxing:core:3.5.3")

    implementation("androidx.leanback:leanback:1.0.0")

    val media3Version = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-datasource-rtmp:$media3Version")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("androidx.cardview:cardview:1.0.0")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.10.3")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.10")
    
    // Property-based testing
    testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
    testImplementation("io.kotest:kotest-property:5.7.2")
}

configurations.configureEach {
    resolutionStrategy {
        force("com.google.code.gson:gson:2.10.1")
    }
}