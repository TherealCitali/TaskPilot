plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val taskPilotKeystoreFile = providers.environmentVariable("TASKPILOT_KEYSTORE_FILE").orNull
val taskPilotKeystorePassword = providers.environmentVariable("TASKPILOT_KEYSTORE_PASSWORD").orNull
val taskPilotKeyAlias = providers.environmentVariable("TASKPILOT_KEY_ALIAS").orNull
val taskPilotKeyPassword = providers.environmentVariable("TASKPILOT_KEY_PASSWORD").orNull
val hasTaskPilotSigning = listOf(
    taskPilotKeystoreFile,
    taskPilotKeystorePassword,
    taskPilotKeyAlias,
    taskPilotKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "dev.citali.taskpilot"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.citali.taskpilot"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("taskPilotRelease") {
            if (hasTaskPilotSigning) {
                storeFile = file(taskPilotKeystoreFile!!)
                storePassword = taskPilotKeystorePassword
                keyAlias = taskPilotKeyAlias
                keyPassword = taskPilotKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasTaskPilotSigning) {
                signingConfig = signingConfigs.getByName("taskPilotRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
