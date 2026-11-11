# Play Store bilgileri

## Mağaza başlığı (kesinleşti)

**Kıblenüma: Namaz Vakitleri**  (26 karakter, sınır 30)

- Uygulama adı (telefonda simgenin altında): Kıblenüma
- Paket kimliği: `com.aydinmtl.kiblenuma` (yayından sonra değiştirilemez)

## "Reklamsız" vurgusu: kurallar

- Başlıkta, simgede ve geliştirici adında "Reklamsız", "Ücretsiz", "No ads", "Free" **kullanılamaz**
  (Google Play meta veri politikası; Türkçe çeviriler dahil).
- Uzun açıklamada doğru bilgi olarak yazılabilir: reklam yok, üyelik yok, kişisel veri toplanmaz.
- Reklamsız uygulamalarda "Reklam içerir" etiketi çıkmaz. Veri güvenliği bölümünde ise konum OpenStreetMap'e gönderildiği için "Veri toplanmıyor" yerine "Yaklaşık konum (geçici, isteğe bağlı)" görünür; ayrıntı aşağıda.

## Yayın öncesi yapılacaklar

- [ ] Kalıcı yükleme anahtarı (upload key) oluştur, GitHub Secrets'a ekle (Aydın; adımlar aşağıda, derleme tarafı hazır)
- [x] Gizlilik politikası sayfası (docs/GIZLILIK_POLITIKASI.md)
- [x] Kısa ve uzun açıklama (Türkçe, aşağıda)
- [ ] Ekran görüntüleri ve öne çıkan görsel
- [x] Veri güvenliği formu cevapları (aşağıda; konum OpenStreetMap'e gittiği için "yaklaşık konum, geçici işlenir" beyan edilir)
- [x] Hedef API düzeyini Play'in güncel şartına yükselt (API 36)

> Durum (11 Kasım 2026): Hedef API 36'ya yükseltildi (PR #8). Gizlilik politikası, mağaza
> metinleri, veri güvenliği cevapları ve imzalı AAB derlemesi hazır (aşağıda). Kalanlar:
> yükleme anahtarını oluşturmak (Aydın), ekran görüntüleri ve öne çıkan görsel (Aydın).

## Gizlilik politikası

Metin: [docs/GIZLILIK_POLITIKASI.md](docs/GIZLILIK_POLITIKASI.md)

Play Console'a yazılacak adres (depo herkese açık olmalı):
`https://github.com/aydinmtl/Kiblenuma_Namaz_Vakitleri/blob/main/docs/GIZLILIK_POLITIKASI.md`

## Kısa açıklama (en çok 80 karakter)

Diyanet namaz vakitleri, kıble pusulası, ezan ve ailece bilgi yarışması.

## Uzun açıklama (en çok 4000 karakter)

Kıblenüma; namaz vakitlerini, kıble yönünü ve günlük ibadet hatırlatmalarını sade bir
ekranda bir araya getirir. Reklam yok, üyelik yok, kişisel veri toplanmaz.

Not: Kıblenüma bağımsız bir uygulamadır; Diyanet İşleri Başkanlığı'nın resmî uygulaması değildir.

🕌 NAMAZ VAKİTLERİ
• Diyanet İşleri Başkanlığı vakitleri ya da telefonda hesaplanan vakitler (internetsiz)
• Bir sonraki vakte geri sayım, ana ekran aracı (widget)
• Seyahatte şehir kendiliğinden değişir; başka saat dilimindeki yerler doğru gösterilir
• Ramazan imsakiyesi, mübarek gün ve geceler

🔔 EZAN VE HATIRLATMA
• Vakit girince bildirim ve isteğe bağlı ezan
• Vakitten önce hatırlatma

🧭 KIBLE
• Pusula ile kıble yönü
• Kamerayla kıble: kıble yönü kamera görüntüsünün üzerinde gösterilir
• Yakındaki camiler ve mescitler (OpenStreetMap)

📿 İBADET YARDIMCILARI
• Kaza namazı takibi
• Zikirmatik

🌳 AİLE HALKASI
Ailece oynanan İslami bilgi yarışması. Kolay, orta ve zor seviyeler; sırayla oynama;
yanlışları tekrar etme. İlk kez doğru bilinen her soru aile ağacını büyütür. Ağaç
büyüdükçe birlikte koyduğunuz hedeflere (ör. "Meyveli ağaç olunca piknik") ulaşırsınız.
• Her gün bir "Günün sorusu"
• Soruları ve şıkları sesli okuma
• Kaynağı belirtilen sorularda ayet ya da hadis numarası
• Hatalı bulduğun soruyu işaretleyip paylaşabilme

🔒 GİZLİLİK
Uygulamanın kendi sunucusu yoktur. Ayarların, sayaçların ve yarışma ilerlemesi yalnızca
telefonunda saklanır. Konum izni isteğe bağlıdır; vermezsen şehrini elle seçebilirsin.

## Veri güvenliği formu (Play Console → Uygulama içeriği → Veri güvenliği)

Google'ın tanımında, cihazdan dışarı gönderilen her veri "toplanan veri" sayılır. Konum,
şehir adı ve camiler için OpenStreetMap'e gönderildiğinden **yaklaşık konum** beyan
edilmeli. Dürüst beyan şöyle:

| Soru | Cevap |
|---|---|
| Uygulama gerekli kullanıcı verisi türlerinden herhangi birini topluyor ya da paylaşıyor mu? | **Evet** |
| Toplanan veriler aktarım sırasında şifreleniyor mu? | **Evet** (bütün istekler HTTPS) |
| Kullanıcıların verilerinin silinmesini istemesi için bir yol sağlıyor musunuz? | Sunucuda veri tutulmuyor. "Uygulamayı silince her şey silinir" diye açıklanır. |
| Veri türü | **Konum → Yaklaşık konum** |
| Toplanıyor mu / paylaşılıyor mu? | **Toplanıyor**, paylaşılmıyor (hizmet sağlayıcıya gönderim "paylaşım" sayılmaz) |
| Geçici olarak mı işleniyor? | **Evet** (saklanmıyor, yalnızca o istek için kullanılıyor) |
| Zorunlu mu, isteğe bağlı mı? | **İsteğe bağlı** (şehir elle seçilebilir) |
| Amaç | **Uygulama işlevselliği** |
| Diğer veri türleri (ad, e-posta, fotoğraf, cihaz kimliği, uygulama etkinliği vb.) | **Hayır** |

Not: Kamera görüntüsü cihazdan çıkmadığı için beyan edilmez. Kamera izni yine de izin
listesinde görünür; açıklaması gizlilik politikasında var.

İzinlerle ilgili ek beyanlar:
- **Tam zamanlı alarm (SCHEDULE_EXACT_ALARM):** "Namaz vakti girdiği anda bildirim/ezan" —
  Play'in izin verdiği "alarm/saat" kullanımına uyar.
- **Ön plan hizmeti (mediaPlayback):** ezan sesini çalmak için. Play Console bu tür için
  kısa bir açıklama ve video isteyebilir: ezanın çaldığı ve bildirimden durdurulduğu
  10-20 saniyelik bir ekran kaydı yeter.
- **Reklam içeriyor mu?** Hayır. **Hedef kitle:** 13 yaş ve üstü önerilir (ailece kullanılır;
  "çocuklara yönelik" seçilirse Aileler politikasının ek şartları gelir).

## Yükleme anahtarı (upload key) ve imzalı AAB

Play Store APK değil **AAB** (Android App Bundle) ister ve bunun gizli bir anahtarla
imzalanması gerekir. Anahtar depoya **asla** konmaz; GitHub Secrets'ta saklanır.
Secrets eklenince `main`'e her birleştirmede Actions, `Kiblenuma-aab` adlı imzalı bir
AAB dosyası üretir (Actions → ilgili çalıştırma → Artifacts). Secrets yoksa bu adım
sessizce atlanır, APK eskisi gibi derlenir.

### Bir kerelik kurulum (Aydın, bilgisayarda)

1. Java kurulu bir bilgisayarda (Android Studio ile gelir) şu komutu çalıştır.
   Şifreyi kendin seç ve **bir yere yaz**; anahtar dosyasını kaybedersen Play'de
   sıfırlama talebi gerekir:

   ```
   keytool -genkeypair -v -keystore kiblenuma-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Dosyayı metne çevir:
   - Mac / Linux: `base64 -i kiblenuma-upload.jks | pbcopy` (Linux'ta `base64 -w0 kiblenuma-upload.jks`)
   - Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("kiblenuma-upload.jks")) | Set-Clipboard`

3. GitHub → depo → Settings → Secrets and variables → Actions → **New repository secret**.
   Şu dördünü ekle:

   | Ad | Değer |
   |---|---|
   | `UPLOAD_KEYSTORE_BASE64` | 2. adımda kopyalanan uzun metin |
   | `UPLOAD_STORE_PASSWORD` | anahtar deposu şifresi |
   | `UPLOAD_KEY_ALIAS` | `upload` |
   | `UPLOAD_KEY_PASSWORD` | anahtar şifresi (keytool aynı şifreyi kullandıysa aynısı) |

4. `kiblenuma-upload.jks` dosyasını ve şifreleri güvenli bir yerde (ör. şifre yöneticisi)
   sakla. Bu dosyayı kimseyle paylaşma, sohbete yapıştırma.

5. Play Console'da uygulamayı oluştururken **Play Uygulama İmzalama**'yı kabul et. İlk
   AAB'yi yüklediğinde Google bu yükleme anahtarını tanır.

### Sürüm numarası

`versionCode` GitHub Actions çalıştırma numarasıdır; her derlemede artar, Play'e her
yüklenen AAB bir öncekinden büyük olur. Elden kurulan APK ile Play sürümü farklı
anahtarla imzalandığı için **telefondaki APK sürümünü silmeden Play sürümü kurulamaz**
(veriler silinir; kaza sayılarını önceden not al).
