import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

// 地址只从本机配置读取。缺省时应用显示配置说明，不回落到任意网站。
val erpBaseUrl = (localProperties.getProperty("erp.baseUrl") ?: "")
    .trim()
    .replace("\\", "\\\\")
    .replace("\"", "")

android {
    namespace = "com.zhubao.erp.shell"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.zhubao.erp"
        minSdk = 26
        targetSdk = 37
        // 仅在制作可安装发布包时递增 versionCode，并同步调整 versionName。
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "ERP_BASE_URL", "\"$erpBaseUrl\"")
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("ZHUBAO_RELEASE_STORE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("ZHUBAO_RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("ZHUBAO_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("ZHUBAO_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val releaseStore = System.getenv("ZHUBAO_RELEASE_STORE")
val releaseStorePassword = System.getenv("ZHUBAO_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("ZHUBAO_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ZHUBAO_RELEASE_KEY_PASSWORD")

tasks.configureEach {
    if (name == "assembleRelease" || name == "bundleRelease" || name == "packageRelease") {
        doFirst {
            if (releaseStore.isNullOrBlank() || releaseStorePassword.isNullOrBlank()
                || releaseKeyAlias.isNullOrBlank() || releaseKeyPassword.isNullOrBlank()
            ) {
                throw GradleException("正式签名密钥不在仓库内。请在仓库外设置 ZHUBAO_RELEASE_STORE、ZHUBAO_RELEASE_STORE_PASSWORD、ZHUBAO_RELEASE_KEY_ALIAS 和 ZHUBAO_RELEASE_KEY_PASSWORD。")
            }
            if (erpBaseUrl.isBlank() || erpBaseUrl.contains("example.com")) {
                throw GradleException("正式包不能使用空地址或示例地址。请在仓库外的 local.properties 设置真实 https erp.baseUrl。")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    testImplementation("junit:junit:4.13.2")
}
