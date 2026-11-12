package com.aydinmtl.namazvakitleri

/**
 * "Namaz Öğreniyorum" oyunu: çocuk her soruyu bildikçe figür bir adım ilerler.
 * Abdestle başlar, sabah namazının farzı (2 rekat) selamla biter. Adam asmacanın tersi:
 * bilinen her soru figürü namazın sonuna bir adım daha yaklaştırır.
 *
 * Bu dosya Android'e bağlı değildir; adımlar testlerde (PrayerGameTest) denetlenir.
 *
 * Kaynak kuralı: [PrayerStep.source] alanına yalnızca ayet ya da hadis numarası yazılır.
 * Ayrıntılar Hanefi mezhebine göredir (uygulamanın geri kalanı gibi).
 */

/** Oyunun iki bölümü. Sahne (arka plan) de buna göre değişir. */
enum class PrayerChapter(val title: String, val icon: String) {
    ABDEST("Abdest", "💧"),
    NAMAZ("Namaz", "🕌"),
}

/** Yıkanan / meshedilen yer: doğru cevaptan sonra orada ışıltı belirir. */
enum class BodyPart { HANDS, MOUTH, NOSE, FACE, R_ARM, L_ARM, HEAD, EARS, R_FOOT, L_FOOT, ALL }

/**
 * Okunan bir dua ya da sure: [text] okunuşu (Latin harfleriyle), [meaning] Türkçe anlamı.
 * Figür okurken ağzı kıpırdar; okunuş ekranda kelime kelime ilerler. Ses kaydı varsa çalınır ([PrayerSounds]).
 * [source]: yalnızca ayet ya da hadis numarası.
 */
data class Recitation(val title: String, val text: String, val meaning: String, val source: String = "") {
    val words: List<String> get() = text.split(' ').filter { it.isNotBlank() }

    /** Okuma süresi: kelime başına 380 ms (en az 1,5 sn). */
    val durationMs: Long get() = maxOf(1500L, words.size * 380L)
}

/**
 * Canlandırmanın bir karesi: figür [ms] milisaniyede [pose] duruşuna geçer.
 * [caption] verilirse figürün üstündeki konuşma balonunda o söz yazar ("Allâhu Ekber").
 * [water]: musluk akıyor mu. [recite]: bu sırada okunan dua/sure.
 */
data class Move(
    val pose: String,
    val ms: Long = 700,
    val caption: String? = null,
    val water: Boolean = false,
    val recite: Recitation? = null,
)

