// =====================================================================================
// duruslar.js — Abdest ve namazdaki bütün duruşlar ve aralarındaki geçiş yolları.
//
// Bir duruş şunlardan oluşur (açılar derece, uzunluklar metre):
//   pel: leğenin yeri, pp/py/pr: leğenin öne eğilmesi / dönmesi / yana yatması
//   sp, cp: bel ve göğsün öne eğilmesi; st: gövdenin dönmesi; sl: yana eğilme
//   np, hp: boyun ve başın öne eğilmesi; hy: başın sağa sola dönmesi; hr: yana yatması
//   fL, fR: ayaklar → b: ayak tarağının (parmak kökünün) yeri, p: topuğun kalkması,
//           y: dışa dönme, t: parmakların kıvrılması, k: dizin baktığı yön
//   aL, aR: eller → at: elin bağlı olduğu yer, p: bileğin yeri ("bağ duruşundaki" koordinat),
//           f: parmakların yönü, n: avucun baktığı yön, k: dirseğin baktığı yön,
//           c/c2: parmakların kıvrılması, th/tf: başparmak
//   gz: bakış, lid: göz kapağı, sm: gülümseme
//
// Hanefi mezhebine göre: erkek tekbirde ellerini kulak hizasına kaldırır, kıyamda göbek
// altında bağlar; kız omuz hizasına kaldırır, göğüs üzerinde bağlar (uygulamanın 2B
// çizimindekiyle aynı).
// =====================================================================================
'use strict';

const elAyna = (a) => a && ({
  ...a, at: a.at.replace(/[LR]$/, (c) => (c === 'L' ? 'R' : 'L')),
  p: [-a.p[0], a.p[1], a.p[2]], f: [-a.f[0], a.f[1], a.f[2]], n: [-a.n[0], a.n[1], a.n[2]], k: [-a.k[0], a.k[1], a.k[2]],
  yay: a.yay && [-a.yay[0], a.yay[1], a.yay[2]],
});
const ayakAyna = (f) => ({ ...f, b: [-f.b[0], f.b[1], f.b[2]], y: -f.y, r: -f.r, k: [-f.k[0], f.k[1], f.k[2]] });
const EL0 = { at: 'gogus', p: [0.18, 0.632, 0.015], f: [0, -1, 0.1], n: [-1, 0, 0], k: [0.4, 0, -1], c: 20, c2: 25, th: 8, tf: 0 };
const AYAK0 = { b: [0.085, 0.014, 0.095], p: 0, y: 7, r: 0, t: 0, k: [0.1, 0, 1] };
const GOVDE0 = { pel: [0, 0.72, 0], pp: 0, py: 0, pr: 0, sp: 0, cp: 0, st: 0, sl: 0, np: 0, hp: 0, hy: 0, hr: 0, gz: [0, -0.3], lid: 0.12, sm: 0.5 };

/** Eksik alanları varsayılanlarla doldurur; yalnız sol el/ayak verildiyse sağını ayna görüntüsü yapar. */
function durus(o) {
  const d = { ...GOVDE0, ...o };
  d.aL = { ...EL0, ...(o.aL || {}) };
  d.aR = o.aR ? { ...elAyna(EL0), ...o.aR } : elAyna(d.aL);
  d.fL = { ...AYAK0, ...(o.fL || {}) };
  d.fR = o.fR ? { ...ayakAyna(AYAK0), ...o.fR } : ayakAyna(d.fL);
  return d;
}

/**
 * Diz üstü duruşların ortak hesabı: ayak parmakları yere basılı ve kıbleye dönük, dizler yerde.
 * u: uyluğun yerle yaptığı açı (90 = kalça dizin tam üstünde, küçüldükçe topuklara doğru oturur)
 * ayakP: topuğun kalkma açısı; pp: gövdenin öne eğimi.
 */
function dizUstu(u, ayakP, pp, ek = {}) {
  const bx = 0.092, dizX = 0.098, dizY = 0.056;
  const bilek = V.sub([bx, 0.014, 0.095], M.td(M.euler(ayakP, 0, 0), TARAK_FARK));
  const dizZ = bilek[2] + Math.sqrt(BALDIR * BALDIR - (bilek[1] - dizY) ** 2 - (dizX - bx) ** 2);
  const uy = Math.sqrt(UYLUK * UYLUK - (dizX - 0.078) ** 2);
  const kalca = [0.078, dizY + uy * Math.sin(u * RAD), dizZ - uy * Math.cos(u * RAD)];
  const pel = [0, kalca[1] + 0.04 * Math.cos(pp * RAD), kalca[2] + 0.04 * Math.sin(pp * RAD)];
  return { pel, pp, fL: { b: [bx, 0.014, 0.095], p: ayakP, y: 0, r: 0, t: -ayakP, k: [0.06, -0.25, 1] }, ...ek };
}
const DIZ_Z = 0.39; // diz üstü duruşlarda dizlerin yere değdiği yer (yaklaşık)

