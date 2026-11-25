import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val secretsDir = rootProject.file("secrets")
val serviceAccountFile = File(secretsDir, "service-account.json")
val configProps = Properties().apply {
    val f = File(secretsDir, "config.properties")
    if (f.exists()) f.inputStream().use { load(it) }
    else logger.warn("xc-tv-wall: secrets/config.properties missing — app will show 'Not configured'")
}
if (!serviceAccountFile.exists()) {
    logger.warn("xc-tv-wall: secrets/service-account.json missing — app will show 'Not configured'")
}
val keystoreProps = Properties().apply {
    val f = File(secretsDir, "keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "tech.xelerate.tvwall"
    compileSdk = 34

    defaultConfig {
        applicationId = "tech.xelerate.tvwall.player"
        minSdk = 25
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        buildConfigField("String", "DRIVE_FOLDER_ID", "\"${configProps.getProperty("driveFolderId", "")}\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = File(secretsDir, keystoreProps.getProperty("storeFile", "release.keystore"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }

    sourceSets {
        getByName("main") {
            if (serviceAccountFile.exists()) assets.srcDir(layout.buildDirectory.dir("generated/secretAssets"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += listOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST", "META-INF/*.md")
    }
}

val copySecretAssets by tasks.registering(Copy::class) {
    from(serviceAccountFile)
    into(layout.buildDirectory.dir("generated/secretAssets"))
}
tasks.named("preBuild") { if (serviceAccountFile.exists()) dependsOn(copySecretAssets) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.google.apis:google-api-services-drive:v3-rev20240914-2.0.0")
    implementation("com.google.api-client:google-api-client:2.7.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.27.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
