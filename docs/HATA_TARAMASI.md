# Hata taraması (11 Kasım 2026)

Bütün Kotlin kodu, manifest, widget tanımları ve iş akışı okundu. Bulut ortamında Android SDK
olmadığı için uygulama çalıştırılamadı; telefona bağlı maddeler "telefonda denenmeli" diye işaretli.
Ekrana bağlı olmayan mantık JVM birim testleriyle denendi (`app/src/test/`).

Önem dereceleri:
- **Kritik:** Kullanıcı vakti kaçırabilir, uygulama çöker ya da veri kaybolur.
- **Orta:** Belirli durumlarda yanlış sonuç; ya da Play'e çıkışı engeller.
- **Küçük:** Kozmetik, nadir ya da zararsız.

## Kritik

| # | Sorun | Nerede | Durum |
|---|---|---|---|
| K1 | **Ezan çalamayınca vakit bildirimi de sessiz kalıyor.** Ezan açıkken bildirim önce "sessiz" kanala gönderiliyor, sonra ezan servisi başlatılıyor. Android 12+ servisi başlatmayı reddederse (ör. "Alarmlar ve hatırlatıcılar" izni yoksa; Android 14+ yeni kurulumda bu izin kapalı gelir) hata sessizce yutuluyor: ne ezan çalıyor ne bildirim ses çıkarıyor. Kullanıcı vakti kaçırır. | `Receivers.kt`, `AdhanService.start` | Düzeltilecek |

## Orta

