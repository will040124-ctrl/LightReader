import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val releaseSigningFile = rootProject.file("keystore.properties")
val productionSigning = Properties().apply { if(releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) } }

android {
    namespace = "com.will.lightreader"
    // Android 17 平台目录名为 android-37.0（小版本命名），用精确 hash string 匹配
    compileSdkVersion = "android-37.0"
    defaultConfig {
        applicationId = "com.will.lightreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 106
        versionName = "1.0.0"
        manifestPlaceholders["appLabel"]="@string/app_name"
    }
    signingConfigs {
        create("production") {
            if(releaseSigningFile.isFile) {
                storeFile=file(productionSigning.getProperty("storeFile"))
                storePassword=productionSigning.getProperty("storePassword")
                keyAlias=productionSigning.getProperty("keyAlias")
                keyPassword=productionSigning.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            manifestPlaceholders["appLabel"]="轻阅（旧版）"
            signingConfig=signingConfigs.getByName("debug")
            versionNameSuffix="-migration"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("stable") {
            initWith(getByName("release"))
            applicationIdSuffix=".stable"
            versionNameSuffix=""
            signingConfig=signingConfigs.getByName("production")
            matchingFallbacks += "release"
            manifestPlaceholders["appLabel"]="@string/app_name"
        }
        create("optimized") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-optimized"
            manifestPlaceholders["appLabel"] = "轻阅（优化版）"
            matchingFallbacks += "release"
        }
        create("benchmark") {
            initWith(getByName("release"))
            // Profile generation requires unobfuscated methods. Keep this build for that purpose.
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".benchmark"
            matchingFallbacks += "release"
        }
        create("releaseQa") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".releaseqa"
            matchingFallbacks += "release"
        }
        create("promoCapture") {
            initWith(getByName("benchmark"))
            applicationIdSuffix = ".promocapture"
            manifestPlaceholders["appLabel"] = "轻阅（宣传演示）"
            matchingFallbacks += "benchmark"
        }
    }
    sourceSets.getByName("releaseQa") {
        manifest.srcFile("src/benchmark/AndroidManifest.xml")
        java.srcDir("src/benchmark/java")
        res.srcDir("src/benchmark/res")
    }
    sourceSets.getByName("promoCapture") {
        manifest.srcFile("src/benchmark/AndroidManifest.xml")
        java.srcDir("src/benchmark/java")
        res.srcDir("src/benchmark/res")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

// Kotlin 2.3：kotlinOptions 已废弃，迁移到 compilerOptions DSL
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Room schema 导出（exportSchema=true）：生成到 schemas/ 供写 Migration 参考
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.javascriptengine:javascriptengine:1.0.0")
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // material3 1.4 不再自动带 icons-core（ChapterDrawer 用 Icons.Default.List）
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
