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
 * Canlandırmanın bir karesi: figür [ms] milisaniyede [pose] duruşuna geçer.
 * [caption] verilirse figürün üstündeki konuşma balonunda o söz yazar ("Allâhu Ekber").
 * [water]: musluk akıyor mu.
 */
data class Move(val pose: String, val ms: Long = 700, val caption: String? = null, val water: Boolean = false)

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
            listOf(Move("a_sit", 600, "Bismillâhirrahmânirrahîm")),
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
            listOf(Move("a_sit", 1600, "Eşhedü en lâ ilâhe illallah…")),
            "Müslim, Tahâret 234", BodyPart.ALL,
        ),

        // ===================== NAMAZ (sabah namazının farzı, 2 rekat) =====================
        PrayerStep(
            PrayerChapter.NAMAZ, "Kıbleye döndük",
            "Namaz kılarken hangi yöne döneriz?",
            listOf("Kıbleye, yani Kâbe'ye doğru", "Güneşe doğru", "Pencereye doğru"),
            "Namazda Mekke'deki Kâbe'ye doğru döneriz. Buna kıble denir. Seccademizin önündeki mihrap kıbleyi gösteriyor.",
            listOf(Move("stand", 1200)),
            "Bakara suresi, 144. ayet",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Niyet ettik",
            "Namaza başlamadan önce kalbimizden ne yaparız?",
            listOf("Hangi namazı kılacağımıza niyet ederiz", "Şarkı söyleriz", "Gözümüzü kapatıp uyuruz"),
            "\"Niyet ettim Allah rızası için bugünkü sabah namazının farzını kılmaya\" diye niyet ederiz. Ameller niyetlere göredir.",
            listOf(Move("stand", 1400, "Niyet ettim…")),
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
            listOf(Move("kiyam", 1600, "Sübhânekellâhümme ve bi-hamdik…")),
            "Müslim, Salât 399",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Fâtiha'yı okuyoruz",
            "Her rekatta mutlaka okunan sure hangisidir?",
            listOf("Fâtiha", "Kevser", "Nâs"),
            "Eûzü besmele çekip Fâtiha suresini okuruz. Fâtiha'yı okumayanın namazı olmaz.",
            listOf(Move("kiyam", 1600, "Elhamdü lillâhi rabbi'l-âlemîn…")),
            "Buhârî, Ezan 756",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Bir sure okuyoruz",
            "Fâtiha'dan sonra ne okuruz?",
            listOf("Kur'an'dan kısa bir sure, örneğin İhlâs", "Ezan", "Hiçbir şey"),
            "Fâtiha'dan sonra Kur'an'dan bir sure ya da birkaç ayet okuruz. Buna zamm-ı sure denir.",
            listOf(Move("kiyam", 1600, "Kul hüvallâhü ehad…")),
            "Buhârî, Ezan 757",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Rükûdayız",
            "Şimdi eğilip ellerimizi dizlerimize koyacağız. Bu harekete ne denir?",
            listOf("Rükû", "Secde", "Kıyam"),
            "\"Allâhu Ekber\" diyerek eğiliriz, ellerimizle dizlerimizi tutarız ve üç kez \"Sübhâne rabbiye'l-azîm\" deriz.",
            listOf(Move("ruku", 1000, "Allâhu Ekber"), Move("ruku", 1600, "Sübhâne rabbiye'l-azîm (3 kez)")),
            "Müslim, Salâtü'l-müsâfirîn 772",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Doğrulduk",
            "Rükûdan doğrulurken ne deriz?",
            listOf("Semi'allâhü limen hamideh", "Sübhâne rabbiye'l-a'lâ", "Esselâmü aleyküm"),
            "Doğrulurken \"Semi'allâhü limen hamideh\", dimdik durunca \"Rabbenâ leke'l-hamd\" deriz.",
            listOf(Move("stand", 1000, "Semi'allâhü limen hamideh"), Move("stand", 1300, "Rabbenâ leke'l-hamd")),
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
            listOf(Move("secde", 1700, "Sübhâne rabbiye'l-a'lâ (3 kez)")),
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
            listOf(Move("secde", 1000, "Allâhu Ekber"), Move("secde", 1500, "Sübhâne rabbiye'l-a'lâ (3 kez)")),
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
            "İkinci rekatta da önce Fâtiha'yı, ardından bir sure okuruz.",
            listOf(Move("kiyam", 1500, "Elhamdü lillâhi…"), Move("kiyam", 1200, "Kul e'ûzü bi-rabbi'l-felak…")),
            "Buhârî, Ezan 756",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Yine rükûdayız",
            "Rükûda sırtımız nasıl olur?",
            listOf("Dümdüz", "Kambur", "Dimdik ayakta"),
            "Rükûda başımızı ne yukarı kaldırırız ne aşağı sarkıtırız; sırtımız dümdüz olur.",
            listOf(Move("ruku", 1000, "Allâhu Ekber"), Move("ruku", 1500, "Sübhâne rabbiye'l-azîm (3 kez)")),
            "Müslim, Salât 498",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Yine doğrulduk",
            "\"Rabbenâ leke'l-hamd\" ne demektir?",
            listOf("Rabbimiz, hamd sanadır", "Allah en büyüktür", "Selam üzerinize olsun"),
            "Rükûdan doğrulup \"Rabbimiz, hamd sanadır\" anlamına gelen \"Rabbenâ leke'l-hamd\" sözünü söyleriz.",
            listOf(Move("stand", 1000, "Semi'allâhü limen hamideh"), Move("stand", 1200, "Rabbenâ leke'l-hamd")),
            "Buhârî, Ezan 789",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Yine secdedeyiz",
            "Secdeye giderken ne deriz?",
            listOf("Allâhu Ekber", "Âmin", "Elhamdülillah"),
            "Her eğilip kalkışta \"Allâhu Ekber\" deriz.",
            listOf(Move("kneel", 900, "Allâhu Ekber"), Move("secde", 900), Move("secde", 1300, "Sübhâne rabbiye'l-a'lâ (3 kez)")),
            "Buhârî, Ezan 789",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Son secde",
            "İki secde arasında oturduktan sonra ne yaparız?",
            listOf("Yine secdeye gideriz", "Namazı bitiririz", "Ayağa kalkarız"),
            "Kısa oturuştan sonra \"Allâhu Ekber\" deyip ikinci kez secdeye gideriz.",
            listOf(
                Move("sit", 1000, "Allâhu Ekber"), Move("sit", 500),
                Move("secde", 1000, "Allâhu Ekber"), Move("secde", 1300, "Sübhâne rabbiye'l-a'lâ (3 kez)"),
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
            listOf(Move("sit", 1700, "Ettehiyyâtü lillâhi…")),
            "Buhârî, Ezan 831",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Salli ve Bârik",
            "Ettehiyyâtü'den sonra Peygamberimiz için hangi duaları okuruz?",
            listOf("Allâhümme salli ve Allâhümme bârik", "Sübhâneke", "Ezan"),
            "Peygamberimize salavat getiren Allâhümme salli ve Allâhümme bârik dualarını okuruz.",
            listOf(Move("sit", 1500, "Allâhümme salli…"), Move("sit", 1300, "Allâhümme bârik…")),
            "Buhârî, Enbiyâ 3370",
        ),
        PrayerStep(
            PrayerChapter.NAMAZ, "Rabbenâ duası",
            "Sonra hangi duayı okuruz?",
            listOf("Rabbenâ âtinâ", "Sübhâneke", "Ezan"),
            "\"Rabbenâ âtinâ fi'd-dünyâ haseneten ve fi'l-âhireti haseneten ve kınâ azâbe'n-nâr\" diye dua ederiz.",
            listOf(Move("sit", 1700, "Rabbenâ âtinâ…")),
            "Bakara suresi, 201. ayet",
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
                Move("selam_r", 900, "Esselâmü aleyküm ve rahmetullah →"), Move("selam_r", 700, "Esselâmü aleyküm ve rahmetullah →"),
                Move("selam_l", 1100, "← Esselâmü aleyküm ve rahmetullah"), Move("selam_l", 700, "← Esselâmü aleyküm ve rahmetullah"),
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
