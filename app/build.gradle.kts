import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import dev.detekt.gradle.Detekt

plugins {
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.detekt)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.illustar0.teishucan"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.illustar0.teishucan"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Baseline Profile 插件只认这对旧开关，要靠它们派生出不混淆的 nonMinifiedRelease，
            // 所以这里不用 optimization {} DSL。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    androidResources {
        generateLocaleConfig = true
        // 默认 values 是日语，只保留依赖库中与之对应的日语和中文翻译。
        localeFilters += listOf("ja", "zh")
    }
    // 不上架 Google Play，不需要只有 Play 能解密的依赖清单。
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

aboutLibraries {
    library {
        // 同一项目拆出的多个构件（如 androidx.compose.*）按 groupId 与名称合并为一条。
        duplicationRule = DuplicateRule.SIMPLE
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    // 改编自 Miuix 示例，保持与上游接近以便同步，不按本项目的规则检查。
    exclude("**/ui/component/animation/**", "**/ui/component/effect/**", "**/ui/component/liquid/**")
}

dependencies {
    baselineProfile(project(":baselineprofile"))
    detektPlugins(libs.compose.rules.detekt)
    implementation(libs.aboutlibraries.core)
    implementation(libs.zzu)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.firebase.crashlytics)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.ui)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
