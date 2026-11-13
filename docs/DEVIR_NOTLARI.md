# Kıblenüma — Geliştirici devir notları

Bu dosya projeye yeni katılan geliştirici (insan ya da yapay zekâ) içindir. Önce bunu, sonra
`PLAY_STORE.md` ve `YAPILACAKLAR.md` dosyalarını oku.

## Proje özeti

- Reklamsız, ücretsiz, kişisel veri toplamayan Android namaz vakti uygulaması.
- Sahibi: Aydın (1. sınıf bilgisayar öğrencisi, C ve Linux biliyor, Kotlin/Android'de yeni).
  **Kod okunur ve Türkçe yorumlu kalmalı**; her değişiklik sade Türkçeyle açıklanmalı.
- Arayüz dili: Türkçe (varsayılan), Fransızca, İngilizce, Almanca. Ana ekranın sağ üstündeki dil
  düğmesiyle (TR/FR/EN/DE) seçilir. Yorumlar: Türkçe. Ayrıntı: aşağıdaki "Diller" bölümü.
- Mağaza başlığı (kesin): **Kıblenüma: Namaz Vakitleri**. Yeni uygulama/modül adları Osmanlıca seçilir.

## Teknik yapı

| Konu | Değer |
|---|---|
| Dil / UI | Kotlin, platform View'ları (XML layout). Compose YOK. |
| Bağımlılıklar | Yalnızca `androidx.activity` + CameraX (AR kıble için). Gerisi Android platformu. |
| SDK | minSdk 26, compileSdk/targetSdk 35 |
| Araçlar | AGP 8.7.3, Kotlin 2.0.21, Gradle 8.14.3 (wrapper depoda) |
| Paket | namespace `com.aydinmtl.namazvakitleri`, applicationId `com.aydinmtl.kiblenuma` |
| Derleme | `.github/workflows/build.yml`: her PR'da ve `main`'e her push'ta birim testleri + `assembleRelease`, APK "artifact" olarak indirilebilir. Yalnızca `main` push'unda GitHub Release `v1.0.<run>` (`Kiblenuma.apk`) |
| İmza | `app/debug.keystore` yalnızca elden kurulum içindir; Play için ayrı upload key gerekecek |
| Lint | `abortOnError=false`, `checkReleaseBuilds=false` (bilinçli olarak gevşek; sıkılaştırılabilir) |
| Test | `app/src/test/`: JVM birim testleri (`./gradlew testDebugUnitTest`), yalnızca JUnit + org.json (test için) |
| Kod denetimi | `.github/workflows/denetim.yml`: her PR'da ve `main`'e her push'ta **ktlint** (Kotlin kod düzeni, kurallar `.editorconfig`'te) ve **gitleaks** (şifre/anahtar sızıntısı taraması, bütün geçmiş dahil). İkisi de yalnızca GitHub Actions'ta çalışır; Gradle'a, uygulamaya ve APK'ya hiçbir şey eklemez. Ayrıntı aşağıda |

Bulut çalışma ortamında Google Maven / Android SDK sunucularına erişim yok: **derleme yalnızca
GitHub Actions'ta** yapılabiliyor. Ortamdan ezanvakti.emushaf.net, Nominatim ve Overpass'a da
erişilemiyor; bunları kullanan kod telefonda denenmeli.

## Dosyalar ve görevleri (`app/src/main/java/com/aydinmtl/namazvakitleri/`)

