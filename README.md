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

Android SDK gerekir (Android Studio ile gelir). Böyle derlenen APK yalnızca denemek içindir:
bilgisayarın kendi deneme anahtarıyla imzalanır ve **Releases** sayfasından kurulmuş
Kıblenüma'nın üzerine güncelleme olarak kurulamaz (aşağıya bak).

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

**Releases** sayfasındaki APK'lar gizli bir anahtarla imzalanır. Anahtar depoda değildir;
GitHub Secrets'ta durur ve imzayı yalnızca GitHub Actions atar. Böylece başka birinin derlediği
bir APK, telefondaki Kıblenüma'nın üzerine "güncelleme" olarak kurulamaz.

v1.0.98 ve öncesi, depoda açık duran bir deneme anahtarıyla imzalanıyordu. Yeni sürümler eski
kurulumun üzerine **veri silinmeden** kurulur (Android'in "imza döndürme" özelliği); ayrıntı
`docs/DEVIR_NOTLARI.md` içinde, "İmza" başlığında.
Play Store'a çıkarken ayrıca gizli tutulan bir yükleme anahtarı kullanılacak (`PLAY_STORE.md`).
