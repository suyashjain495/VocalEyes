plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
    id("io.objectbox")
    alias(libs.plugins.ksp)
    alias(libs.plugins.secrets)
}

android {
    namespace = "com.example.vocaleyesnew"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.vocaleyesnew"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Performance optimizations
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true
        
        // Native library optimizations
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
        
        // Render script support
        renderscriptTargetApi = 21
        renderscriptSupportModeEnabled = true
        
        // Enable resource optimization
        resourceConfigurations.addAll(listOf("en", "xxhdpi"))
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
        
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug") // Replace with release signing config for production
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Further optimizations for release builds
            packaging {
                resources {
                    excludes += setOf(
                        "DebugProbesKt.bin",
                        "kotlin-tooling-metadata.json",
                        "META-INF/AL2.0",
                        "META-INF/LGPL2.1",
                        "META-INF/LICENSE*",
                        "META-INF/NOTICE*",
                        "META-INF/*.kotlin_module",
                        "META-INF/versions/**"
                    )
                }
            }
            
            // Using vector drawables, no need for PNG optimization
            // Note: crunchPngs property is not available in this Gradle version
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    lint {
        abortOnError = true
    }

    packaging {
        jniLibs {
            pickFirsts.add("lib/arm64-v8a/libtensorflowlite_jni.so")
            pickFirsts.add("lib/armeabi-v7a/libtensorflowlite_jni.so")
            pickFirsts.add("lib/x86/libtensorflowlite_jni.so")
            pickFirsts.add("lib/x86_64/libtensorflowlite_jni.so")
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE",
                "META-INF/notice.txt",
                "META-INF/NOTICE",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "**/*.kotlin_module",
                "META-INF/*.version",
                "META-INF/proguard/**",
                "META-INF/com.android.tools/**"
            )
        }
    }
    
    // Use newer Android Gradle Plugin syntax for preventing compression
    androidResources {
        noCompress += listOf("tflite", "model", "pb", "lite")
    }
    
    // Bundle configuration for Play Store
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
}

secrets {
    // Optionally specify a different file name containing your secrets.
    // The plugin defaults to "local.properties"
    propertiesFileName = ".env"

    // A properties file containing default values for secrets, so that it will help avoid build errors if they are missing from local.properties.
    defaultPropertiesFileName = "local.defaults.properties"

    // Configure which keys should be ignored by the plugin by providing regular expressions.
    // "sdk.dir" is ignored by default.
    ignoreList.add("keyToIgnore") // Ignore the key "keyToIgnore"
    ignoreList.add("sdk.*")       // Ignore all keys starting with "sdk"
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose BOM and UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // ML Kit - using direct group:name syntax to avoid parsing issues  
    implementation("com.google.mlkit:common:18.10.0")
    implementation("com.google.mlkit:object-detection:17.0.1")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    
    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.camera2)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // TensorFlow Lite
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.androidx.exifinterface)
    
    // Dependency injection
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    
    // Database
    implementation(libs.objectbox.kotlin)
    implementation(libs.objectbox.android)
    kapt("io.objectbox:objectbox-processor:3.8.0")
    
    // MediaPipe
    implementation(libs.mediapipe.tasks.vision)
    
    // OpenCV for document detection (temporarily disabled for build)
    // implementation("org.opencv:opencv-android:4.8.0")
    
    // Image loading
    implementation(libs.coil.compose)
    
    // Fonts
    implementation(libs.androidx.ui.text.google.fonts)
    
    // AI/ML
    implementation(libs.gemini.generativeai)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
