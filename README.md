# Kıblenüma

Reklamsız, ücretsiz ve hiçbir kişisel veri toplamayan Android namaz vakti uygulaması.

*Kıblenüma*: Osmanlıcada "kıble gösteren", kıble yönünü bulmaya yarayan pusula.

## Özellikler

- **Diyanet vakitleri:** Diyanet İşleri Başkanlığı'nın vakitleri ([ezanvakti.emushaf.net](https://ezanvakti.emushaf.net) üzerinden). Ülke → şehir → ilçe seçilir, 30 günlük vakit telefona kaydedilir ve internetsiz de çalışır. Veri azalınca kendiliğinden yenilenir.
- **Geri sayım:** Sıradaki vakte kalan süre, içinde bulunulan vakit vurgulu.
- **Vakit bildirimi:** Her vakit girdiğinde bildirim gelir (Güneş hariç). Sesi, Android ayarlarındaki "Namaz vakitleri" bildirim kanalından değiştirilebilir.
- **İmsakiye:** 30 günlük vakit tablosu.
- **Kıble pusulası:** Yaklaşık konum + telefonun pusulası; manyetik sapma düzeltmesi yapılır.
- **Ana ekran widget'ı:** Sıradaki vakit, canlı geri sayım ve günün 6 vakti.

## APK'yı indirme

Her `main` gönderiminde GitHub Actions APK'yı derler ve **Releases** sayfasına koyar.
Telefonda en son sürümün `Kiblenuma.apk` dosyasını indir ve kur
("Bilinmeyen kaynaklardan yüklemeye izin ver" istenebilir).

## Kendi bilgisayarında derleme

```bash
./gradlew assembleRelease
# çıktı: app/build/outputs/apk/release/app-release.apk
```

Android SDK gerekir (Android Studio ile gelir).

## Proje yapısı

| Dosya | Görevi |
|---|---|
| `Diyanet.kt` | Ülke/şehir/ilçe listeleri ve vakitleri internetten çeker, JSON'u ayrıştırır |
| `Store.kt` | Seçilen yer, ayarlar ve 30 günlük önbellek |
| `Model.kt` | Veri tipleri, sıradaki vakit hesabı |
| `MainActivity.kt` | Ana ekran ve geri sayım |
| `LocationActivity.kt` | Yer seçimi |
| `ImsakiyeActivity.kt` | Aylık tablo |
| `QiblaActivity.kt`, `QiblaView.kt` | Kıble pusulası |
| `AlarmScheduler.kt`, `Receivers.kt`, `Notifications.kt` | Vakit alarmı ve bildirim |
| `PrayerWidget.kt` | Ana ekran widget'ı |

## İmza anahtarı hakkında

`app/debug.keystore` yalnızca elden kurulan (sideload) APK'lar için sabit bir test anahtarıdır;
böylece her yeni APK eskisinin üzerine güncelleme olarak kurulur.
Play Store'a çıkarken gizli tutulan ayrı bir yükleme anahtarı kullanılacak.
