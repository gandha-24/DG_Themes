plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

}
android {
    namespace = "com.example.dgtheme"
    compileSdk =36 // Update this to 36 or higher

    defaultConfig {
        applicationId = "com.example.dgtheme"
        minSdk = 24
        targetSdk = 36 // Update this to match compileSdk

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}