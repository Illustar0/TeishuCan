import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import dev.detekt.gradle.Detekt
import java.io.File

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

fun git(vararg args: String): String =
    providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()

fun gitCliff(vararg args: String): String {
    val isWindows = providers.systemProperty("os.name").get().startsWith("Windows")
    val executableName = if (isWindows) "git-cliff.exe" else "git-cliff"
    val pathEntries = providers.environmentVariable("PATH")
        .map { it.split(File.pathSeparatorChar) }
        .getOrElse(emptyList())

    // JVM 可能沿用旧的命令搜索路径，使用本次构建的 PATH 解析绝对路径。
    val executable = pathEntries.asSequence()
        .map { rootDir.resolve(it).resolve(executableName) }
        .firstOrNull { it.isFile && it.canExecute() }
        ?: throw GradleException("git-cliff was not found on Gradle's PATH. Install git-cliff and add it to PATH.")

    return providers.exec {
        commandLine(executable.absolutePath, *args)
        // 在子目录运行时 git-cliff 只统计改动过该目录的提交。
        workingDir = rootDir
    }.standardOutput.asText.get().trim()
}

val gitCommitCount = git("rev-list", "--count", "HEAD").toInt()

// 还没有 tag 时 --always 只输出 hash，这里当作根提交上有个 v0.0.0，补全成与有 tag 时相同的格式。
val gitDescription = git("describe", "--tags", "--long", "--dirty", "--always", "--match", "v[0-9]*")
    .let { if (it.startsWith("v")) it else "v0.0.0-$gitCommitCount-g$it" }

// 按 cliff.toml 的规则算出的下一个正式版；还没有 tag 时是 initial_tag。
val gitCliffBumpedTag = gitCliff("--bumped-version")

val gitDescriptionPattern = Regex("""(v\d+\.\d+\.\d+)-(\d+)-g([0-9a-f]+)(-dirty)?""")
val versionTagPattern = Regex("""v(\d+)\.(\d+)\.(\d+)""")
val versionOrder = compareBy<List<Int>>({ it[0] }, { it[1] }, { it[2] })

fun versionOf(tag: String): List<Int> =
    requireNotNull(versionTagPattern.matchEntire(tag)) { "Cannot parse version tag: $tag" }
        .destructured.toList().map(String::toInt)

/**
 * 把形如 `v0.2.0-3-gabc1234-dirty` 的 [description] 转成 SemVer 2.0.0 版本号。
 *
 * 正好位于 tag 上时就是 tag 本身，如 `0.2.0`。tag 之后第 N 个提交是 `0.3.0-dev.N+gabc1234`，
 * 其中 `0.3.0` 取 git-cliff 算出的 [bumpedTag]，并且至少是该 tag 的 patch + 1，
 * 所以它排在该 tag 之后、之后任何正式版之前。有未提交改动时在 build metadata 末尾追加 `.dirty`。
 */
fun semVerFromGitDescription(description: String, bumpedTag: String): String {
    val match = requireNotNull(gitDescriptionPattern.matchEntire(description)) {
        "Cannot parse `git describe` output: $description"
    }
    val (tag, distance, hash, dirtyMarker) = match.destructured
    val isDirty = dirtyMarker.isNotEmpty()
    val buildMetadata = if (isDirty) "g$hash.dirty" else "g$hash"
    val (major, minor, patch) = versionOf(tag)
    if (distance == "0") {
        return if (isDirty) "$major.$minor.$patch+$buildMetadata" else "$major.$minor.$patch"
    }
    // tag 之后只有不符合约定式提交的提交时，git-cliff 会原样返回该 tag。
    val next = maxOf(versionOf(bumpedTag), listOf(major, minor, patch + 1), versionOrder)
    return "${next.joinToString(".")}-dev.$distance+$buildMetadata"
}

android {
    namespace = "com.illustar0.teishucan"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.illustar0.teishucan"
        minSdk = 29
        targetSdk = 37
        // 偏移量给日后改写历史或更换方案留余地：调大它就能保证 versionCode 不倒退。
        versionCode = 1000 + gitCommitCount
        versionName = semVerFromGitDescription(gitDescription, gitCliffBumpedTag)
    }

    buildTypes {
        release {
            // Baseline Profile 插件只认这对旧开关，要靠它们派生出不混淆的 nonMinifiedRelease，
            // 所以这里不用 optimization {} DSL。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))

            // 签名用的环境变量只由 Release workflow 提供；本地没有时 release 包保持未签名。
            val keystoreFile = providers.environmentVariable("RELEASE_KEYSTORE_FILE")
            if (keystoreFile.isPresent) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(keystoreFile.get())
                    storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").get()
                    keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                    keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
                }
            }
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
    packaging {
        jniLibs {
            // JNA 自带 armeabi、mips 和 mips64 的库，但 minSdk 29 的设备不会是这些架构。
            excludes += listOf("lib/armeabi/**", "lib/mips/**", "lib/mips64/**")
        }
    }
    // 原生库占了 APK 的大头，按 ABI 拆分后每个包只带一种架构，universal 包留给不清楚自己架构的用户。
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
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
    // 改编自 Miuix 示例与 InstallerX-Revived，保持与上游接近以便同步，不按本项目的规则检查。
    exclude(
        "**/ui/component/animation/**",
        "**/ui/component/effect/**",
        "**/ui/component/liquid/**",
        "**/ui/component/predictiveback/**",
    )
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
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.firebase.analytics)
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
