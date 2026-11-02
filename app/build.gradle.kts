plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.georecon.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.georecon.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Optional ArcGIS Location Platform key for Esri satellite: local.properties arcgis.apiKey=...
        val arcgisKey = rootProject.file("local.properties").takeIf { it.exists() }?.readLines()
            ?.firstOrNull { it.startsWith("arcgis.apiKey=") }?.substringAfter("=")?.trim() ?: ""
        buildConfigField("String", "ARCGIS_API_KEY", "\"$arcgisKey\"")
        // Optional api.data.gov key for Smithsonian Open Access images: local.properties si.apiKey=...
        val siKey = rootProject.file("local.properties").takeIf { it.exists() }?.readLines()
            ?.firstOrNull { it.startsWith("si.apiKey=") }?.substringAfter("=")?.trim() ?: ""
        buildConfigField("String", "SI_API_KEY", "\"$siKey\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("org.maplibre.gl:android-sdk:11.5.2")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.ar:core:1.45.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