/** Oyunda okunan dualar ve sureler (Hanefi uygulamasına göre). */
object Recitations {
    val BESMELE = Recitation(
        "Besmele", "Bismillâhirrahmânirrahîm.",
        "Rahmân ve Rahîm olan Allah'ın adıyla.",
    )
    val SEHADET = Recitation(
        "Kelime-i şehâdet",
        "Eşhedü en lâ ilâhe illallâh ve eşhedü enne Muhammeden abdühû ve rasûlüh.",
        "Şahitlik ederim ki Allah'tan başka ilah yoktur ve yine şahitlik ederim ki Muhammed O'nun kulu ve elçisidir.",
        "Müslim, Tahâret 234",
    )
    val NIYET = Recitation(
        "Niyet", "Niyet ettim Allah rızası için bugünkü sabah namazının farzını kılmaya.",
        "Niyet kalple yapılır; dille söylemek de güzeldir.",
    )
    val SUBHANEKE = Recitation(
        "Sübhâneke",
        "Sübhânekellâhümme ve bi hamdik. Ve tebârakesmük. Ve teâlâ ceddük. Ve lâ ilâhe ğayrük.",
        "Allah'ım! Seni her türlü eksiklikten uzak tutar ve överim. Senin adın mübarektir. Şanın yücedir. Senden başka ilah yoktur.",
        "Müslim, Salât 399",
    )
    val EUZU = Recitation(
        "Eûzü besmele",
        "E'ûzü billâhimineşşeytânirracîm. Bismillâhirrahmânirrahîm.",
        "Kovulmuş şeytandan Allah'a sığınırım. Rahmân ve Rahîm olan Allah'ın adıyla.",
        "Nahl suresi, 98. ayet",
    )
    val FATIHA = Recitation(
        "Fâtiha suresi",
        "Elhamdü lillâhi rabbil'âlemîn. Errahmânirrahîm. Mâliki yevmiddîn. İyyâke na'büdü ve iyyâke neste'în. " +
            "İhdinas-sırâtal müstakîm. Sırâtallezîne en'amte aleyhim ğayril mağdûbi aleyhim ve leddâllîn. Âmîn.",
        "Hamd, âlemlerin Rabbi Allah'a mahsustur. O, Rahmân'dır, Rahîm'dir. Din gününün sahibidir. " +
            "Yalnız sana ibadet ederiz ve yalnız senden yardım dileriz. Bizi doğru yola ilet. Kendilerine nimet " +
            "verdiklerinin yoluna; gazaba uğramışların ve sapmışların yoluna değil.",
        "Fâtiha suresi, 1-7. ayet",
    )
    val IHLAS = Recitation(
        "İhlâs suresi",
        "Kul hüvallâhü ehad. Allâhüssamed. Lem yelid ve lem yûled. Ve lem yekün lehû küfüven ehad.",
        "De ki: O, Allah'tır, bir tektir. Allah Samed'dir; her şey O'na muhtaçtır, O hiçbir şeye muhtaç değildir. " +
            "O doğurmamış ve doğmamıştır. Hiçbir şey O'na denk değildir.",
        "İhlâs suresi, 1-4. ayet",
    )
    val KEVSER = Recitation(
        "Kevser suresi",
        "İnnâ a'taynâkel kevser. Fesalli li rabbike venhar. İnne şânieke hüvel ebter.",
        "Şüphesiz biz sana Kevser'i verdik. Şimdi sen Rabbin için namaz kıl, kurban kes. " +
            "Asıl soyu kesik olan, şüphesiz sana buğzedendir.",
        "Kevser suresi, 1-3. ayet",
    )
    val RUKU = Recitation(
        "Rükû tesbihi",
        "Sübhâne rabbiyel azîm. Sübhâne rabbiyel azîm. Sübhâne rabbiyel azîm.",
        "Büyük Rabbimi her türlü eksiklikten uzak tutarım. (3 kez)",
        "Müslim, Salâtü'l-müsâfirîn 772",
    )
    val SEMI = Recitation(
        "Rükûdan doğrulurken", "Semi'allâhü limen hamideh.",
        "Allah, kendisine hamd edeni işitir.",
        "Buhârî, Ezan 789",
    )
    val RABBENA_LEKEL = Recitation(
        "Dimdik durunca", "Rabbenâ lekel hamd.",
        "Rabbimiz, hamd sanadır.",
        "Buhârî, Ezan 789",
    )
    val SECDE = Recitation(
        "Secde tesbihi",
        "Sübhâne rabbiyel a'lâ. Sübhâne rabbiyel a'lâ. Sübhâne rabbiyel a'lâ.",
        "En yüce olan Rabbimi her türlü eksiklikten uzak tutarım. (3 kez)",
        "Müslim, Salâtü'l-müsâfirîn 772",
    )
    val TAHIYYAT = Recitation(
        "Ettehiyyâtü",
        "Ettehiyyâtü lillâhi vessalevâtü vettayyibât. Esselâmü aleyke eyyühen-nebiyyü ve rahmetullâhi ve berakâtüh. " +
            "Esselâmü aleynâ ve alâ ibâdillâhis-sâlihîn. Eşhedü en lâ ilâhe illallâh ve eşhedü enne Muhammeden abdühû ve rasûlüh.",
        "Bütün dualar, ibadetler ve güzel sözler Allah içindir. Ey Peygamber! Allah'ın selamı, rahmeti ve bereketi " +
            "senin üzerine olsun. Selam bizim üzerimize ve Allah'ın salih kulları üzerine olsun. Şahitlik ederim ki " +
            "Allah'tan başka ilah yoktur ve yine şahitlik ederim ki Muhammed O'nun kulu ve elçisidir.",
        "Buhârî, Ezan 831",
    )
    val SALLI = Recitation(
        "Allâhümme salli",
        "Allâhümme salli alâ Muhammedin ve alâ âli Muhammed. Kemâ salleyte alâ İbrâhîme ve alâ âli İbrâhîm. İnneke hamîdün mecîd.",
        "Allah'ım! İbrahim'e ve ailesine rahmet ettiğin gibi Muhammed'e ve ailesine de rahmet et. " +
            "Şüphesiz sen övülmeye layıksın, şanı yüce olansın.",
        "Buhârî, Enbiyâ 3370",
    )
    val BARIK = Recitation(
        "Allâhümme bârik",
        "Allâhümme bârik alâ Muhammedin ve alâ âli Muhammed. Kemâ bârekte alâ İbrâhîme ve alâ âli İbrâhîm. İnneke hamîdün mecîd.",
        "Allah'ım! İbrahim'e ve ailesine bereket verdiğin gibi Muhammed'e ve ailesine de bereket ver. " +
            "Şüphesiz sen övülmeye layıksın, şanı yüce olansın.",
        "Buhârî, Enbiyâ 3370",
    )
    val RABBENA = Recitation(
        "Rabbenâ duaları",
        "Rabbenâ âtinâ fid'dünyâ haseneten ve fil'âhireti haseneten ve kınâ azâben-nâr. " +
            "Rabbenağfirlî ve li vâlideyye ve lil mü'minîne yevme yekûmül hisâb.",
        "Rabbimiz! Bize dünyada da iyilik ver, ahirette de iyilik ver ve bizi cehennem azabından koru. " +
            "Rabbimiz! Hesap gününde beni, anamı, babamı ve bütün müminleri bağışla.",
        "Bakara suresi, 201. ayet; İbrâhîm suresi, 41. ayet",
    )
    val SELAM = Recitation(
        "Selam", "Esselâmü aleyküm ve rahmetullâh.",
        "Allah'ın selamı ve rahmeti üzerinize olsun.",
        "Müslim, Mesâcid 582",
    )