| Dosya | Görevi |
|---|---|
| `Model.kt` | `PrayerDay`, `Moment`, `Prayers` (sıradaki/şu anki vakit), `norm()` (isim sadeleştirme), biçimlendirme |
| `Diyanet.kt` | ezanvakti.emushaf.net: ülke/şehir/ilçe listeleri, 30 günlük vakit JSON'u, `label()` |
| `Store.kt` | SharedPreferences: yer, ayarlar, Diyanet önbelleği, yarışma oturumu, ağaç puanı, hedefler, kaza, zikir |
| `Times.kt` | **Vakitlerin tek kaynağı.** Diyanet seçiliyse indirilen liste, değilse `PrayerCalc` ile hesap |
| `PrayerCalc.kt` | `CalcMethod` (Diyanet, MWL, ISNA, Mısır, Ümmü'l-Kurâ, Karaçi, Kuveyt, Katar, Singapur, UOIF) + astronomik hesap |
| `Hijri.kt` | ICU Ümmü'l-Kurâ takvimi, Diyanet'e göre gün kaydırma, dini günler listesi |
| `PlaceMatch.kt` | Yer adlarını Diyanet listesiyle eşleştirme (saf mantık, testli) |
| `AutoLocation.kt` | Otomatik konum. Sırayla: 1) yer adını Diyanet listesinde ara, 2) çevredeki yerleşimlerin adlarını ara (2-24 km halkalar, ülke sınırına bakmaz), 3) kısa listelerde en yakın yer (Geocoder ile ileri arama, önbellekli), 4) hiçbiri olmazsa vakitleri konumdan hesapla ("hesaplanan yer", `Store.COMPUTED_ID`). Geocoder yoksa Nominatim |
| `GeoMath.kt` | Mesafe ve "şu yöne şu kadar git" hesabı (saf mantık, testli) |
| `LocationHelper.kt` | Tek seferlik konum (fused/network/gps), zaman aşımlı |
| `LocationConsent.kt` | Konum izni öncesi gerekçe penceresi |
| `AlarmScheduler.kt` | Yalnızca SIRADAKİ olay için alarm (vakit girişi veya X dk önce hatırlatma) |
| `Receivers.kt` | `PrayerAlarmReceiver` (bildirim, ezan, widget, sonraki alarm), `BootReceiver` |
| `Notifications.kt` | 4 kanal: vakit, vakit (sessiz), hatırlatma, ezan |
| `AdhanService.kt` | Kullanıcının seçtiği ses dosyasını ön plan servisiyle çalar |
| `PrayerWidget.kt` | 4x2 ve 4x1 widget'lar (`PrayerWidgetSmall`), ↻ yenile düğmesi |
| `RefreshActivity.kt` | Widget'taki ve ana ekrandaki ↻ düğmesinin açtığı küçük pencere: konum + vakit yenileme (kilit ekranında da görünür) |
| `Lang.kt` | Uygulama dili: seçili dil (`Store.language`), `Lang.wrap` (her ekran ve bildirim/widget yazısı seçili dilde), `LangActivity` (bütün ekranların atası), `Lang.prayerName` |
| `MainActivity.kt` | Ana ekran, ilk açılış akışı (`resumeFlow`), otomatik konum kontrolü |
| `SettingsActivity.kt` | Konum, yöntem, ikindi, bildirimler, hatırlatma, ezan sesi, hakkında |
| `LocationActivity.kt` | Ülke → şehir → ilçe elle seçim |
| `ImsakiyeActivity.kt`, `HolyDaysActivity.kt` | Aylık tablo, dini günler |
| `QiblaActivity.kt`, `QiblaView.kt` | Pusulalı kıble |
| `QiblaArActivity.kt`, `ArOverlayView.kt` | Kamerayla (AR) kıble — `ComponentActivity` + CameraX |
| `MosquesActivity.kt` | Yakındaki camiler ekranı (arama mantığı `MosqueSearch.kt`: OpenStreetMap Overpass, 3 yedek sunucu, 10 km) |
| `KazaActivity.kt`, `ZikirActivity.kt` | Kaza çetelesi, zikirmatik |
| `Quiz.kt`, `QuizActivity.kt`, `TreeView.kt` | Aile Halkası: bilgi yarışması, oturum kaydı, aile ağacı, aile hedefleri |
| `PrayerGame.kt`, `PrayerGameActivity.kt` | Namaz Öğreniyorum: çocuklar için soru cevap oyunu (abdest + sabah namazının farzı), adımlar ve okunan dualar. Başlangıç ekranında Abdest ve Namaz ayrı düğmelerdir; kalınan adım bölüm bölüm kaydedilir (`Store.prayerStep(ctx, bölüm)`), yarım kalan bölümde "devam mı, baştan mı" diye sorulur. Abdesti hep Ahmet alır, Zeynep seçiliyken Abdest düğmesi kapalıdır. Abdest bitince "Namaza geçelim mi?" sorulur |
| `PrayerStageView.kt` + `assets/namazoyun3d/` | Oyunun 3B sahnesi: kodla üretilen çocuk figürü (Ahmet/Zeynep), abdesthane ve namaz odası. WebView + WebGL2; hazır model, doku ya da kütüphane yok. `motor.js` çizim, `karakter.js` figür ve iskelet, `duruslar.js` duruşlar ve geçiş yolları, `sahne.js` sahneler, `oyun.js` oyunla köprü. Namaz, çocuğun rulo seccadeyi getirip serdiği giriş canlandırmasıyla başlar (`PrayerStageView.intro`, `N3.giris`, `duruslar.js` içinde `girisKur`); seccade ayrı bir örgüdür ve şerit şerit açılır |
| `PrayerVoice.kt` | Oyundaki sesli okuma: `res/raw/ses_*.mp3` kayıtlarını çalar; dosya yoksa sessiz geçer. Dosya adları ve metinler: `docs/SES_DOSYALARI.md` |
| `PrayerFigure.kt`, `PrayerFigureView.kt` | Eski 2B çizim; yalnızca 3B açılamayan cihazlarda yedek olarak kullanılır |

