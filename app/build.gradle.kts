import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Ключ подписи берётся из keystore.properties (в git не попадает).
// Обновления ставятся поверх только при подписи ТЕМ ЖЕ ключом — храните его.
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.l1ttlehe11.netwatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.l1ttlehe11.netwatch"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "2.7"
        // universal: в APK попадают нативные библиотеки под все архитектуры
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
    }

    signingConfigs {
        if (ksProps.getProperty("storeFile") != null) create("release") {
            storeFile = rootProject.file(ksProps.getProperty("storeFile"))
            storePassword = ksProps.getProperty("storePassword")
            keyAlias = ksProps.getProperty("keyAlias")
            keyPassword = ksProps.getProperty("keyPassword")
        }
    }

    // full — GitHub / сайт, со встроенным обновлением; fdroid — для F-Droid: без обновления и без REQUEST_INSTALL_PACKAGES
    flavorDimensions += "dist"
    productFlavors {
        create("full") { dimension = "dist"; buildConfigField("boolean", "UPDATER", "true") }
        create("fdroid") { dimension = "dist"; buildConfigField("boolean", "UPDATER", "false") }
    }

    // F-Droid: без зашифрованного блока со списком зависимостей от Google
    dependenciesInfo { includeInApk = false; includeInBundle = false }

    buildTypes {
        release {
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
    // один universal APK без разбиения по ABI
    splits { abi { isEnable = false } }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    // скриншоты экранов на JVM (без телефона)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
