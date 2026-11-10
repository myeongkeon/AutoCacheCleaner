plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cachewipe.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cachewipe.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-mvp"

        // Real AdMob IDs live in ~/.gradle/gradle.properties (cachewipe.admob.*), never in the repo.
        // Without them Google's public test IDs are used, which only ever serve test ads.
        fun prop(name: String, testValue: String) = providers.gradleProperty(name).getOrElse(testValue)
        manifestPlaceholders["admobAppId"] = prop("cachewipe.admob.appId", "ca-app-pub-3940256099942544~3347511713")
        resValue("string", "ad_unit_interstitial", prop("cachewipe.admob.interstitial", "ca-app-pub-3940256099942544/1033173712"))
        resValue("string", "ad_unit_rewarded", prop("cachewipe.admob.rewarded", "ca-app-pub-3940256099942544/5354046379"))
    }

    // Upload key: set cachewipe.signing.* in ~/.gradle/gradle.properties (never in the repo).
    // Without them the release build is simply unsigned, so debug/test builds keep working.
    val signingStore = providers.gradleProperty("cachewipe.signing.storeFile").orNull
    signingConfigs {
        if (signingStore != null) {
            create("release") {
                storeFile = file(signingStore)
                storePassword = providers.gradleProperty("cachewipe.signing.storePassword").get()
                keyAlias = providers.gradleProperty("cachewipe.signing.keyAlias").get()
                keyPassword = providers.gradleProperty("cachewipe.signing.keyPassword").get()
            }
        }
    }

    buildTypes {
        debug { buildConfigField("boolean", "OPEN_BUILD", "false") }
        // Play Store build (ads + Pro purchase). Not published yet.
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "OPEN_BUILD", "false")
            if (signingStore != null) signingConfig = signingConfigs.getByName("release")
        }
        // GitHub distribution: no ads, no purchase, all features unlocked, no INTERNET permission
        // (src/open/AndroidManifest.xml strips the ad/billing SDK entries).
        create("open") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            buildConfigField("boolean", "OPEN_BUILD", "true")
            versionNameSuffix = "-open"
        }
        // Debuggable copy used as a cleaning target in device tests (cache files visible via run-as)
        create("probe") {
            initWith(getByName("debug"))
            buildConfigField("boolean", "OPEN_BUILD", "false")
            applicationIdSuffix = ".probe"
        }
    }
    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    testImplementation("junit:junit:4.13.2")
}
