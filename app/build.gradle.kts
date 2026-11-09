plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.roro"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.roro"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }
    buildTypes {
        release {
            // 코드 축소(R8) + 안 쓰는 리소스 제거
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 시연·측정용으로 디버그 키 서명 (배포 때는 정식 키로 교체)
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // Android 차량 API (CarUxRestrictionsManager: 기어 신호가 없을 때 주행 여부). 기기 시스템 라이브러리라 APK에 들어가지 않음
    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Pleos Playground SDK: 차량 신호(SEN-01~07·주행거리·기어), 위치(랜드마크 방문)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
}