Soru bankası: `tools/sorular.py` → `python3 tools/sorular.py app/src/main/assets/sorular.json`.
JSON elle düzenlenmez. Her soruda doğru cevap ilk şıktır; uygulama şıkları karıştırır.

## Diller

- Yazılar `res/values/strings.xml` (Türkçe) ve `values-fr`, `values-en`, `values-de` içindedir. Bu dosyalar
  **elle düzenlenmez**: kaynak `tools/diller.json`, üretmek için `python3 tools/diller.py`.
- Her yeni ekran `LangActivity`'den türer; ekransız yerler (bildirim, widget, servis) yazıyı
  `Lang.str(ctx, R.string.x)` ya da `Lang.wrap(ctx)` ile alır. Widget'taki yazılar XML'de değil kodda verilir
  (widget'ı telefonun ana ekranı, telefonun kendi diliyle çizer).
- `LangResourcesTest` dört dosyada aynı yazıların ve aynı yer tutucuların (`%1$s`) olduğunu denetler.
- Vakit adları: İngilizce/Fransızca/Almancada "İmsak" yerine "Fajr" yazılır; Diyanet'in imsak vakti fecr-i
  sadıkın, yani sabah namazı vaktinin başlangıcıdır. Hicri tarih Türkçede Diyanet'in yazdığı gibi, öteki
  dillerde aynı gün o dilin ay adlarıyla gösterilir.
- Büyük/küçük harf dönüşümünde dilden bağımsız işler (yer adları, `norm`) sabit `TR`/`Locale.ROOT` kullanır;
  seçili dile bakmaz. Tarih biçimi `Lang.locale(ctx)` ile yazılır.
- Henüz çevrilmeyenler bu bölümün altında, "Çeviri durumu" başlığında listelenir.

### Çeviri durumu

- Çevrildi: ana ekran, vakit adları, bildirimler, ezan bildirimi, widget, yenile penceresi, konum izni
  penceresi, kaynak satırı, otomatik konum adımları, Ayarlar (hesaplama yöntemleri dahil), konum seçimi,
  imsakiye, dini günler, kıble pusulası ve AR kıble, yakındaki camiler, kaza çetelesi, zikirmatik.
- Henüz Türkçe: Aile Halkası (bilgi yarışması ve soru bankası), Namaz Öğreniyorum oyunu ve 3B canlandırma.
  Bunlar dua, sure ve dinî bilgi içerdiğinden ayrı ve dikkatli çevrilmeli.
- Dini günlerin adları `Hijri.HolyKind` ile seçili dile çevrilir (`Lang.holyDayName`); `HolyDay.name` Türkçe
  kalır (testler bunu kullanır). "Üç Aylar" bilinçli olarak "kutsal aylar" diye çevrilmedi: o terim İslam'da
  başka dört ayı (eşhür-i hurum) anlatır; bunun yerine ayların adları yazıldı.
- Hesaplama yöntemlerinin adları ve açıklamaları `method_titles` / `method_where` listelerindedir; sıra
  `CalcMethod` sırasıyla aynı olmalı.
- Diyanet'ten gelen yer adları (ör. "İsviçre") her dilde Diyanet'in yazdığı gibi kalır.

## Kod denetimi (ktlint ve gitleaks)

İkisi de `.github/workflows/denetim.yml` içinde, "APK derle" iş akışından **ayrı** çalışır. Bu yüzden
buradaki kırmızı işaret APK derlemesini ve `main`'den sürüm çıkmasını engellemez; ama bir PR
birleştirilmeden önce ikisinin de yeşil olması beklenir. Sürümler dosyanın başında sabittir
(ktlint 1.8.0, gitleaks v8.30.1).

- **ktlint** Kotlin dosyalarının düzenine bakar. Kurallar `.editorconfig` dosyasındadır: Android Studio
  kural takımı; yalnızca "satırı nerede böleceğin" ile ilgili kurallar ve satır uzunluğu sınırı
  bilinçli olarak kapalıdır (açılsalar 600'den fazla satır yeniden dizilirdi). Kırmızı olursa:
  PR sayfası → Checks → "Kod düzeni (ktlint)" çıktısında dosya, satır ve kural adı yazar.
  Çoğu hatayı ktlint kendisi düzeltebilir: `java -jar ktlint.jar --format "app/src/**/*.kt"`.
- **gitleaks** depoda şifre, anahtar, jeton gibi gizli bilgi var mı diye hem şu anki dosyalara hem
  bütün geçmiş commit'lere bakar. İlk tarama (45 commit) temiz çıktı. Kırmızı olursa **sırrı
  commit geçmişinden silmeye çalışmadan önce o şifreyi/anahtarı geçersiz kıl ve yenisini üret**;
  bir kez GitHub'a çıkan sır sızmış sayılır. Play yükleme anahtarı ve şifreleri yalnızca GitHub
  Secrets'ta durmalı, depoya hiçbir zaman dosya olarak konmamalı.
- `app/debug.keystore` (şifresi `android`) bir sır **değildir**: yalnızca elden kurulan APK'yı
  imzalayan, herkese açık deneme anahtarıdır. Play anahtarıyla ilgisi yoktur.

## Bilinen sorunlar ve şüpheler (öncelik sırasıyla)

1. **Otomatik konum, sınır bölgesi (v1.0.79 sonrası düzeltildi, telefonda doğrulanmalı).** Kullanıcı
   Cenevre/Thônex'te; telefonun yaklaşık konumu sınırın öbür yanına (Ambilly, Fransa) düşüyor. Diyanet'te
   Fransa tek "şehir" ve 582 "ilçe"; eski kod 150'den uzun listede en yakın yeri aramıyor ve
   "bulunamadı, son seçilen yer kullanılıyor" diyordu. Şimdi çevredeki yerleşimler ülke sınırına
   bakılmadan aranıyor; Diyanet'te yakın yer yoksa vakitler konumdan Diyanet ölçüleriyle hesaplanıyor
   (`PrayerCalc.diyanetDay`, ekranda "Konumundan hesaplandı" yazar). Hesaplanan yerdeyken Diyanet
   listesi 12 saatte bir (internet hatasından sonra 2 dakikada) yeniden aranır.
   **Yer değişimini algılama:** uygulama her öne geldiğinde konuma bakılır; son bulunan noktadan
   3 km'den fazla uzaklaşıldıysa yer yeniden aranır (`AutoLocation.MOVE_KM`; eskiden 15 km idi ve
   Annemasse → Cenevre gibi komşu şehir geçişleri algılanmıyordu). Uygulama kapalıyken (widget) konum
   kendiliğinden yenilenemez: Android bunun için "her zaman konum" iznini şart koşar ve uygulama bu
   izni istemez. Widget'taki ve ana ekrandaki ↻ düğmesi (`RefreshActivity`) elle yeniler.
   **Yer adlarının yazımı:** Diyanet adları büyük harfle verir; yabancı adlarda I harfi "i" olur
   ("VERNIER" → "Vernier"), Türkiye, Kuzey Kıbrıs ve Azerbaycan'da Türkçe kural geçerlidir
   (`titleCase(ad, turkish)`, `Diyanet.turkishSpelling`).
2. **Saat dilimi:** Diyanet vakitleri o yerin yerel saatidir ama alarmlar telefonun saat diliminde kurulur.
   Elle uzak bir yer seçilirse (ör. İsviçre'deyken İstanbul) vakitler kayar. API'deki
   `GreenwichOrtalamaZamani` alanı kullanılabilir.
3. **Yüksek enlem / gece yarısını geçen yatsı:** `PrayerCalc` saati `LocalTime`'a mod 24 ile çeviriyor;
   yatsı 00:00'ı geçerse aynı günün başına düşer ve sıralama bozulur.
4. **Ezan servisi:** Tam zamanlı alarm izni yoksa (inexact alarm) Android 12+ arka plandan ön plan servisi
   başlatmayı reddedebilir (`ForegroundServiceStartNotAllowedException`); şu an sessizce yutuluyor.
5. **Hedef API:** targetSdk 35. Play'in güncel şartı kontrol edilip yükseltilmeli (edge-to-edge,
   öngörülü geri hareketi, `onBackPressed` kullanımları).
6. **Hicri kaydırma:** `HicriTarihKisa` biçimi ("2.6.1448" varsayıldı) doğrulanmadı.
   Dini günler Diyanet'in yayımladığı takvimle karşılaştırılmalı.
7. **Widget:** alarm gecikirse Chronometer eksiye düşer; `updatePeriodMillis` 30 dk.
8. **Kilit ekranı:** `RefreshActivity` `setShowWhenLocked(true)` kullanıyor; kilit ekranı widget'ından
   açılınca kilit sorulup sorulmadığı denenmedi.
9. **AR kıble:** kamera yatay görüş açısı 62° varsayıldı; `CameraCharacteristics`'ten okunabilir.
10. **Ana ekran her saniye** `Times.days()` + `Prayers.moments()` çağırıyor; gereksiz iş.
11. ~~Birim testi yok.~~ Yapıldı: `app/src/test/`. Hata taraması sonuçları: `docs/HATA_TARAMASI.md`.
12. **Soru içeriği** bir hoca tarafından kontrol edilmedi; Play'den önce şart.

## Uyulması gereken kurallar

- Reklam, analitik, izleyici, hesap/sunucu YOK. Kişisel veri cihazdan çıkmaz
  (yalnızca Diyanet, Geocoder/Nominatim ve Overpass sorguları; kimlik bilgisi gönderilmez).
- Yeni bağımlılık, Compose'a geçiş, sunucu/bulut eklemek gibi büyük kararlar **önce sahibine sorulur**.
- Dini içerikte uydurma yok; tartışmalı konular ya çıkarılır ya mezhep belirtilerek yazılır.
- Başka uygulamalardan kod, görsel, ses kopyalanmaz.
- Play meta veri politikası: başlık/simge/geliştirici adında "Reklamsız/Ücretsiz" geçmez.
