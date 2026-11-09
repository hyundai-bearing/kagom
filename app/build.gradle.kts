plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** 서명 값: Gradle 속성이 있으면 그것, 없으면 환경 변수 (CI에서 넘기기 쉽게) */
fun signingValue(property: String, env: String): String? =
    providers.gradleProperty(property).orElse(providers.environmentVariable(env)).orNull

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
    signingConfigs {
        // 정식 키는 레포에 넣지 않는다: ~/.gradle/gradle.properties의 kagom.signing.* 또는 환경 변수 KAGOM_SIGNING_*로 넘긴다
        // (docs/PR_AND_DEPLOY.md 6장). 네 값이 다 있을 때만 만든다.
        val storeFile = signingValue("kagom.signing.storeFile", "KAGOM_SIGNING_STORE_FILE")
        val storePassword = signingValue("kagom.signing.storePassword", "KAGOM_SIGNING_STORE_PASSWORD")
        val keyAlias = signingValue("kagom.signing.keyAlias", "KAGOM_SIGNING_KEY_ALIAS")
        val keyPassword = signingValue("kagom.signing.keyPassword", "KAGOM_SIGNING_KEY_PASSWORD")
        if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
            create("release") {
                this.storeFile = file(storeFile)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }
    buildTypes {
        release {
            // 코드 축소(R8) + 안 쓰는 리소스 제거
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 정식 키가 없으면 디버그 키로 서명한다 (시연·내부 공유용, 앱마켓에는 못 올림)
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
