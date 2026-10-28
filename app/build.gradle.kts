plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ttaak.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ttaak.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.5.0"
    }

    // 기본판은 파일로 설치되고, 알림판(다른 앱 알림 보기)은 Play 프로텍트가 파일 설치를 막아 컴퓨터(adb)로 설치합니다.
    flavorDimensions += "noti"
    productFlavors {
        create("basic") { dimension = "noti" }
        create("noti") {
            dimension = "noti"
            versionNameSuffix = "-noti"
        }
    }

    // Play 업로드 키: 저장소 밖(~/.gradle/gradle.properties 의 ttaakUploadStore·ttaakUploadPassword)에만 있습니다.
    // `-Pplay` 로 빌드할 때만 씁니다(Play 내부 테스트용 AAB). 깃허브 APK 는 그대로 debug 키(기존 설치본 위에 덮어 설치되게).
    val play = providers.gradleProperty("play").isPresent
    if (play) {
        signingConfigs.create("upload") {
            storeFile = file(providers.gradleProperty("ttaakUploadStore").get())
            storePassword = providers.gradleProperty("ttaakUploadPassword").get()
            keyAlias = "upload"
            keyPassword = providers.gradleProperty("ttaakUploadPassword").get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (play) "upload" else "debug")
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
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
}
