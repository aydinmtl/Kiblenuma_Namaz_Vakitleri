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
| Bağımlılıklar | `androidx.activity`, CameraX (AR kıble) ve WorkManager (vakit verisi yenileme). |
| SDK | minSdk 26, compileSdk/targetSdk 36 |
| Araçlar | AGP 8.7.3, Kotlin 2.0.21, Gradle 8.14.3 (wrapper depoda) |
| Paket | namespace `com.aydinmtl.namazvakitleri`, applicationId `com.aydinmtl.kiblenuma` |
| Derleme | `.github/workflows/build.yml`: her PR'da ve `main`'e her push'ta birim testleri + `assembleRelease`, APK "artifact" olarak indirilebilir. Yalnızca `main` push'unda GitHub Release `v1.0.<run>` (`Kiblenuma.apk`) |
| İmza | Elden kurulan APK'yı GitHub Actions gizli anahtarla imzalar (anahtar GitHub Secrets'ta, depoda yok). Ayrıntı aşağıda, "İmza" başlığında. Play için ayrı upload key gerekecek |
| Lint | Android Lint her derlemede çalışır (`./gradlew lintDebug`); "hata" bulursa derleme durur, "uyarı"lar raporda kalır. Rapor: PR sayfası → Checks → Artifacts → `lint-raporu` |
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
| `Receivers.kt` | `PrayerAlarmReceiver` (önce sonraki alarm, sonra bildirim, ezan, widget; adımlar birbirinden bağımsız), `BootReceiver` |
| `SelectionGuard.kt` | Yer seçimi ile arka plan işlerini sıralayan kilit: YER sayacı (yalnızca seçili yer değişince) ve VERİ sayacı (alarmı etkileyen her değişiklikte). `GuardedPublication`: alarm kurmayı korur ve reddedilen çağrıyı yeniden dener |
| `DataRefresh.kt` | WorkManager işleri: vakit verisi yenileme (internet varken) ve saatlik alarm bekçisi (kopan alarm zincirini onarır) |
| `NotificationIds.kt` | Bütün bildirim numaraları tek yerde |
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
- Aile Halkası, 120 soruluk banka, Namaz Öğreniyorum ve 3B izleme ekranı da dört dili kullanır.
  Soru/adım kimlikleri değişmez; dil değişimi kayıtlı ilerlemeyi sıfırlamaz. Arapça okunuşlar ve
  ses kayıtları korunur. Soru bankaları `sorular.json` ve `sorular_fr/en/de.json` dosyalarıdır.
  Türkçe bankayı yeniden üretirken diğer bankaların kimliklerini, sırasını ve doğru ilk şıkkını da
  güncelle; `python3 tools/check_content_l10n.py` ve `node app/src/test/js/namaz3d_l10n.test.js` çalıştır.
  Arapça dua/sure okunuşu ve kayıtları dil seçiminden etkilenmez; yalnızca Türkçe niyet seçili dile
  çevrilip okunur. Niyetin sesli okuması atlama, sesi kapatma ve ekrandan çıkmada durdurulur.
  Kaynak künyelerindeki Türkçe açıklamalar da gösterilirken çevrilir; asıl kaynak numaraları korunur.
  Çevirilerdeki dini anlam ve gerçek cihazdaki görünüm ayrıca insan kontrolü gerektirir.
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
- Eski deneme anahtarı (`app/debug.keystore`, şifresi `android`) depodan kaldırıldı ama geçmiş
  commit'lerde durur ve herkese açıktır; sır sayılmaz, gitleaks de onu sır olarak işaretlemez.
  Güvenlik artık ona dayanmaz (bkz. "İmza").

## İmza (elden kurulan APK)

**Sorun neydi?** v1.0.98'e kadar APK, depoda açık duran deneme anahtarıyla imzalanıyordu. Android,
bir uygulamanın üzerine yalnızca aynı anahtarla imzalı APK'yı güncelleme olarak kurar. Anahtar
herkese açık olunca, kötü niyetli biri sahte bir "güncelleme" hazırlayıp yüklü uygulamanın
verilerini okuyabilirdi.

