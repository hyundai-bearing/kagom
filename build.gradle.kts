plugins {
    // AGP 9는 Kotlin을 내장하므로 org.jetbrains.kotlin.android 플러그인은 쓰지 않는다
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
