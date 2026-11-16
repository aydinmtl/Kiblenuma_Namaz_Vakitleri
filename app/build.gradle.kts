plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub Actions her derlemede çalıştırma numarasını verir; böylece her yeni APK
// bir öncekinin üzerine güncelleme olarak kurulabilir.
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

// Play Store yükleme anahtarı. Yalnızca GitHub Actions'ta, Secrets tanımlıysa ve
// "./gradlew bundleRelease -PplayUpload" komutuyla derlenirken kullanılır.
// Anahtar dosyası ve şifreler depoda DEĞİL, GitHub Secrets'ta durur.
val uploadKeystore = System.getenv("UPLOAD_KEYSTORE_PATH")
val usePlayUpload = project.hasProperty("playUpload") && !uploadKeystore.isNullOrEmpty()

android {
    namespace = "com.aydinmtl.namazvakitleri"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aydinmtl.kiblenuma"
        minSdk = 26
        targetSdk = 36
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    signingConfigs {
        if (usePlayUpload) {
            create("upload") {
                storeFile = file(uploadKeystore!!)
                storePassword = System.getenv("UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Elden kurulan APK'nın GERÇEK imzası burada atılmaz: Gradle APK'yı yalnızca bu bilgisayara
            // özgü deneme anahtarıyla ("debug") imzalar; GitHub Actions bu imzayı silip gizli anahtarla
            // yeniden imzalar (bkz. .github/workflows/build.yml, "APK'yı gizli anahtarla imzala").
            // Gizli anahtar depoda DEĞİL, GitHub Secrets'tadır. Bu yüzden başka bir bilgisayarda
            // derlenen APK, telefondaki Kıblenüma'nın üzerine güncelleme olarak kurulamaz.
            // Play'e gidecek AAB ise yükleme anahtarıyla imzalanır.
            signingConfig = signingConfigs.getByName(if (usePlayUpload) "upload" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Android Lint: kodu ve kaynakları (çeviriler, tasarımlar, manifest) bilinen hata kalıplarına
    // karşı tarar. CI'da "./gradlew lintDebug" ile çalışır (bkz. .github/workflows/build.yml).
    lint {
        // Lint'in "hata" dediği bulgular derlemeyi durdurur; "uyarı"lar durdurmaz, raporda görünür.
        abortOnError = true
        warningsAsErrors = false
        // Release derlemesinde ayrıca çalıştırma: aynı tarama CI'da ayrı bir adımdır.
        checkReleaseBuilds = false
        htmlReport = true
        xmlReport = true
        textReport = true
    }

    testOptions {
        // Birim testleri bilgisayarda (JVM) çalışır, telefonda değil. Android'in kendi
        // fonksiyonları orada yoktur; yanlışlıkla çağrılırsa hata yerine boş değer dönsün.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.work:work-runtime:2.9.1")

    // Kamera ile (AR) kıble bulucu için CameraX
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")

    // Yalnızca birim testleri için (APK'ya girmez):
    // JUnit: test çatısı. org.json: Android'deki JSON sınıflarının bilgisayarda çalışan sürümü.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