| # | Sorun | Nerede | Durum |
|---|---|---|---|
| O1 | **Vakit verisi bitebilir.** Ayarlarda Diyanet dışı bir yöntem seçili ama yerin koordinatı bulunamadıysa uygulama Diyanet listesini gösterir; fakat `needsRefresh` yöntem Diyanet değil diye hiç yenileme yapmaz. ~30 gün sonra vakitler biter, ekran "--:--" olur, alarm kurulmaz. | `Store.needsRefresh` | Düzeltilecek |
| O2 | **Gece yarısını geçen vakit sıralamayı bozar** (devir notu 3). Hesaplanan yatsı 00:00'ı geçerse aynı günün başına (00:xx) yazılıyor; sıradaki vakit ve alarm yanlış olur. Cenevre'de Haziran'da MWL yatsısı astronomik olarak 00:01'dir; yüksek enlem kuralı onu 23:50'ye çektiği için Cenevre'de şimdilik sorun çıkmıyor, ama İskandinavya / İskoçya gibi daha kuzeyde çıkar. | `PrayerCalc.day`, `Prayers.moments` | Düzeltilecek |
| O3 | **Yakındaki camiler listesi en yakınları atlayabilir.** Overpass sorgusu 10 km içindeki ilk 100 kaydı (mesafeye göre değil, kayıt numarasına göre) getiriyor; İstanbul gibi yoğun yerlerde en yakın camiler listede olmayabilir. | `MosquesActivity.search` | Düzeltilecek |
| O4 | **Gizlilik metni eksik bilgi veriyor.** Konum izni penceresi "şehir adı için yalnızca telefonun kendi konum hizmetine sorulur" diyor; oysa o çalışmazsa koordinat OpenStreetMap'e (Nominatim) gönderiliyor, camiler ekranı da Overpass'a gönderiyor. Play "Veri güvenliği" formu için de doğru yazılmalı. | `LocationConsent.kt` | Düzeltilecek |
| O5 | **Saat dilimi** (devir notu 2). Diyanet vakitleri yerin yerel saatidir, ama ekran ve alarmlar telefonun saat dilimini kullanır. Elle başka saat dilimindeki bir yer seçilirse (Cenevre'deyken İstanbul) geri sayım ve bildirim 1-2 saat kayar. API'deki `GreenwichOrtalamaZamani` alanının yaz saatini içerip içermediği bilinmiyor; bulut ortamından servise erişilemediği için doğrulanamadı. Yanlış varsayımla düzeltmek Cenevre'deki normal kullanımı bozabilir. | `Store`, `Times` | **Ertelendi**, örnek veri gerekli |
| O6 | **Hedef API 35, Play artık 36 istiyor** (devir notu 5). 31 Ağustos 2026'dan beri yeni uygulama/güncellemeler için hedef API 36 şart. 36'ya geçince Android 16+ telefonlarda `onBackPressed` çağrılmaz (öngörülü geri hareketi) ve kenardan kenara (edge-to-edge) çizim zorunlu olur; `LocationActivity` ve `QuizActivity` geri tuşu davranışı değişir. AGP/Gradle sürümü de yükselmeli. Telefonda çalışmayı bozmaz, Play'e çıkışı engeller. | `app/build.gradle.kts` | Ayrı PR önerisi |

## Küçük

| # | Sorun | Nerede |
|---|---|---|
| S1 | Widget geri sayımı alarm gecikirse eksiye düşer (devir notu 7). Alarm tam zamanlıysa görülmez. | `PrayerWidget` |
| S2 | Ana ekran her saniye `Times.days()` + `Prayers.moments()` çağırıyor (devir notu 10). Önbellek sayesinde maliyeti çok düşük (≈190 öğe sıralanıyor); ölçülebilir pil etkisi beklenmez. | `MainActivity.tick` |
| S3 | Bildirim alarmı Doze yüzünden 15 dakikadan fazla gecikirse bildirim hiç gösterilmez (eski vakit için uyarı vermeme kuralı). Yalnızca tam zamanlı alarm izni yoksa olur. | `PrayerAlarmReceiver` |
| S4 | Aynı ana denk gelen iki olaydan (ör. bir hatırlatma ile bir vakit girişi) ikincisi atlanır. Çok nadir. | `AlarmScheduler.nextEvent` |
| S5 | `Hijri.alignWithDiyanet` ortak `offsetDays` değişkenini döngüde geçici olarak değiştiriyor; aynı anda başka iş parçacığı Hicri tarih okursa bir an yanlış gün görebilir. | `Hijri.kt` |
| S6 | Elle yer seçiminde arama aksan duyarlı: "zurih" yazınca "Zürih" bulunur ama "geneve" ile "Cenevre" bulunmaz (beklenen), "kadikoy" ile "Kadıköy" bulunmaz. `norm()` kullanılabilir. | `LocationActivity.filter` |
| S7 | Kaza sayısına çok büyük sayı yazılırsa (Int'i aşan) değer sessizce 0 olur. | `KazaActivity.askNumber` |
| S8 | Hedef kaydı bozulursa (aşama numarası listede yoksa) Aile Halkası ekranı çöker. Normal kullanımda olmaz. | `QuizActivity.renderGoals` |
| S9 | AR kıblede kamera görüş açısı 62° varsayılıyor (devir notu 9); `CameraCharacteristics`'ten okunabilir. Ok yönü doğru, yalnızca Kâbe işaretinin ekrandaki yatay yeri biraz kayabilir. | `ArOverlayView` |
| S10 | `allowBackup=true`: yedekten başka telefona geri yüklenince seçili ezan dosyasının adresi geçersiz olur; ezan çalmaz, bildirim yine gelir (K1 düzeltmesinden sonra sesli). | `AndroidManifest.xml` |
| S11 | Fransızca "Genève" ile Diyanet'in İngilizce "Geneva" adı eşleşmiyor (testle gösterildi). Türkçe "Cenevre" adı ya da "en yakın yer" yedeği bunu karşılıyor; Thônex'te sorun beklenmez ama telefonda denenmeli. | `PlaceMatch` |

## Devir notlarındaki 12 maddenin doğrulaması

| # | Madde | Sonuç |
|---|---|---|
| 1 | Otomatik konum telefonda doğrulanmadı | **Kodda hata bulunmadı**, telefonda denenmeli. İsim eşleştirme artık testli (`PlaceMatchTest`). Akış: Geocoder adları → ülke → şehir (isim, tek şehir ya da en yakın) → ilçe. Bkz. S11. |
| 2 | Saat dilimi | **Doğrulandı, gerçek sorun** → O5 (ertelendi, örnek veri gerekli). |
| 3 | Gece yarısını geçen yatsı | **Doğrulandı** → O2. Cenevre'de yüksek enlem kuralı sayesinde şimdilik görülmüyor (test: `cenevre_yaz_yuksekEnlemKurali`). |
| 4 | Ezan servisi arka plandan başlatılamayabilir | **Doğrulandı ve devir notundan daha ciddi** → K1 (bildirim de sessiz kalıyor). |
| 5 | Hedef API | **Doğrulandı** → O6. Play şu an API 36 istiyor. |
| 6 | Hicri kaydırma biçimi doğrulanmadı | Kod "g.a.yyyy" dışındaki biçimde sessizce kaydırma yapmaz (zararsız). Dini gün kuralları testle denendi (`HijriTest`): 2026 Ramazan ve Kurban Bayramı Ümmü'l-Kurâ ile Diyanet'in ilan ettiği tarihlerle aynı. Ramazan 1447 başlangıcında Ümmü'l-Kurâ Diyanet'ten 1 gün önde; hizalama bunun için var. Telefonda "Dini Günler" ekranı Diyanet takvimiyle karşılaştırılmalı. |
| 7 | Widget geri sayımı eksiye düşer | **Doğrulandı** → S1 (küçük). |
| 8 | Kilit ekranından yenileme | Kodda hata yok; telefonda denenmeli. |
| 9 | AR görüş açısı | **Doğrulandı** → S9 (küçük). |
| 10 | Ana ekran her saniye hesap | **Doğrulandı** → S2 (önbellek sayesinde küçük). |
| 11 | Birim testi yok | **Yapıldı:** 46 JVM testi, her PR'da GitHub Actions'ta çalışıyor. |
| 12 | Soru içeriği hoca kontrolünden geçmedi | Doğru; içerik değiştirilmedi. Aile Halkası önerilerinde ayrıca ele alınacak. |
