package com.aydinmtl.namazvakitleri

import java.util.Locale

/**
 * Ülke adlarını seçili dilde yazmak için: Diyanet'in ülke numarasından uluslararası (ISO) ülke
 * koduna. Kod bilinince ülkenin adı her dilde telefonun kendi sözlüğünden alınır, internet
 * gerekmez: "CH" → Türkçe "İsviçre", Fransızca "Suisse", Almanca "Schweiz", İngilizce "Switzerland".
 *
 * Android'e bağlı değildir; bilgisayarda test edilir (bkz. PlaceNamesTest).
 */
object CountryNames {
    // Diyanet ülke numarası:ISO kodu. Diyanet'in 15 Kasım 2026 tarihli listesinden üretildi
    // (202 ülkenin 199'u). Uluslararası kodu olmayan üç yer (Kuzey Kıbrıs, Kırım, Çeçenistan) burada
    // yoktur; onlar Diyanet'in verdiği adla gösterilir. Diyanet listeye yeni bir ülke eklerse o da
    // Diyanet'in adıyla görünür; buraya satır eklemek yeterlidir.
    private const val TABLE =
        "2:TR,3:MC,4:NL,5:AZ,6:EE,7:HU,8:IT,9:BA,10:VA,11:BE,12:SE,13:DE,14:SK,15:GB," +
            "16:CZ,17:AD,19:SI,20:LV,21:FR,22:GR,23:ES,24:MT,25:AL,26:DK,27:RS,28:MK,30:HR,31:LU," +
            "32:IE,33:US,34:ME,35:AT,36:NO,37:RO,38:LI,39:PL,40:UA,41:FI,42:LB,44:BG,45:PT,46:MD," +
            "47:LT,48:GL,49:CH,51:BM,52:CA,53:MX,54:BS,55:MW,56:VU,57:CO,58:GD,59:AU,60:MN,61:CN," +
            "62:GE,63:GQ,64:SA,65:BI,66:CW,67:ZA,68:PR,69:PE,70:HT,71:TG,72:DO,73:LR,74:LK,75:UG," +
            "76:NP,77:PK,79:GA,80:CF,81:RW,82:GY,83:BO,84:NE,85:FM,86:DZ,87:MQ,88:KM,89:PA,90:AG," +
            "91:BF,92:KZ,93:AE,94:QA,95:ET,96:TT,97:BN,98:MG,99:GT,101:TJ,102:SN,103:MV,104:AM,105:HN," +
            "106:MR,107:MY,108:TW,109:GM,110:TZ,111:GN,112:RE,114:KE,115:NC,116:JP,117:ID,118:TN,119:JM,120:CI," +
            "122:IS,123:DM,124:IQ,125:AI,126:PH,127:NG,128:KR,129:SD,130:TO,131:UZ,132:BH,133:KW,134:LA,135:VN," +
            "136:LC,137:TH,138:SC,139:EC,140:AO,141:NI,142:KP,143:GH,145:MA,146:BR,147:MS,148:YE,149:PW,150:SO," +
            "151:MZ,152:ML,153:AW,155:BT,156:TD,157:YT,158:ZM,159:TM,160:DJ,161:KH,162:CR,164:MU,165:SV,166:AF," +
            "167:BW,168:KG,169:GU,170:SZ,171:GP,172:SR,173:OM,174:LS,175:ER,176:TL,177:BD,178:NU,179:SG,181:BJ," +
            "182:BZ,183:PN,184:CM,185:PG,186:VE,187:IN,188:BB,189:EG,191:SY,192:JO,193:NZ,194:PY,196:NA,197:FJ," +
            "198:WS,199:AR,200:CL,201:UY,202:IR,203:LY,207:RU,208:BY,209:CU,18:XK,100:MO,113:HK,144:CV,154:MM," +
            "163:SJ,180:CD,204:PS"

    private val byId: Map<String, String> by lazy {
        TABLE.split(',').associate { it.substringBefore(':') to it.substringAfter(':') }
    }

    /** Diyanet ülke numarasının ISO kodu ("49" → "CH"); bilinmiyorsa null. */
    fun code(countryId: String?): String? = byId[countryId]

    /** [code] ülkesinin [locale] dilindeki adı ("CH", Fransızca → "Suisse"); bilinmiyorsa null. */
    fun display(code: String?, locale: Locale): String? {
        if (code.isNullOrEmpty()) return null
        val name = try {
            Locale.Builder().setRegion(code).build().getDisplayCountry(locale)
        } catch (e: Exception) {
            ""
        }
        // Sözlükte adı yoksa Java kodun kendisini döndürür ("XX"); o zaman bilinmiyor sayılır.
        return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
    }
}

/**
 * Yer etiketini ("Cenevre, İsviçre") seçili dilde yazmak için saf mantık.
 *
 * Diyanet yer adlarını tek biçimde verir (çoğu Türkçe: "Cenevre", "Lozan"). Seçili dildeki adı
 * ("Genève", "Genf", "Geneva") telefonun yer adı hizmeti bulur (bkz. PlaceLabels). O henüz
 * cevap vermediyse ya da internet yoksa en azından ülke adı seçili dilde yazılır.
 */
object PlaceNames {

    /**
     * Etiketin son parçası ülke adıysa ([secondIsCountry]) onu [locale] dilinde yazar:
     * "Cenevre, İsviçre" → "Cenevre, Suisse". Ülke kodu bilinmiyorsa etiket olduğu gibi kalır.
     * Türkiye'deki yerlerde son parça il adıdır ("Kadıköy, İstanbul"); onlara dokunulmaz.
     */
    fun withLocalCountry(base: String, countryCode: String?, secondIsCountry: Boolean, locale: Locale): String {
        if (!secondIsCountry) return base
        val country = CountryNames.display(countryCode, locale) ?: return base
        val cut = base.lastIndexOf(", ")
        return if (cut < 0) base else base.substring(0, cut) + ", " + country
    }

    /**
     * Yer adı hizmetinin verdiği adlardan etiket kurar: ("Genève", "Suisse") → "Genève, Suisse".
     * [base] tek parçalıysa (ülke yazılmamışsa) yalnızca yer adı döner. Yer adı yoksa null.
     */
    fun compose(base: String, local: String?, country: String?): String? {
        val place = local?.trim().orEmpty()
        if (place.isEmpty()) return null
        val land = country?.trim().orEmpty()
        return if (!base.contains(", ") || land.isEmpty() || norm(land) == norm(place)) place else "$place, $land"
    }
}
