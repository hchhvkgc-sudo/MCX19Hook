plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.linyu.mcx19hook"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("proguard-rules.pro")
        ndkVersion = "30.0.16248370"

        externalNativeBuild {
            cmake {
                cppFlags.addAll(listOf(
                    "-std=c++20", "-O3", "-fvisibility=hidden",
                    "-DDOBBY_EXPORT", "-DDISABLE_LOGGING",
                    "-fstack-protector-strong", "-D_FORTIFY_SOURCE=2",
                    "-Wno-unused-parameter", "-Wno-write-strings"
                ))
                arguments(mapOf(
                    "ANDROID_STL" to "c++_shared",
                    "ANDROID_TOOLCHAIN" to "clang"
                ))
                abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "IS_DEBUG", "false")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            buildConfigField("boolean", "IS_DEBUG", "true")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(files("libs/lsposed-api-1.9.2.jar"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.google.guava:guava:33.3.1-android")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
