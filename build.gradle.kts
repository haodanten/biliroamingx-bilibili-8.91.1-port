plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.revanced.biliroaming.manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.revanced.biliroaming.manager"
        minSdk = 26
        targetSdk = 34
        versionName = project.version.toString()
        versionCode = 1
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        getByName("release") {
            // 必须显式指定 signingConfig: AGP 只会把 debug 签名自动挂到 debug 变体上,
            // release 变体的 signingConfig 默认为 null, 产物会变成 *-unsigned.apk,
            // 用户下载后 INSTALL_PARSE_FAILED_NO_CERTIFICATES 装不上。
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
