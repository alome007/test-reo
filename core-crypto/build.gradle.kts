plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    jvm("desktop")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "CoreCrypto"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
        }
        val jvmCommonTest by creating {
            dependsOn(commonTest.get())
        }
        val desktopMain by getting {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.lazysodium.java)
                implementation(libs.jna)
            }
        }
        val desktopTest by getting {
            dependsOn(jvmCommonTest)
        }
        androidMain {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.lazysodium.android)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "dev.uclip.crypto"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
