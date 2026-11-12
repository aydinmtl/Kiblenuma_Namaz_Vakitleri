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
- Kaynak: yalnızca kendi kaydımız ya da kullanım izni yazılı olarak belli olan kayıtlar.
  Başka uygulamalardan ya da izni belli olmayan sitelerden ses kopyalanmaz.

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