/** Bütün duruşlar. kiz: Zeynep için true. */
function duruslar(kiz) {
  const D = {};
  // Eller uyluğun üstünde, parmak uçları diz hizasında (oturuşta ve çömelirken)
  const elUyluk = (y = 0.475, c = 12) => ({ at: 'uylukL', p: [0.080, y, 0.080], f: [0, -1, -0.04], n: [0, 0, -1], k: [0.7, -0.3, -1], c, c2: c + 4, th: 14 });
  // Eller dizleri kavrar (rükû)
  const elDiz = { at: 'uylukL', p: [0.080, 0.432, 0.064], f: [0.04, -1, -0.10], n: [0, 0, -1], k: [1, 0, -0.4], c: 30, c2: 34, th: -6, tf: 10 };
  // Eller yerde, parmaklar bitişik ve kıbleye dönük (secde)
  const elYer = (z, x = 0.15) => ({ at: 'dunya', p: [x, 0.021, z], f: [-0.04, -0.06, 1], n: [0, -1, 0], k: kiz ? [0.45, -0.9, -0.2] : [1, -0.35, -0.55], c: 2, c2: 3, th: 16 });

  // ---------------- Namaz ----------------
  D.stand = durus({ hp: 9, gz: [0, -0.55], lid: 0.2 });
  D.tekbir = durus({
    hp: 3, gz: [0, -0.35], lid: 0.12,
    aL: kiz
      ? { at: 'gogus', p: [0.128, 0.868, 0.108], f: [0.02, 1, 0.04], n: [0, 0, 1], k: [0.5, -1, -0.35], c: 5, c2: 8, th: 0, yay: [-0.03, 0, 0.08] }
      : { at: 'bas', p: [0.184, 1.050, 0.028], f: [-0.04, 1, 0.02], n: [0, 0, 1], k: [1, -0.55, 0.12], c: 5, c2: 8, th: -4, yay: [-0.10, -0.02, 0.13] },
  });
  // Kıyam: sağ el sol elin üstünde. Erkek göbek altında (sağ el sol bileği kavrar), kız göğüs üstünde.
  D.kiyam = durus(kiz ? {
    hp: 10, gz: [0, -0.6], lid: 0.22,
    aL: { at: 'gogus', p: [0.050, 0.892, 0.096], f: [-1, 0.10, 0.10], n: [0, 0, -1], k: [1, -0.7, -0.5], c: 8, c2: 10, th: 10 },
    aR: { at: 'gogus', p: [-0.066, 0.898, 0.116], f: [1, 0.06, 0.02], n: [0, 0, -1], k: [-1, -0.7, -0.5], c: 12, c2: 14, th: 10 },
  } : {
    hp: 10, gz: [0, -0.6], lid: 0.22,
    aL: { at: 'legen', p: [0.052, 0.748, 0.103], f: [-1, -0.10, 0.06], n: [0, -0.05, -1], k: [1, -0.25, -0.6], c: 8, c2: 10, th: 10, yay: [0, 0, 0.07] },
    aR: { at: 'legen', p: [-0.070, 0.754, 0.124], f: [1, -0.06, 0.0], n: [0, 0, -1], k: [-1, -0.25, -0.6], c: 26, c2: 34, th: 0, tf: 30, yay: [0, 0, 0.09] },
  });
  // Rükû: sırt dümdüz, baş sırt hizasında, eller dizleri kavrar, bacaklar dik
  D.ruku = durus({ pel: [0, 0.72, -0.155], pp: 73, sp: 8, cp: 6, np: -3, hp: -4, gz: [0, -0.5], lid: 0.2, aL: elDiz });
  // Çömelme: secdeye inerken ve kalkarken ara duruş (dizler bükülü, topuklar kalkık)
  D.comel = durus({
    pel: [0, 0.43, -0.07], pp: 34, sp: 8, cp: 5, np: -4, hp: -6, gz: [0, -0.5], lid: 0.2,
    fL: { ...AYAK0, p: 30, t: -30, y: 3, k: [0.12, 0, 1] }, aL: elUyluk(0.455, 22),
  });
  // Diz çökme (secdeye inerken): dizler yerde, gövde hafif öne eğik, eller uylukta
  D.kneel = durus({ ...dizUstu(52, 64, 20), sp: 6, cp: 4, hp: 2, gz: [0, -0.55], lid: 0.2, aL: elUyluk(0.46, 16) });
  // Eller yere konmuş, baş henüz yukarıda (dizlerden sonra eller, sonra burun ve alın)
  D.elyerde = durus({ ...dizUstu(72, 68, 74), sp: 10, cp: 8, np: -12, hp: -22, gz: [0, -0.5], lid: 0.2, aL: elYer(DIZ_Z + 0.30) });
  // Secde: alın ve burun yerde, baş iki elin arasında, dirsekler yerden kalkık (erkek),
  // karın uyluklardan uzak, ayak parmakları kıbleye dönük
  D.secde = durus({ ...dizUstu(kiz ? 74 : 86, 70, kiz ? 104 : 108), sp: 11, cp: 9, np: -14, hp: -20, gz: [0, -0.2], lid: 0.55, aL: elYer(DIZ_Z + 0.31, kiz ? 0.135 : 0.155) });
  // Secdeden doğrulma anı: baş kalkmış, eller yerden ayrılmak üzere
  D.dogrul = durus({ ...dizUstu(58, 66, 58), sp: 9, cp: 7, np: -8, hp: -12, gz: [0, -0.5], lid: 0.25, aL: elYer(DIZ_Z + 0.24) });
  // Kalkış (Hanefi): eller dizlere dayanır, önce baş, sonra eller, sonra dizler yerden kalkar
  D.kalk1 = durus({ ...dizUstu(40, 62, 34), sp: 9, cp: 6, np: -6, hp: -8, gz: [0, -0.5], lid: 0.2, aL: { ...elDiz, p: [0.080, 0.445, 0.066], c: 22, c2: 26 } });
  D.kalk2 = durus({ pel: [0, 0.47, -0.085], pp: 46, sp: 9, cp: 6, np: -6, hp: -10, gz: [0, -0.5], lid: 0.2, fL: { ...AYAK0, p: 22, t: -22, y: 3, k: [0.12, 0, 1] }, aL: { ...elDiz, c: 24, c2: 28 } });

  // Oturuş. Erkek: sol ayağını yatırıp üzerine oturur, sağ ayağını diker (parmakları kıbleye).
  // Kız: iki ayağını sağ yana çıkarıp yere oturur (teverrük).
  if (!kiz) {
    D.sit = durus({
      pel: [0, 0.182, 0.085], pp: 2, sp: 4, cp: 3, hp: 16, gz: [0, -0.6], lid: 0.25,
      fL: { b: [0.035, 0.050, -0.012], p: 158, y: 32, r: 0, t: 8, k: [0.12, 0.02, 1] },
      fR: { b: [-0.158, 0.014, 0.070], p: 72, y: -12, r: 0, t: -70, k: [-0.1, -0.28, 1] },
      aL: elUyluk(0.47, 10),
    });
  } else {
    D.sit = durus({
      pel: [0.07, 0.132, 0.08], pp: 2, pr: 3, sp: 4, cp: 3, sl: -4, hp: 16, gz: [0, -0.6], lid: 0.25,
      fL: { b: [-0.13, 0.040, 0.13], p: 150, y: 74, r: 0, t: 8, k: [0.05, -0.08, 1] },
      fR: { b: [-0.30, 0.040, 0.03], p: 150, y: 70, r: 0, t: 8, k: [0.0, -0.08, 1] },
      aL: elUyluk(0.47, 10),
    });
  }
  // Selam: baş önce sağa, sonra sola döner; bakış omuza doğru
  D.selam_r = { ...D.sit, hy: -68, st: -7, hp: 8, gz: [-0.35, -0.35], lid: 0.15 };
  D.selam_l = { ...D.sit, hy: 68, st: 7, hp: 8, gz: [0.35, -0.35], lid: 0.15 };

  // ---------------- Abdest: alçak tabureye oturmuş, musluk önde ----------------
  const SU = [0, 0.40, 0.445]; // suyun ellere değdiği yer
  const otur = { pel: [0, 0.372, 0], pp: 12, sp: 9, cp: 7, np: -4, hp: 2, gz: [0, -0.45], lid: 0.12, fL: { b: [0.135, 0.014, 0.325], p: 0, y: 12, r: 0, t: 0, k: [0.45, 0.3, 1] } };
  const dizde = { at: 'uylukL', p: [0.080, 0.455, 0.070], f: [0, -1, -0.05], n: [0, 0, -1], k: [0.8, -0.2, -0.8], c: 14, c2: 18, th: 12 };
  const suda = (dz = 0, dy = 0, dx = 0) => ({ at: 'dunya', p: [0.052 + dx, SU[1] + 0.035 + dy, SU[2] - 0.085 + dz], f: [-0.42, -0.36, 0.84], n: [-0.80, -0.50, -0.30], k: [0.9, -0.6, -0.2], c: 12, c2: 14, th: 6 });
  const A = (o) => durus({ ...otur, ...o });
  D.a_sit = A({ aL: dizde });
  D.a_hands = A({ pp: 16, sp: 11, hp: 8, gz: [0, -0.7], aL: suda(0.012, 0.004), aR: elAyna(suda(-0.012, -0.004)) });
  D.a_hands2 = A({ pp: 16, sp: 11, hp: 8, gz: [0, -0.7], aL: suda(-0.016, -0.006), aR: elAyna(suda(0.016, 0.006)) });
  // Sağ el avuç içiyle ağza / burna su götürür
  const yuzeEl = (p, ek = {}) => ({ at: 'bas', p, f: [0.30, 0.86, -0.42], n: [0.25, 0.35, -1], k: [-0.7, -1, 0.1], c: 30, c2: 26, th: 14, ...ek });
  D.a_mouth = A({ hp: 4, gz: [0, -0.3], lid: 0.3, aL: dizde, aR: yuzeEl([-0.045, 1.012, 0.178]) });
  D.a_mouth2 = A({ hp: -5, gz: [0, 0], lid: 0.5, aL: dizde, aR: yuzeEl([-0.05, 1.0, 0.20], { c: 22 }) });
  D.a_nose = A({ hp: 6, gz: [0, -0.3], lid: 0.4, aL: dizde, aR: yuzeEl([-0.042, 1.035, 0.186]) });
  D.a_nose2 = A({ hp: 0, gz: [0, -0.2], lid: 0.55, aL: dizde, aR: yuzeEl([-0.046, 1.022, 0.20], { c: 24 }) });
  // Yüz: iki el alından çeneye iner
  const yuzde = (y, z) => ({ at: 'bas', p: [0.050, y, z], f: [-0.10, 1, -0.30], n: [0.05, 0.3, -1], k: [0.8, -1, 0.1], c: 10, c2: 12, th: 4 });
  D.a_face1 = A({ hp: 10, gz: [0, -0.2], lid: 1, aL: yuzde(1.090, 0.158) });
  D.a_face2 = A({ hp: 14, gz: [0, -0.2], lid: 1, aL: yuzde(1.008, 0.160) });
  // Kol: uzatılan kol suyun altında, öbür el bilekten dirseğe ovar
  const uzanan = { at: 'dunya', p: [-0.045, SU[1] + 0.03, SU[2] - 0.05], f: [0.10, -0.30, 1], n: [0.85, 0.5, 0.05], k: [-0.5, -1, -0.1], c: 8, c2: 10, th: 6 };
  const ovan = (y) => ({ at: 'onkolR', p: [-0.14 + 0.046, y, -0.012], f: [0.10, 0.12, 1], n: [-1, 0, 0.1], k: [0.9, -0.7, -0.1], c: 46, c2: 44, th: 0, tf: 20 });
  const govdeKol = { pp: 17, sp: 11, hp: 10, gz: [-0.15, -0.7] };
  D.a_rarm1 = A({ ...govdeKol, aR: uzanan, aL: ovan(0.655) });
  D.a_rarm2 = A({ ...govdeKol, aR: uzanan, aL: ovan(0.775) });
  D.a_larm1 = A({ ...govdeKol, gz: [0.15, -0.7], aL: elAyna(uzanan), aR: elAyna(ovan(0.655)) });
  D.a_larm2 = A({ ...govdeKol, gz: [0.15, -0.7], aL: elAyna(uzanan), aR: elAyna(ovan(0.775)) });
  // Baş meshi: ıslak eller başın önünden arkasına sıvazlar
  D.a_head1 = A({ hp: 12, gz: [0, -0.4], lid: 0.3, aL: { at: 'bas', p: [0.052, 1.246, 0.120], f: [-0.05, 0.42, -1], n: [0, -1, -0.4], k: [1, -0.25, 0.3], c: 14, c2: 16, th: 6 } });
  D.a_head2 = A({ hp: 16, gz: [0, -0.4], lid: 0.3, aL: { at: 'bas', p: [0.056, 1.292, 0.000], f: [-0.05, -0.30, -1], n: [0, -1, 0.3], k: [1, -0.1, 0.1], c: 22, c2: 22, th: 6 } });
  // Kulak meshi: parmaklar kulakta
  const kulak = (dy, e) => ({ at: 'bas', p: [0.176, 1.066 + dy, 0.020], f: [-0.34 - e, 1, -0.10 + e], n: [-1, -0.35, 0], k: [1, -0.75, 0.2], c: 26, c2: 30, th: 10 });
  D.a_ear = A({ hp: 4, lid: 0.25, aL: kulak(0, 0) });
  D.a_ear2 = A({ hp: 4, lid: 0.25, aL: kulak(0.014, 0.16) });
  // Ense meshi: ellerin dışı enseye
  const ense = (x, y) => ({ at: 'bas', p: [x, y, -0.118], f: [-0.86, 0.22, -0.42], n: [0.35, 0.1, -1], k: [1, 0.15, -0.35], c: 12, c2: 14, th: 6 });
  D.a_neck = A({ hp: 14, lid: 0.3, aL: ense(0.118, 1.058) });
  D.a_neck2 = A({ hp: 14, lid: 0.3, aL: ense(0.134, 1.034) });
  // Ayak: ayak suyun altına uzatılır, sol el ovar (parmak aralarıyla birlikte)
  const ayakYika = (sag, ileri) => {
    const uz = { b: [-0.06, 0.105, 0.455], p: -12, y: -4, r: 0, t: ileri ? -8 : 6, k: [-0.25, 1, 0.2] };
    const el = { at: 'ayakR', p: ileri ? [-0.052, 0.118, 0.045] : [-0.050, 0.150, -0.005], f: [-0.25, -0.55, 0.80], n: [0.1, -0.8, -0.55], k: [0.8, -0.6, -0.2], c: 30, c2: 30, th: 10 };
    const g = { pel: [0, 0.372, 0.01], pp: 40, sp: 17, cp: 12, np: -8, hp: 4, gz: [sag ? -0.2 : 0.2, -0.7] };
    return sag
      ? A({ ...g, st: -6, fR: uz, fL: otur.fL, aL: el, aR: elAyna(dizde) })
      : A({ ...g, st: 6, fL: ayakAyna(uz), fR: ayakAyna(otur.fL), aL: { ...elAyna(el), at: 'ayakL', k: [0.5, -1, -0.3] }, aR: elAyna(dizde) });
  };
  D.a_rfoot = ayakYika(true, false); D.a_rfoot2 = ayakYika(true, true);
  D.a_lfoot = ayakYika(false, false); D.a_lfoot2 = ayakYika(false, true);

  // Secdede alın tam yere değsin: gövde eğimini küçük adımlarla ayarla
  for (let i = 0; i < 24; i++) {
    const r = coz(D.secde), bas = M.tp(r.W[K.BAS], [0, 0.11, 0.008]), on = M.tp(r.W[K.BAS], [0, 0.155, 0.118]);
    const fark = on[1] - 0.022; // alnın yerden yüksekliği
    if (Math.abs(fark) < 0.002) break;
    D.secde.pp += fark * 160;
    D.secde.pel = dizUstu(kiz ? 74 : 86, 70, D.secde.pp).pel;
  }
  // Secdede ellerin yeri: baş iki elin arasında, başparmaklar kulak hizasında
  {
    const r = coz(D.secde), bas = M.tp(r.W[K.BAS], [0, 0.11, 0.008]);
    for (const s of ['aL', 'aR']) D.secde[s] = { ...D.secde[s], p: [D.secde[s].p[0], 0.021, bas[2] - 0.075] };
  }
  return D;
}