    val ALL = listOf(
        BESMELE, SEHADET, NIYET, SUBHANEKE, EUZU, FATIHA, IHLAS, KEVSER, RUKU, SEMI, RABBENA_LEKEL,
        SECDE, TAHIYYAT, SALLI, BARIK, RABBENA, SELAM,
    )
}

/**
 * Sesli okuma dosyalarının adları. Dosyalar `app/src/main/res/raw/` klasörüne bu adlarla konur
 * (uzantı mp3, m4a ya da ogg olabilir; ör. `ses_fatiha.mp3`). Olmayan dosya sessiz geçilir.
 */
object PrayerSounds {
    /** "Allâhu Ekber" sözü. */
    const val TEKBIR = "ses_tekbir"

    private val names: Map<Recitation, String> = mapOf(
        Recitations.BESMELE to "ses_besmele",
        Recitations.SEHADET to "ses_sehadet",
        Recitations.NIYET to "ses_niyet",
        Recitations.SUBHANEKE to "ses_subhaneke",
        Recitations.EUZU to "ses_euzu",
        Recitations.FATIHA to "ses_fatiha",
        Recitations.IHLAS to "ses_ihlas",
        Recitations.KEVSER to "ses_kevser",
        Recitations.RUKU to "ses_ruku",
        Recitations.SEMI to "ses_semi",
        Recitations.RABBENA_LEKEL to "ses_rabbena_lekel",
        Recitations.SECDE to "ses_secde",
        Recitations.TAHIYYAT to "ses_tahiyyat",
        Recitations.SALLI to "ses_salli",
        Recitations.BARIK to "ses_barik",
        Recitations.RABBENA to "ses_rabbena",
        Recitations.SELAM to "ses_selam",
    )

    /** Okunan dua ya da surenin ses dosyası adı. */
    fun of(r: Recitation): String? = names[r]

    /** Konuşma balonundaki sözün ses dosyası adı (şimdilik yalnızca "Allâhu Ekber"). */
    fun ofCaption(caption: String?): String? = if (caption == "Allâhu Ekber") TEKBIR else null

    /** Bütün dosya adları (testte ve belgede kullanılır). */
    val ALL: List<String> get() = names.values + TEKBIR
}

/** [pose] duruşunda [r] okunur; süre okunacak kelime sayısına göre. */
fun read(pose: String, r: Recitation) = Move(pose, r.durationMs, recite = r)

/**
 * Oyunun bir adımı.
 * [question] ve [options]: çocuğa sorulan soru; ilk şık her zaman doğrudur (ekranda karışır).
 * [title]: doğru cevaptan sonra görünen adım adı ("Ellerimizi yıkıyoruz").
 * [info]: kısa açıklama. [moves]: doğru cevaptan sonra figürün yaptığı hareketler.
 */
