# Kıblenüma: Namaz Vakitleri — Gizlilik Politikası

Son güncelleme: 15 Kasım 2026

Kıblenüma'da reklam, üyelik, hesap, analiz (istatistik) ya da takip aracı yoktur.
Uygulamanın kendine ait bir sunucusu yoktur. Senin hakkında hiçbir bilgi bize gelmez.

## Telefonda saklananlar

Aşağıdakiler telefonunda, uygulamanın kendi alanında saklanır. Uygulamanın sunucusu
olmadığı için bunların hiçbiri bize gelmez:

- Seçtiğin şehir ve vakit hesaplama ayarları
- Kaza namazı sayıların, zikir sayıların
- Aile Halkası yarışmasının ilerlemesi, oyuncu adları, aile hedefleri ve "bu soruda hata var" notların
- Bildirim ve ezan tercihlerin

**Yedekleme:** Telefonunda Android'in yedekleme özelliği açıksa, bu veriler Android'in
senin Google hesabına aldığı şifreli yedeğe dahil olabilir ve yeni telefonuna (ya da
uygulamayı yeniden kurduğunda) geri yüklenebilir. Bu yedeği uygulama değil Android alır;
istemezsen telefonunun Ayarlar → Google → Yedekleme bölümünden kapatabilirsin.
Uygulamayı silince telefondaki veriler silinir.

## Konum

Konum izni isteğe bağlıdır; vermezsen şehrini elle seçebilirsin. Yalnızca **yaklaşık
konum** (şehir düzeyi) istenir. Konum şunlar için kullanılır:

- Bulunduğun şehri bulup vakitleri göstermek, seyahatte şehri kendiliğinden değiştirmek
- Kıble yönünü hesaplamak
- "Yakındaki Camiler" ekranında camileri bulmak

Şehir adını bulmak için önce telefonunun kendi yer adı hizmetine (Android; çoğu telefonda
bu hizmeti Google sağlar) sorulur. O çalışmazsa koordinat, adın, hesabın ya da cihaz
kimliğin **olmadan** OpenStreetMap'in Nominatim hizmetine (nominatim.openstreetmap.org)
gönderilir.

"Yakındaki Camiler" ekranı camileri bulmak için koordinatı aynı şekilde, OpenStreetMap
verisini sunan şu iki Overpass sunucusundan birine gönderir; ikincisi yalnızca birincisi
cevap vermezse kullanılır:

- overpass-api.de (FOSSGIS e.V.)
- overpass.private.coffee (Private.coffee)

Koordinat başka hiçbir sunucuya gönderilmez. Bu hizmetlerin kendi gizlilik kuralları
vardır; OpenStreetMap Vakfı'nınki: https://osmfoundation.org/wiki/Privacy_Policy

Konum geçmişi tutulmaz; yalnızca son seçilen yer telefonda saklanır.

## Namaz vakitleri

"Diyanet" yöntemi seçiliyse vakitler ezanvakti.emushaf.net hizmetinden alınır. Bu
istekte koordinat değil, yalnızca seçilen ülke/şehir/ilçe numarası gönderilir. Diğer
yöntemlerde vakitler telefonda hesaplanır, internet gerekmez.

## Kamera

Kamera izni yalnızca "Kamerayla kıble" ekranında, kıble yönünü kamera görüntüsünün
üzerine çizmek için kullanılır. Görüntü kaydedilmez, hiçbir yere gönderilmez.

## Bildirimler ve alarmlar

Bildirim ve tam zamanlı alarm izinleri, vakit girince haber vermek ve ezan okumak
içindir. Bunlar için internete bir şey gönderilmez.

## Sesli okuma

Aile Halkası'nda "Oku" düğmesi telefonunun kendi metin okuma (TextToSpeech) motorunu
kullanır. Uygulama bu metni hiçbir yere göndermez.

## Paylaşma

"İşaretli soruları paylaş" düğmesine basarsan, işaretlediğin soruların listesi senin
seçtiğin uygulamayla (ör. WhatsApp, e-posta) paylaşılır. Sen basmadıkça hiçbir şey
paylaşılmaz.

## Çocuklar

Uygulama çocuklardan bilerek kişisel bilgi toplamaz; zaten hiç kimseden toplamaz.

## Değişiklikler ve iletişim

Bu politika değişirse bu sayfa güncellenir. Soru ve önerilerin için:
https://github.com/aydinmtl/Kiblenuma_Namaz_Vakitleri/issues

---

## Privacy Policy (English summary)

Kıblenüma has no ads, no accounts, no analytics and no server of its own. All settings,
counters and quiz progress are stored on the device; if Android backup is enabled on the
phone, they may be included in Android's own encrypted backup in the user's Google
account. Location (approximate only, optional) is used to find the city, the qibla
direction and nearby mosques; if the phone's own geocoder is unavailable, coordinates are
sent without any identifier to OpenStreetMap's Nominatim service, and the "Nearby mosques"
screen sends them to an Overpass server serving OpenStreetMap data (overpass-api.de, or
overpass.private.coffee as a fallback). Coordinates are not sent to any other server.
The Diyanet times service receives only the selected city ID. The camera is used only
for the AR qibla view; nothing is recorded or sent. Contact:
https://github.com/aydinmtl/Kiblenuma_Namaz_Vakitleri/issues
