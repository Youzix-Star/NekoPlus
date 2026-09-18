import java.util.Properties

plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so the standalone
    // `org.jetbrains.kotlin.android` plugin must NOT be applied here.
    alias(libs.plugins.android.application)
    // The Compose compiler plugin still has to be applied explicitly.
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing material.
 *
 * Locally it is read from `keystore.properties` (git-ignored); on CI it comes from environment
 * variables backed by GitHub Actions secrets. When none of it is present the build still succeeds
 * and simply emits an unsigned APK.
 */
val signingProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String): String? = signingProperties.getProperty(key) ?: System.getenv(key)

val keystorePath = signingValue("KEYSTORE_PATH")
val keystorePassword = signingValue("KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("KEY_ALIAS")
val releaseKeyPassword = signingValue("KEY_PASSWORD")
val hasReleaseSigning =
    keystorePath != null && keystorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "love.miao.yun"
    compileSdk = 37

    defaultConfig {
        applicationId = "love.miao.yun"
        minSdk = 33
        targetSdk = 35
        versionCode = 107
        versionName = "2.0.3 Onboarding Preview 2"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystorePath!!)
                storePassword = keystorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8 strips the thousands of unused Material icons pulled in by
            // material-icons-extended, which is what keeps the release APK small.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // The guide's next/back animation chain is an AIDL service inside the provisioning module
        // (fan.provision.ProvisionAnimHelper binds it), and upstream builds that module with
        // `aidl = true` for exactly that reason.
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// `kotlin.compilerOptions.jvmTarget` is not set on purpose: with built-in Kotlin it already
// defaults to `android.compileOptions.targetCompatibility` (Java 21).

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material.icons)

    // The first-run guide is HyperCeiler's provisioning module, ported as-is, and that module is
    // written against Xiaomi's MIUI/Miuix framework jars (fan.miuix:*). HyperCeiler republishes
    // them on GitHub Packages; see settings.gradle.kts for the credentials they need.
    implementation(libs.miuix.legacy.appcompat)
    implementation(libs.miuix.legacy.animation)
    implementation(libs.miuix.legacy.folme)
    implementation(libs.miuix.legacy.core)
    implementation(libs.miuix.legacy.theme)
    implementation(libs.miuix.legacy.basewidget)
    implementation(libs.miuix.legacy.cardview)
    implementation(libs.miuix.legacy.recyclerview)
    implementation(libs.miuix.legacy.springback)
    implementation(libs.miuix.legacy.navigator)
    implementation(libs.miuix.legacy.nestedheader)
    implementation(libs.miuix.legacy.pickerwidget)
    implementation(libs.miuix.legacy.preference)
    implementation(libs.miuix.legacy.bottomsheet)
    // fan.transition.ActivityOptionsHelper, used for the start button's scale-up into page two.
    implementation(libs.miuix.legacy.transition)

    // The second UI engine is written against Material Design.
    implementation(libs.compose.material3)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    // The liquid-glass floating bottom bar is built on miuix-blur, which requires minSdk 33.
    implementation(libs.miuix.blur)

    implementation(libs.androidx.activity.compose)

    debugImplementation("androidx.compose.ui:ui-tooling")

    // The rule language and its engine are pure Kotlin, so they are tested on the JVM in CI
    // rather than by installing an APK and looking at it.
    testImplementation("junit:junit:4.13.2")
}

// CI is the only place these tests ever run. Gradle's default report for a failure is one line —
// "AssertionError at FooTest.kt:12" — which costs a full round trip through GitHub Actions to
// explain; the message itself is the whole diagnosis.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}