**Şimdi nasıl?**

- Yeni bir gizli anahtar var (RSA 4096, sertifika adı `CN=Kiblenuma`, takma ad `kiblenuma`).
  Yalnızca iki yerde durur: GitHub Secrets (`SIDELOAD_KEYSTORE_BASE64`, `SIDELOAD_STORE_PASSWORD`)
  ve Aydın'ın bilgisayarındaki `~/Kiblenuma-anahtar/` klasörü (yedek). **Bu klasör kaybolursa ve
  GitHub'daki gizli değişken de silinirse bir daha güncelleme çıkarılamaz**; klasör ayrıca güvenli
  bir yere (ör. şifre yöneticisi, şifreli USB) yedeklenmeli. GitHub, gizli değişkeni geri okutmaz.
- Gradle gerçek imzayı atmaz. `assembleRelease` APK'yı o bilgisayara özgü deneme anahtarıyla
  imzalar; `.github/workflows/build.yml` içindeki "APK'yı gizli anahtarla imzala" adımı bu imzayı
  silip `apksigner` ile yeniden imzalar ve sonucu denetler (yanlış anahtar çıkarsa derleme durur).
- **İmza döndürme:** eski kurulumların veri kaybetmeden güncellenebilmesi için APK iki anahtarla
  imzalanır. `app/signing/sideload-lineage.bin` (sır değildir, her APK'nın içinde de vardır) eski
  anahtarın "yerimi bu anahtara bıraktım" beyanıdır.
  - Android 9 ve üstü yeni anahtarı kullanır; eski kurulumun üzerine güncelleme olarak kurar.
    Geçişten sonra yalnızca eski anahtarla imzalı bir APK'yı güncelleme olarak kabul etmez
    (belgede eski anahtarın "geri dönme" yetkisi kapalıdır).
  - Android 8.0 ve 8.1 imza döndürmeyi bilmez; onlar için eski anahtarın imzası da APK'da durur
    (`OLD_SIDELOAD_KEYSTORE_BASE64`). Bu sürümlerde eski zayıflık sürer. Tamamen kapatmanın yolu
    en düşük sürümü Android 9'a (minSdk 28) çıkarmaktır; bu, sahibinin kararıdır.
- Başka birinin deposundan gelen PR'lar gizli anahtarı göremez; onların APK'sı deneme anahtarıyla
  kalır (uyarı yazar). `main`'de anahtar yoksa derleme durur, APK yayınlanmaz.
- Anahtarı değiştirmek gerekirse (sızarsa): yeni anahtar üret, `apksigner rotate --in
  sideload-lineage.bin ...` ile belgeye ekle, Secrets'ı ve iş akışındaki parmak izini güncelle.
  Android 9+ kurulumlar yine veri kaybetmeden güncellenir.

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
2. **Saat dilimi (düzeltildi):** her vakit kendi gerçek anıyla (`Instant`) alarm hesabına girer.
   Telefonun yaz saati farkı günlük tek bir değerle uygulanmaz. Bildirimler ana ekran ve widget gibi
   seçili yerin özgün saatini gösterir; saat dilimi ve gün sınırı regresyon testleri vardır.
3. **Gece yarısını geçen yatsı (düzeltildi):** vakitlere gün kayması eklenir; gece yarısından
   sonraki yatsı ertesi güne yerleştirilir. Yüksek enlem ve gün sırası regresyon testleri vardır.
4. **Ezan servisi:** Tam zamanlı alarm izni yoksa (inexact alarm) Android 12+ arka plandan ön plan servisi
   başlatmayı reddedebilir (`ForegroundServiceStartNotAllowedException`); şu an sessizce yutuluyor.
5. **Hedef API:** compileSdk/targetSdk 36. Edge-to-edge, öngörülü geri hareketi ve
   `onBackPressed` davranışı gerçek cihazda ayrıca kontrol edilmeli.
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

## 15 Kasım 2026 tamamlanan düzeltmeler

- Şehir seçimi, vakit verisi, koordinatlar ve hesap ayarları aynı koruma altında tutarlı okunur.
  Elle seçim sürerken otomatik konum işi seçimi geçersiz kılamaz; iptal/hata yaşam döngüsü
  rezervasyonu serbest bırakır. Geç gelen eski yanıtlar reddedilir.
- Alarm hesaplaması kilit dışında yapılır; kurma/iptal anında seçim ve veri sürümü yeniden
  doğrulanır. Eski ya da daha erken başlayan iş yeni alarmı değiştiremez.
- Konum yaş sınırı bütün dönüş yollarında uygulanır. Diyanet verisi bittiğinde ağ koşullu
  WorkManager işi vakit alarmından bağımsız yenileme yapar.
- Aynı tarih aralığındaki yeni vakitler içerikle anahtarlanan önbelleğe yansır. Ezan ve günlük
  soru farklı bildirim kimlikleri kullanır.
- Android birim testleri ve imzalı APK GitHub Actions üzerinde çalışır. Bu düzeltmeler birim
  testleriyle sınandı; telefonda (konum, bildirim, ezan, widget) ayrıca denenmelidir.

## Faz 2 düzeltmeleri (Kasım 2026): kararlılık

Denetim raporundaki O1, O2, O3, O4, O5, O9 ve O13 maddeleri. Birim testleriyle sınandı;
**telefonda denenmesi gerekenler** her maddede yazılıdır.

- **O1 — Ayar değişikliği süren işi çöpe atmaz.** `SelectionGuard` iki sayaç tutar. Bildirim ayarı ya da
  hesap yöntemi gibi bir değişiklik artık süren indirme/konum işini geçersiz kılmaz. `Store.refresh`,
  seçili ilçe hâlâ indirilen ilçeyse listeyi yazar; `false` yalnızca gerçek başarısızlıktır. Yeni bir
  `Store` ayarı eklerken: yeri değiştiriyorsa `selection.changePlace`, değiştirmiyorsa `selection.change`.
- **O2 — Alarm zinciri.** Her seferinde tek alarm kurulduğu için zincirin kopmaması gerekir: alıcı önce
  sıradaki alarmı kurar; reddedilen kurma çağrısı yeniden denenir; saatlik bekçi (`AlarmWatchdogWorker`)
  kopan zinciri onarır. Alıcıya yeni bir iş eklerken `attempt { }` içine koy.
- **O3 — Kıble kalibrasyon uyarısı** yön yazısının üstünde kalıcıdır (`Ui.compassHint`).
  Telefonda: pusulayı bir mıknatısa yaklaştırıp uyarının çıktığını ve 8 çizince kalktığını gör.
- **O4 — 3B sahneler.** Çizim süreci çökerse uygulama kapanmaz (`onRenderProcessGone`); oyun 2B çizime
  geçer, 3B izleme "yeniden aç" yazısı gösterir. WebGL bağlamı kaybolursa ya da sahne takılırsa
  (`PrayerStageView` bekçisi) yine 2B çizime geçilir. Bekçi süreleri `PrayerStageView` içindeki
  sabitlerdedir; uzun bir canlandırma eklenirse gözden geçir.
- **O5 — Ekran döndürme.** `LocationActivity`, `PrayerGameActivity` ve `RefreshActivity` döndürmede
  yeniden kurulmaz (manifestte `configChanges`).
- **O9 — Ezan.** Uyanık tutma kilidi (`WAKE_LOCK`) ve ses odağı. Telefonda: ekran kapalıyken vakit
  girişi, ezan sırasında gelen arama, müzik çalarken ezan.
- **O13 — Liste bitince hesapla devam.** `PrayerTimeReader.listContinuation`: Diyanet listesi yarını
  kapsamıyorsa ve koordinat biliniyorsa devamı Diyanet ölçüleriyle hesaplanır (`PrayerDay.computed`);
  kaynak satırı ve imsakiye bunu belirtir. Telefonda: uçak modunda tarihi bir ay ileri alıp bak.

## Faz 3 düzeltmeleri (Kasım 2026): diller, taşmalar, imza, altyapı

Denetim raporundaki O6, O7, O8, O10, O11, O12, O14, sonradan eklenen O15 ve sahibinin ana ekran
istekleri (O16). **Telefonda denenmesi gerekenler** her maddede yazılıdır.

- **O6 — İlk açılış dili.** Yeni kurulumda dil telefonun diline göre seçilir (dört dilden biri
  değilse İngilizce) ve ilk pencere dil seçimidir (`Lang.pick`). Eski kullanıcıya dil sorulmaz.
  Telefonda: uygulamanın verisini silmeden güncelle (dil sorulmamalı); ayrı bir telefonda ya da
  veriyi silip ilk açılışı dene.
- **O7 — Kalan Türkçe metinler.** Hata iletileri `Ui.errorText` ile seçili dilde kısa ifadeye
  çevrilir; Java'nın teknik iletisi ekrana basılmaz. Kullanıcıya gösterilecek yeni bir hata
  yazarken `UserMessage` kullan. Telefonda: uçak modunda yer seçimini aç.
- **O8 — Taşmalar.** Widget ve imsakiye yazıları sığacak kadar küçülür (autoSize); düğmeler
  sabit 52dp değil **en az** 52dp'dir, yazı iki satıra inince büyür. Yeni düğmede sabit yükseklik
  verme. Telefonda: dört dilde ve telefonun büyük yazı ayarında widget, imsakiye, ana ekran.
- **O10 — İmza anahtarı.** Yukarıda, "İmza" başlığında. Telefonda: yeni APK'yı eski kurulumun
  **üzerine** kur (silmeden); kaza, zikir ve yarışma verileri durmalı. Kurulum reddedilirse
  uygulamayı SİLME, haber ver.
- **O11 — Gizlilik.** Camiler araması yalnızca iki Overpass sunucusunu kullanır; `maps.mail.ru`
  çıkarıldı. `allowBackup` açık kalır; gizlilik metni bunu anlatır. Yeni bir sunucu eklenirse
  `docs/GIZLILIK_POLITIKASI.md` ve `PLAY_STORE.md` içindeki veri güvenliği cevabı da güncellenir.
- **O12 — WorkManager kalıyor (sahibinin kararı, 15.11.2026).** Vakit verisi yenileme ve saatlik
  alarm bekçisi (`DataRefresh.kt`) ona dayanır. Karşılığında APK biraz büyür ve birleştirilen
  manifestle `ACCESS_NETWORK_STATE` izni gelir (internet var mı diye bakmak için; istediği öteki
  izinleri uygulama zaten kullanıyor). Kişisel veri toplamaz. Başka yeni bağımlılık yine önce sahibine sorulur.
- **O14 — Android Lint** derlemede ve CI'da açık. İlk çalışmadaki 159 hata giderildi; kalan
  uyarılar Faz 4'te (temizlik) ele alınacak.
- **O15 — Yer adları seçili dilde.** Ülke adı telefonun sözlüğünden (`CountryNames`), şehir adı
  telefonun yer adı hizmetinden (`PlaceLabels`, önbellekli). Gösterim için `Store.placeLabel`;
  asıl kayıt (`Store.placeName`) Diyanet adlarıyla kalır. Telefonda: dili değiştirip ana ekran,
  widget ve bildirimdeki yer adına bak.
- **O16 — Ana ekran.** Düğmeler "Bilgi Yarışması" ve "Namaz Öğreniyorum" yazar (seçili dilde;
  "Aile Halkası" ve "Çocuklar için oyun" ekleri kalktı). "Namazı 3B izle" düğmesi Namaz
  Öğreniyorum ekranının içindedir. **"Günün sorusu" bütünüyle kaldırıldı** (düğmeler, akşam
  bildirimi, kanal, soru seçimi, ayar kayıtları); eski kurulumdaki bildirim kanalı
  `Notifications.createChannel` içinde silinir. Yarışma sayfasının başlığı "Aile Halkası" olarak
  durur.