// =====================================================================================
// Geçişler
// =====================================================================================

/**
 * Bir duruştan ötekine giderken uğranan ara duruşlar ve süreleri (saniye).
 * "gec": o parçada hangi bölümün ne zaman hareket ettiği (0..1 arası başlangıç-bitiş);
 * örneğin secdeye inerken önce gövde eğilir, eller biraz sonra yere varır.
 */
const YOLLAR = {
  'stand>tekbir': [['tekbir', 0.95]],
  'kiyam>tekbir': [['tekbir', 0.95]],
  'tekbir>kiyam': [['kiyam', 1.1, { kol: [0.0, 1], bas: [0.2, 1] }]],
  'stand>kiyam': [['kiyam', 0.9]],
  'kiyam>stand': [['stand', 0.9]],
  'kiyam>ruku': [['ruku', 1.5, { kol: [0.12, 1], bas: [0, 0.9] }]],
  'stand>ruku': [['ruku', 1.4, { kol: [0.1, 1] }]],
  'ruku>stand': [['stand', 1.5, { kol: [0.15, 1], bas: [0.1, 1] }]],
  'ruku>kiyam': [['stand', 1.4, { kol: [0.15, 1] }], ['kiyam', 0.8]],
  // Secdeye iniş: önce dizler, sonra eller, sonra burun ve alın
  'stand>kneel': [['comel', 0.95, { kol: [0.05, 0.95] }], ['kneel', 0.8]],
  'kiyam>kneel': [['comel', 1.0, { kol: [0.05, 0.95] }], ['kneel', 0.8]],
  'kneel>secde': [['elyerde', 0.9, { kol: [0.0, 0.85], bas: [0.1, 1] }], ['secde', 0.85]],
  'stand>secde': [['comel', 0.95, { kol: [0.05, 0.95] }], ['kneel', 0.75], ['elyerde', 0.85, { kol: [0, 0.85] }], ['secde', 0.85]],
  // Secdeden oturuşa: önce baş, sonra eller kalkar
  'secde>sit': [['dogrul', 0.8, { bas: [0, 0.8] }], ['sit', 1.1, { kol: [0.0, 0.8], bacak: [0.1, 1] }]],
  'sit>secde': [['elyerde', 1.0, { kol: [0.05, 0.9], bacak: [0, 0.8] }], ['secde', 0.85]],
  // İkinci rekata kalkış (oturmadan, ayak parmakları üzerinde, eller dizlerde)
  'secde>kneel': [['dogrul', 0.8, { bas: [0, 0.8] }], ['kalk1', 0.85, { kol: [0, 0.85] }]],
  'kneel>stand': [['kalk2', 0.85], ['stand', 1.25, { kol: [0.25, 1], bas: [0.2, 1] }]],
  'secde>stand': [['dogrul', 0.8], ['kalk1', 0.8], ['kalk2', 0.85], ['stand', 1.25, { kol: [0.25, 1] }]],
  'sit>selam_r': [['selam_r', 1.0]],
  'selam_r>selam_l': [['selam_l', 1.5]],
  'selam_l>sit': [['sit', 1.0]],
  'selam_r>sit': [['sit', 1.0]],
};