data class PrayerStep(
    val chapter: PrayerChapter,
    val title: String,
    val question: String,
    val options: List<String>,
    val info: String,
    val moves: List<Move>,
    val source: String = "",
    val part: BodyPart? = null,
) {
    val correct: String get() = options[0]
}

object PrayerGame {

    /** Üç kez yıkama: iki duruş arasında üç kez gidip gelinir, balonda "1", "2", "3" yazar. */
    private fun three(a: String, b: String, water: Boolean = true): List<Move> = listOf(
        Move(a, 550, "1", water), Move(b, 380, "1", water),
        Move(a, 380, "2", water), Move(b, 380, "2", water),
        Move(a, 380, "3", water), Move(b, 380, "3", water),
    )

    private const val BUHARI_ABDEST = "Buhârî, Vudû 159"
    private const val MAIDE_6 = "Mâide suresi, 6. ayet"

    val STEPS: List<PrayerStep> = listOf(
        // ===================== ABDEST =====================
        PrayerStep(
            PrayerChapter.ABDEST, "Besmele ile başlıyoruz",
            "Abdest almaya başlarken ne deriz?",
            listOf("Bismillâhirrahmânirrahîm", "Elhamdülillah", "Esselâmü aleyküm"),
            "Abdest almaya niyet eder, \"Bismillâhirrahmânirrahîm\" diyerek başlarız.",
            listOf(read("a_sit", Recitations.BESMELE)),
        ),
        PrayerStep(
            PrayerChapter.ABDEST, "Ellerimizi yıkıyoruz",
            "Önce neyimizi yıkarız?",
            listOf("Ellerimizi bileklere kadar", "Ayaklarımızı", "Saçlarımızı"),
            "Ellerimizi bileklerimize kadar üç kez yıkarız, parmak aralarını da unutmayız.",
            three("a_hands", "a_hands2") + Move("a_sit", 500),
            BUHARI_ABDEST, BodyPart.HANDS,
        ),
        PrayerStep(
            PrayerChapter.ABDEST, "Ağzımızı yıkıyoruz",
            "Sonra ağzımıza su alıp ne yaparız?",
            listOf("Üç kez çalkalarız", "Yutarız", "Hiçbir şey yapmayız"),
            "Sağ elimizle ağzımıza su alır, üç kez çalkalayıp dökeriz.",
            listOf(
                Move("a_hands", 500, null, true), Move("a_mouth", 450, "1"), Move("a_mouth2", 300, "1"),
                Move("a_hands", 450, "2", true), Move("a_mouth", 450, "2"), Move("a_mouth2", 300, "2"),
                Move("a_hands", 450, "3", true), Move("a_mouth", 450, "3"), Move("a_mouth2", 300, "3"),
                Move("a_sit", 500),
            ),
            BUHARI_ABDEST, BodyPart.MOUTH,
        ),
        PrayerStep(
            PrayerChapter.ABDEST, "Burnumuzu temizliyoruz",
            "Ağzımızdan sonra neye su veririz?",
            listOf("Burnumuza", "Kulağımıza", "Gözümüze"),
            "Burnumuza üç kez su çekip temizleriz.",
            listOf(
                Move("a_hands", 500, null, true), Move("a_nose", 450, "1"), Move("a_nose2", 300, "1"),
                Move("a_hands", 450, "2", true), Move("a_nose", 450, "2"), Move("a_nose2", 300, "2"),
                Move("a_hands", 450, "3", true), Move("a_nose", 450, "3"), Move("a_nose2", 300, "3"),
                Move("a_sit", 500),
            ),
            BUHARI_ABDEST, BodyPart.NOSE,
        ),
        PrayerStep(
            PrayerChapter.ABDEST, "Yüzümüzü yıkıyoruz",
            "Yüzümüzü nereden nereye yıkarız?",
            listOf(
                "Alnımızdan çenemizin altına, bir kulaktan öbür kulağa",
                "Yalnız burnumuzu",
                "Yalnız alnımızı",
            ),
            "Yüzümüzün tamamını üç kez yıkarız. Yüzü yıkamak abdestin farzlarındandır.",
            listOf(Move("a_hands", 500, null, true)) + three("a_face1", "a_face2", false) + Move("a_sit", 500),
            MAIDE_6, BodyPart.FACE,
        ),
        PrayerStep(
            PrayerChapter.ABDEST, "Sağ kolumuzu yıkıyoruz",
            "Kollarımızı yıkarken hangisinden başlarız?",
            listOf("Sağ kolumuzdan", "Sol kolumuzdan", "Hangisi olursa"),
            "Peygamberimiz (s.a.v.) temizlikte sağdan başlamayı severdi. Sağ kolumuzu dirseğimizle beraber üç kez yıkarız.",
            three("a_rarm1", "a_rarm2") + Move("a_sit", 500),
            "Buhârî, Vudû 168", BodyPart.R_ARM,
        ),
        PrayerStep(
            PrayerChapter.ABDEST, "Sol kolumuzu yıkıyoruz",
            "Kolumuzu nereye kadar yıkarız?",
            listOf("Dirseğimizle beraber", "Bileğimize kadar", "Yalnız parmaklarımızı"),
            "Sol kolumuzu da dirseğimizle beraber üç kez yıkarız. Kolları dirseklerle beraber yıkamak abdestin farzlarındandır.",
            three("a_larm1", "a_larm2") + Move("a_sit", 500),
            MAIDE_6, BodyPart.L_ARM,
        ),
        PrayerStep(
            PrayerChapter.ABDEST, "Başımızı meshediyoruz",
            "Başımıza ne yaparız?",
            listOf("Islak elimizle meshederiz", "Sabunla yıkarız", "Hiçbir şey yapmayız"),
            "Mesh, ıslak elle sıvazlamak demektir. Başı meshetmek abdestin farzlarındandır.",
            listOf(
                Move("a_hands", 500, null, true), Move("a_head1", 550), Move("a_head2", 600),
                Move("a_head1", 600), Move("a_sit", 500),
            ),
            MAIDE_6, BodyPart.HEAD,
        ),
        PrayerStep(
            PrayerChapter.ABDEST, "Kulaklarımızı meshediyoruz",
            "Başımızdan sonra ıslak parmaklarımızla neyi meshederiz?",
            listOf("Kulaklarımızı", "Dizlerimizi", "Ayakkabımızı"),
            "Kulaklarımızın içini ve arkasını ıslak parmaklarımızla meshederiz.",
            listOf(Move("a_ear", 600), Move("a_ear2", 400), Move("a_ear", 400), Move("a_ear2", 400), Move("a_sit", 500)),
            "", BodyPart.EARS,
        ),
        PrayerStep(
            PrayerChapter.ABDEST, "Ensemizi meshediyoruz",
            "Kulaklarımızdan sonra ellerimizin dışıyla neyi meshederiz?",
            listOf("Ensemizi", "Dizlerimizi", "Ayak tabanlarımızı"),
            "Ellerimizin dışıyla ensemizi meshederiz. Hanefi mezhebinde ense meshi abdestin edeplerindendir. Ayaklarımızı bundan sonra yıkarız.",
            listOf(Move("a_neck", 650), Move("a_neck2", 450), Move("a_neck", 450), Move("a_sit", 550)),
            "", BodyPart.HEAD,
        ),
        PrayerStep(
            PrayerChapter.ABDEST, "Sağ ayağımızı yıkıyoruz",
            "Ayaklarımızı nereye kadar yıkarız?",
            listOf("Topuklarımızla beraber", "Parmak uçlarına kadar", "Dizlerimize kadar"),
            "Önce sağ ayağımızı topuğumuzla beraber üç kez yıkar, parmak aralarını ovarız. Ayakları topuklarla beraber yıkamak abdestin farzlarındandır.",
            three("a_rfoot", "a_rfoot2") + Move("a_sit", 600),
            MAIDE_6, BodyPart.R_FOOT,
        ),
        PrayerStep(
            PrayerChapter.ABDEST, "Sol ayağımızı yıkıyoruz",
            "Sağ ayağımızdan sonra neyi yıkarız?",
            listOf("Sol ayağımızı", "Yine sağ ayağımızı", "Yüzümüzü"),
            "Sol ayağımızı da topuğumuzla beraber üç kez yıkarız.",
            three("a_lfoot", "a_lfoot2") + Move("a_sit", 600),
            BUHARI_ABDEST, BodyPart.L_FOOT,
        ),
        PrayerStep(
            PrayerChapter.ABDEST, "Abdestimiz tamam!",
            "Abdest bitince ne söylemek güzeldir?",
            listOf("Kelime-i şehâdet", "Sübhâneke", "Âmin"),
            "Abdestten sonra \"Eşhedü en lâ ilâhe illallah ve eşhedü enne Muhammeden abdühû ve rasûlüh\" demek güzeldir.",
            listOf(read("a_sit", Recitations.SEHADET)),
            "Müslim, Tahâret 234", BodyPart.ALL,
        ),

        // ===================== NAMAZ (sabah namazının farzı, 2 rekat) =====================
        PrayerStep(
            PrayerChapter.NAMAZ, "Kıbleye döndük",
            "Namaz kılarken hangi yöne döneriz?",
            listOf("Kıbleye, yani Kâbe'ye doğru", "Güneşe doğru", "Pencereye doğru"),
            "Namazda Mekke'deki Kâbe'ye doğru döneriz. Buna kıble denir. Seccademizdeki mihrap deseninin ucu kıbleyi gösteriyor.",
            listOf(Move("stand", 1200)),
            "Bakara suresi, 144. ayet",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Niyet ettik",
            "Namaza başlamadan önce içimizden ne yaparız?",
            listOf("Hangi namazı kılacağımıza niyet ederiz", "Şarkı söyleriz", "Gözümüzü kapatıp uyuruz"),
            "\"Niyet ettim Allah rızası için bugünkü sabah namazının farzını kılmaya\" diye niyet ederiz. Ameller niyetlere göredir.",
            listOf(read("stand", Recitations.NIYET)),
            "Buhârî, Bed'ü'l-vahy 1",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Başlama tekbiri",
            "Namaza hangi sözle başlarız?",
            listOf("Allâhu Ekber", "Elhamdülillah", "Âmin"),
            "Ellerimizi kaldırıp \"Allâhu Ekber\" deriz ve namaza başlarız. Buna iftitah tekbiri denir.",
            listOf(Move("tekbir", 800, "Allâhu Ekber"), Move("tekbir", 500, "Allâhu Ekber"), Move("kiyam", 700)),
            "Buhârî, Ezan 757",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Sübhâneke'yi okuyoruz",
            "Ellerimizi bağladık. Ayakta ilk okuduğumuz dua hangisidir?",
            listOf("Sübhâneke", "Ettehiyyâtü", "Rabbenâ âtinâ"),
            "Ayakta ellerimizi bağlarız ve Sübhâneke duasını okuruz.",
            listOf(read("kiyam", Recitations.SUBHANEKE)),
            "Müslim, Salât 399",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Fâtiha'yı okuyoruz",
            "Her rekatta mutlaka okunan sure hangisidir?",
            listOf("Fâtiha", "Kevser", "Nâs"),
            "Eûzü besmele çekip Fâtiha suresini okuruz. Fâtiha'yı okumayanın namazı olmaz.",
            listOf(read("kiyam", Recitations.EUZU), read("kiyam", Recitations.FATIHA)),
            "Buhârî, Ezan 756",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Bir sure okuyoruz",
            "Fâtiha'dan sonra ne okuruz?",
            listOf("Kur'an'dan kısa bir sure, örneğin İhlâs", "Ezan", "Hiçbir şey"),
            "Fâtiha'dan sonra Kur'an'dan bir sure ya da birkaç ayet okuruz. Buna zamm-ı sure denir.",
            listOf(read("kiyam", Recitations.IHLAS)),
            "Buhârî, Ezan 757",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Rükûdayız",
            "Şimdi eğilip ellerimizi dizlerimize koyacağız. Bu harekete ne denir?",
            listOf("Rükû", "Secde", "Kıyam"),
            "\"Allâhu Ekber\" diyerek eğiliriz, ellerimizle dizlerimizi tutarız ve üç kez \"Sübhâne rabbiye'l-azîm\" deriz.",
            listOf(Move("ruku", 1000, "Allâhu Ekber"), read("ruku", Recitations.RUKU)),
            "Müslim, Salâtü'l-müsâfirîn 772",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Doğrulduk",
            "Rükûdan doğrulurken ne deriz?",
            listOf("Semi'allâhü limen hamideh", "Sübhâne rabbiye'l-a'lâ", "Esselâmü aleyküm"),
            "Doğrulurken \"Semi'allâhü limen hamideh\", dimdik durunca \"Rabbenâ leke'l-hamd\" deriz.",
            listOf(read("stand", Recitations.SEMI), read("stand", Recitations.RABBENA_LEKEL)),
            "Buhârî, Ezan 789",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Secdedeyiz",
            "Şimdi alnımızı yere koyacağız. Bu harekete ne denir?",
            listOf("Secde", "Rükû", "Selam"),
            "\"Allâhu Ekber\" diyerek önce dizlerimizi, sonra ellerimizi, sonra alnımızı ve burnumuzu yere koyarız. Kul Rabbine en çok secdede yakın olur.",
            listOf(Move("kneel", 900, "Allâhu Ekber"), Move("secde", 900, "Allâhu Ekber")),
            "Müslim, Salât 482",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Secdede tesbih",
            "Secdede ne deriz?",
            listOf("Sübhâne rabbiye'l-a'lâ", "Sübhâne rabbiye'l-azîm", "Semi'allâhü limen hamideh"),
            "Secdede üç kez \"Sübhâne rabbiye'l-a'lâ\" deriz.",
            listOf(read("secde", Recitations.SECDE)),
            "Müslim, Salâtü'l-müsâfirîn 772",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "İki secde arası",
            "İki secde arasında ne yaparız?",
            listOf("Kısa bir süre otururuz", "Ayağa kalkarız", "Selam veririz"),
            "\"Allâhu Ekber\" diyerek doğrulur, biraz otururuz.",
            listOf(Move("sit", 1000, "Allâhu Ekber"), Move("sit", 700)),
            "Buhârî, Ezan 757",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "İkinci secde",
            "Secdede yere neyimiz değer?",
            listOf(
                "Alnımız, burnumuz, ellerimiz, dizlerimiz ve ayak parmaklarımız",
                "Yalnız alnımız",
                "Yalnız ellerimiz",
            ),
            "\"Allâhu Ekber\" deyip ikinci kez secdeye gideriz. Peygamberimiz yedi organ üzerine secde etmeyi emretti.",
            listOf(Move("secde", 1000, "Allâhu Ekber"), read("secde", Recitations.SECDE)),
            "Buhârî, Ezan 812",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "İkinci rekata kalktık",
            "Bir rekatta kaç secde yaparız?",
            listOf("İki", "Bir", "Üç"),
            "Her rekatta iki secde vardır. Şimdi \"Allâhu Ekber\" diyerek ikinci rekat için ayağa kalkıyoruz.",
            listOf(Move("kneel", 900, "Allâhu Ekber"), Move("stand", 900), Move("kiyam", 600)),
            "Buhârî, Ezan 757",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Fâtiha ve bir sure",
            "İkinci rekatta da önce hangi sureyi okuruz?",
            listOf("Fâtiha", "İhlâs", "Hiçbir sure"),
            "İkinci rekatta besmele çekip Fâtiha'yı, ardından bir sure (burada Kevser) okuruz.",
            listOf(read("kiyam", Recitations.BESMELE), read("kiyam", Recitations.FATIHA), read("kiyam", Recitations.KEVSER)),
            "Buhârî, Ezan 756",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Yine rükûdayız",
            "Rükûda sırtımız nasıl olur?",
            listOf("Dümdüz", "Kambur", "Dimdik ayakta"),
            "Rükûda başımızı ne yukarı kaldırırız ne aşağı sarkıtırız; sırtımız dümdüz olur.",
            listOf(Move("ruku", 1000, "Allâhu Ekber"), read("ruku", Recitations.RUKU)),
            "Müslim, Salât 498",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Yine doğrulduk",
            "\"Rabbenâ leke'l-hamd\" ne demektir?",
            listOf("Rabbimiz, hamd sanadır", "Allah en büyüktür", "Selam üzerinize olsun"),
            "Rükûdan doğrulup \"Rabbimiz, hamd sanadır\" anlamına gelen \"Rabbenâ leke'l-hamd\" sözünü söyleriz.",
            listOf(read("stand", Recitations.SEMI), read("stand", Recitations.RABBENA_LEKEL)),
            "Buhârî, Ezan 789",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Yine secdedeyiz",
            "Secdeye giderken ne deriz?",
            listOf("Allâhu Ekber", "Âmin", "Elhamdülillah"),
            "Her eğilip kalkışta \"Allâhu Ekber\" deriz.",
            listOf(Move("kneel", 900, "Allâhu Ekber"), Move("secde", 900), read("secde", Recitations.SECDE)),
            "Buhârî, Ezan 789",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Son secde",
            "İki secde arasında oturduktan sonra ne yaparız?",
            listOf("Yine secdeye gideriz", "Namazı bitiririz", "Ayağa kalkarız"),
            "Kısa oturuştan sonra \"Allâhu Ekber\" deyip ikinci kez secdeye gideriz.",
            listOf(
                Move("sit", 1000, "Allâhu Ekber"), Move("sit", 500),
                Move("secde", 1000, "Allâhu Ekber"), read("secde", Recitations.SECDE),
            ),
            "Buhârî, Ezan 757",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Son oturuş",
            "Sabah namazının farzı iki rekattır. İkinci rekatın secdelerinden sonra ne yaparız?",
            listOf("Otururuz", "Üçüncü rekata kalkarız", "Rükûya gideriz"),
            "İki rekatı bitirdik. \"Allâhu Ekber\" diyerek doğrulur ve son oturuşa otururuz.",
            listOf(Move("sit", 1100, "Allâhu Ekber"), Move("sit", 500)),
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Ettehiyyâtü",
            "Oturuşta ilk hangi duayı okuruz?",
            listOf("Ettehiyyâtü", "Sübhâneke", "Fâtiha"),
            "Oturuşta önce Ettehiyyâtü duasını okuruz.",
            listOf(read("sit", Recitations.TAHIYYAT)),
            "Buhârî, Ezan 831",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Salli ve Bârik",
            "Ettehiyyâtü'den sonra Peygamberimiz için hangi duaları okuruz?",
            listOf("Allâhümme salli ve Allâhümme bârik", "Sübhâneke", "Ezan"),
            "Peygamberimize salavat getiren Allâhümme salli ve Allâhümme bârik dualarını okuruz.",
            listOf(read("sit", Recitations.SALLI), read("sit", Recitations.BARIK)),
            "Buhârî, Enbiyâ 3370",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Rabbenâ duası",
            "Sonra hangi duayı okuruz?",
            listOf("Rabbenâ âtinâ", "Sübhâneke", "Ezan"),
            "Rabbenâ âtinâ ve Rabbenağfirlî dualarını okuruz.",
            listOf(read("sit", Recitations.RABBENA)),
            "Bakara suresi, 201. ayet; İbrâhîm suresi, 41. ayet",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Selam verdik",
            "Namazı nasıl bitiririz?",
            listOf(
                "Önce sağa, sonra sola selam vererek",
                "Hemen ayağa kalkarak",
                "Önce sola, sonra sağa selam vererek",
            ),
            "Başımızı önce sağa, sonra sola çevirip \"Esselâmü aleyküm ve rahmetullah\" deriz. Namazımız tamam!",
            listOf(
                Move("selam_r", 800, "Sağa selam →"), read("selam_r", Recitations.SELAM),
                Move("selam_l", 1000, "← Sola selam"), read("selam_l", Recitations.SELAM),
                Move("sit", 700),
            ),
            "Müslim, Mesâcid 582",
        ),
    )

    /** Namaz bölümünün ilk adımının sırası ("Yalnız namaz" ile buradan başlanır). */
    val NAMAZ_START: Int = STEPS.indexOfFirst { it.chapter == PrayerChapter.NAMAZ }

    /**
     * [index]. adıma gelindiğinde figürün durduğu duruş: bir önceki adımın son hareketi.
     * İlk adımda abdest taburesinde oturuyordur.
     */
    fun restingPose(index: Int): String =
        if (index <= 0) "a_sit" else STEPS[minOf(index, STEPS.size) - 1].moves.last().pose

    /** [index]. adım gösterilirken sahne: önceki adımın bölümü (kıbleye dönünce sahne değişir). */
    fun sceneAt(index: Int): PrayerChapter =
        if (index <= 0) PrayerChapter.ABDEST else STEPS[minOf(index, STEPS.size) - 1].chapter

    /** Bölüm içindeki sıra: (bu bölümde kaçıncı adım, bölümdeki adım sayısı). */
    fun positionInChapter(index: Int): Pair<Int, Int> {
        val ch = STEPS[index].chapter
        val inCh = STEPS.indices.filter { STEPS[it].chapter == ch }
        return Pair(inCh.indexOf(index) + 1, inCh.size)
    }
}
