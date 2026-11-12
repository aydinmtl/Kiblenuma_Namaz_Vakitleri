# Namaz oyununun ses dosyaları

Namaz Öğreniyorum oyununda figür bir dua, sure, niyet ya da "Allâhu Ekber" söylerken
uygulamanın içindeki ses kaydı çalınır. Kayıtlar uygulamayla birlikte gelir, internet gerekmez.

## Dosyalar nereye konur

`app/src/main/res/raw/` klasörüne, aşağıdaki adlarla. Uzantı `mp3`, `m4a` ya da `ogg` olabilir
(ör. `ses_fatiha.mp3`). Ad küçük harf, rakam ve alt çizgiden oluşmalı.

- Bir dosya yoksa o okuma sessiz geçer, oyun bozulmaz.
- Dosya varsa okuma kartındaki kelimeler kaydın uzunluğuna göre ilerler.
- Kod değişikliği gerekmez: dosyayı klasöre koymak yeter. Adlar `PrayerGame.kt` içindeki `PrayerSounds` nesnesindedir.

## Kayıt kuralları

- Her dosyada yalnızca o söz olsun; başında ve sonunda en fazla yarım saniye sessizlik.
- Tek kanal (mono), 44,1 kHz, 64-96 kbps yeterli. 18 dosyanın toplamı 2 MB'ı geçmez.

## Kayıtların kaynağı

Kayıtlar Diyanet İşleri Başkanlığı'nın kendi sitelerinden alındı (proje sahibinin kararı, 2026-11-12):

- Sureler ve ayetler (`ses_besmele`, `ses_euzu`, `ses_fatiha`, `ses_ihlas`, `ses_kevser`):
  kuran.diyanet.gov.tr, ayet ayet okuyuş (Osman Şahin). Ayet dosyaları sırayla birleştirildi.
- Dualar (`ses_subhaneke`, `ses_ruku`, `ses_secde`, `ses_tahiyyat`, `ses_salli`,
  `ses_barik`, `ses_rabbena`): namaz.diyanet.gov.tr, "Sureler ve Dualar" bölümü, Hanefi duaları.
  Rükû ve secde tesbihi kayıtta bir kez okunuyor; üç kez art arda eklendi.
- `ses_sehadet`: Ettehiyyâtü kaydının son bölümü (kelime-i şehâdet) kesilerek elde edildi.

Dosyalar tek kanala ve 64 kbps'e indirildi, baştaki ve sondaki sessizlik kırpıldı, ses düzeyleri eşitlendi.

- `ses_niyet`, `ses_tekbir`, `ses_semi`, `ses_selam`: Diyanet'in sitelerinde ayrı kayıt olarak yok.
  Bunlar açık kaynak Piper ses üretme aracıyla bilgisayarda üretildi (niyet: Türkçe `tr_TR-dfki-medium`
  sesi; diğer üçü: Arapça `ar_JO-kareem-medium` sesi, harekeli Arapça metinden). Yapay sestir;
  gerçek bir okuyuş kaydı bulunursa aynı adla değiştirilmesi yeterli.
  Not: Türkçe sesin eğitim verisinin lisansı CC BY-NC-SA 4.0'dır (ticari olmayan kullanım).

**Şu an olmayan kayıt:** `ses_rabbena_lekel`. Diyanet kaydında söz "Rabbenâ ve lekel hamd" diye okunuyor,
oyundaki metinle ("Rabbenâ lekel hamd") uyuşmadığı için çıkarıldı; o adım sessiz geçer.

## Liste

| Dosya adı | Ne okunur | Metin |
|---|---|---|
| `ses_besmele` | Besmele | Bismillâhirrahmânirrahîm. |
| `ses_sehadet` | Kelime-i şehâdet | Eşhedü en lâ ilâhe illallâh ve eşhedü enne Muhammeden abdühû ve rasûlüh. |
| `ses_niyet` | Niyet | Niyet ettim Allah rızası için bugünkü sabah namazının farzını kılmaya. |
| `ses_tekbir` | Tekbir | Allâhu Ekber. |
| `ses_subhaneke` | Sübhâneke | Sübhânekellâhümme ve bi hamdik. Ve tebârakesmük. Ve teâlâ ceddük. Ve lâ ilâhe ğayrük. |
| `ses_euzu` | Eûzü besmele | E'ûzü billâhimineşşeytânirracîm. Bismillâhirrahmânirrahîm. |
| `ses_fatiha` | Fâtiha suresi | Elhamdü lillâhi rabbil'âlemîn. Errahmânirrahîm. Mâliki yevmiddîn. İyyâke na'büdü ve iyyâke neste'în. İhdinas-sırâtal müstakîm. Sırâtallezîne en'amte aleyhim ğayril mağdûbi aleyhim ve leddâllîn. Âmîn. |
| `ses_ihlas` | İhlâs suresi | Kul hüvallâhü ehad. Allâhüssamed. Lem yelid ve lem yûled. Ve lem yekün lehû küfüven ehad. |
| `ses_kevser` | Kevser suresi | İnnâ a'taynâkel kevser. Fesalli li rabbike venhar. İnne şânieke hüvel ebter. |
| `ses_ruku` | Rükû tesbihi | Sübhâne rabbiyel azîm. Sübhâne rabbiyel azîm. Sübhâne rabbiyel azîm. |
| `ses_semi` | Rükûdan doğrulurken | Semi'allâhü limen hamideh. |
| `ses_rabbena_lekel` | Dimdik durunca | Rabbenâ lekel hamd. |
| `ses_secde` | Secde tesbihi | Sübhâne rabbiyel a'lâ. Sübhâne rabbiyel a'lâ. Sübhâne rabbiyel a'lâ. |
| `ses_tahiyyat` | Ettehiyyâtü | Ettehiyyâtü lillâhi vessalevâtü vettayyibât. Esselâmü aleyke eyyühen-nebiyyü ve rahmetullâhi ve berakâtüh. Esselâmü aleynâ ve alâ ibâdillâhis-sâlihîn. Eşhedü en lâ ilâhe illallâh ve eşhedü enne Muhammeden abdühû ve rasûlüh. |
| `ses_salli` | Allâhümme salli | Allâhümme salli alâ Muhammedin ve alâ âli Muhammed. Kemâ salleyte alâ İbrâhîme ve alâ âli İbrâhîm. İnneke hamîdün mecîd. |
| `ses_barik` | Allâhümme bârik | Allâhümme bârik alâ Muhammedin ve alâ âli Muhammed. Kemâ bârekte alâ İbrâhîme ve alâ âli İbrâhîm. İnneke hamîdün mecîd. |
| `ses_rabbena` | Rabbenâ duaları | Rabbenâ âtinâ fid'dünyâ haseneten ve fil'âhireti haseneten ve kınâ azâben-nâr. Rabbenağfirlî ve li vâlideyye ve lil mü'minîne yevme yekûmül hisâb. |
| `ses_selam` | Selam | Esselâmü aleyküm ve rahmetullâh. |