/** [from] duruşundan [to] duruşuna yol: [[duruşAdı, süre, gec], ...]. Tanımlı değilse tek parça. */
function yolBul(from, to, ms) {
  const y = YOLLAR[from + '>' + to];
  if (y) return y;
  // Abdest hareketleri: oyunun verdiği süre biraz yavaşlatılarak kullanılır
  const s = Math.max(0.45, (ms || 700) / 1000 * 1.35);
  return [[to, s]];
}

/** İki duruşun arası. t: 0..1. gec: bölümlerin zaman pencereleri. */
function karistir(A, B, t, gec) {
  gec = gec || {};
  const pen = (ad) => { const g = gec[ad]; return smooth(g ? (t - g[0]) / (g[1] - g[0]) : t); };
  const tg = pen('govde'), tb = pen('bas'), tk = pen('kol'), tl = pen('bacak');
  const L = (k, tt) => lerp(A[k], B[k], tt);
  const ayak = (a, b) => ({ b: V.lerp(a.b, b.b, tl), p: lerp(a.p, b.p, tl), y: lerp(a.y, b.y, tl), r: lerp(a.r, b.r, tl), t: lerp(a.t, b.t, tl), k: V.lerp(a.k, b.k, tl) });
  return {
    pel: V.lerp(A.pel, B.pel, tg), pp: L('pp', tg), py: L('py', tg), pr: L('pr', tg), sp: L('sp', tg), cp: L('cp', tg), st: L('st', tg), sl: L('sl', tg),
    np: L('np', tb), hp: L('hp', tb), hy: L('hy', tb), hr: L('hr', tb),
    gz: [lerp(A.gz[0], B.gz[0], tb), lerp(A.gz[1], B.gz[1], tb)], lid: L('lid', tb), sm: L('sm', tb),
    fL: ayak(A.fL, B.fL), fR: ayak(A.fR, B.fR),
    aL: { A: A.aL, B: B.aL, t: tk }, aR: { A: A.aR, B: B.aR, t: tk },
  };
}
