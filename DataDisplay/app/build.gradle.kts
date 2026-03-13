plugins {
    alias(libs.plugins.android.application)
    // If you use Kotlin, also add:
    // alias(libs.plugins.kotlin.android)
    // id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.datadisplay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.datadisplay"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    androidResources {
        noCompress += "mp3"
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    // If Java only:
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    // If Kotlin:
    // kapt("com.github.bumptech.glide:compiler:4.16.0")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.media:media:1.6.0")


    implementation("com.davemorrissey.labs:subsampling-scale-image-view:3.10.0")

    implementation("com.squareup.picasso:picasso:2.8")

    implementation("io.github.chrisbanes:PhotoView:2.3.0")
    

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)


    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    implementation("com.davemorrissey.labs:subsampling-scale-image-view:3.10.0")

}