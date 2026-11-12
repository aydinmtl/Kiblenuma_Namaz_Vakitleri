// =====================================================================================
// karakter.js — Çocuk figürü (Ahmet / Zeynep): iskelet, kodla kurulan 3B gövde ve
// duruşu kemik hareketine çeviren çözücü.
//
// Koordinatlar (metre): +Y yukarı, +Z çocuğun önü (kıble), +X çocuğun SOLU.
// "Bağ duruşu": çocuk ayakta, kollar yanda dümdüz aşağı. Gövde bu duruşta kurulur;
// her köşe bir ya da iki kemiğe bağlıdır ve kemikler oynadıkça onlarla birlikte taşınır.
// =====================================================================================
'use strict';

// Kemik numaraları
const K = {
  KOK: 0, LEGEN: 1, BEL: 2, GOGUS: 3, BOYUN: 4, BAS: 5,
  UKOL_L: 6, ONKOL_L: 7, EL_L: 8, PA_L: 9, PB_L: 10, BA_L: 11, BB_L: 12,
  UKOL_R: 13, ONKOL_R: 14, EL_R: 15, PA_R: 16, PB_R: 17, BA_R: 18, BB_R: 19,
  UYL_L: 20, BAL_L: 21, AYK_L: 22, PRM_L: 23, UYL_R: 24, BAL_R: 25, AYK_R: 26, PRM_R: 27,
};

// Eklem yerleri (bağ duruşunda, sol taraf; sağ taraf x'in eksilisi)
const E = {
  legen: [0, 0.70, 0], bel: [0, 0.745, 0], gogus: [0, 0.84, 0], boyun: [0, 0.995, 0], bas: [0, 1.04, 0],
  basMerkez: [0, 1.15, 0.008], basR: [0.118, 0.125, 0.122],
  omuz: [0.14, 0.965, 0], dirsek: [0.14, 0.785, 0], bilek: [0.14, 0.625, 0],
  parmakA: [0.14, 0.567, 0], parmakB: [0.14, 0.540, 0],
  basparA: [0.14, 0.607, 0.020], basparYon: V.norm([0, -0.75, 0.66]), basparBoy: 0.028,
  kalca: [0.078, 0.66, 0], diz: [0.078, 0.36, 0], ayak: [0.078, 0.06, 0], tarak: [0.078, 0.014, 0.095],
};
const UKOL = 0.18, ONKOL = 0.16, UYLUK = 0.30, BALDIR = 0.30;
const TARAK_FARK = [0, E.tarak[1] - E.ayak[1], E.tarak[2] - E.ayak[2]]; // bilekten ayak tarağına

/** Başta saç/örtü sınırı. motor.js içindeki basMaske ile AYNI formül. */
function basMaske(d, kiz) {
  if (!kiz) {
    const f = clamp((d[2] + 0.25) / 0.8, 0, 1), s = f * f * (3 - 2 * f);
    return d[1] - lerp(-0.42, 0.50 + 0.05 * Math.sin(d[0] * 10 + 1), s);
  }
  return d[2] > 0 ? Math.hypot(d[0] / 0.78, (d[1] + 0.10) / 0.72) - 1 : 1;
}

/** Renkler: erkek krem gömlek + mavi pantolon + takke; kız mor elbise + başörtüsü. */
function renkler(kiz) {
  return kiz
    ? { ust: hex('#9B7BD4'), ustKoyu: hex('#7A5CB3'), alt: hex('#6F52A8'), ortu: hex('#F1E4EE'), ten: hex('#F2C29B') }
    : { ust: hex('#EFE9DA'), ustKoyu: hex('#D2C9B2'), alt: hex('#44689C'), ortu: hex('#F6F3EA'), ten: hex('#F2C29B') };
}

/** Çocuğun gövdesini kurar (bağ duruşunda). kiz: true ise Zeynep. */
function karakterKur(kiz) {
  const R = renkler(kiz), m = new Mesh(), TEN = MAT.TEN;
  const yum = (a, b, x) => { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };

  // ---- Gövde (gömlek / elbise): belden boyuna halkalar ----
  const govde = [[0.585, 0.136, 0.098, 0], [0.60, 0.136, 0.098, 0], [0.66, 0.126, 0.093, 0.002], [0.72, 0.115, 0.088, 0.006], [0.775, 0.112, 0.085, 0.006],
    [0.84, 0.118, 0.085, 0.004], [0.90, 0.128, 0.083, 0.002], [0.94, 0.140, 0.079, 0], [0.966, 0.136, 0.072, 0], [0.985, 0.098, 0.062, 0], [0.997, 0.062, 0.052, 0], [1.008, 0.046, 0.045, 0]];
  m.tube(govde.map(([y, rx, rz, z], i) => {
    // Ağırlık: leğen → bel → göğüs arasında yumuşak geçiş (eğilince kıvrım düzgün olsun)
    let b;
    if (y < 0.79) { const t = yum(0.70, 0.79, y); b = [K.LEGEN, K.BEL, 1 - t]; } else { const t = yum(0.79, 0.89, y); b = [K.BEL, K.GOGUS, 1 - t]; }
    return { c: [0, y, z], rx, rz, e: 2.25, b, col: i >= 10 ? R.ustKoyu : R.ust };
  }), { n: 28, capB: true });
  // etek ucunun iç kıvrımı (alttan bakınca içi boş görünmesin)
  m.tube([{ c: [0, 0.585, 0], rx: 0.136, rz: 0.098, e: 2.25 }, { c: [0, 0.60, 0], rx: 0.120, rz: 0.085, e: 2.25 }, { c: [0, 0.64, 0], rx: 0.09, rz: 0.066, e: 2.25 }], { n: 28, col: R.ustKoyu, b: [K.LEGEN, K.LEGEN, 1], flip: true });
  // Etek ucunun içi görünmesin: pantolonun üst kısmı (leğen)
  m.ball([0, 0.652, 0], [0.114, 0.078, 0.088], { col: R.alt, b: [K.LEGEN, K.LEGEN, 1], lat: 10, lon: 20 });
  if (!kiz) { // gömleğin önünde düğme sırası
    for (let i = 0; i < 4; i++) m.ball([0, 0.93 - i * 0.075, 0.084 + (i === 0 ? -0.004 : i > 1 ? 0.006 : 0.002)], [0.008, 0.008, 0.004],
      { col: R.ustKoyu, b: i < 2 ? [K.GOGUS, K.GOGUS, 1] : [K.BEL, K.LEGEN, i === 2 ? 0.8 : 0.3], lat: 5, lon: 8 });
  }

  for (const s of [1, -1]) {
    const L = s > 0, x = 0.14 * s;
    const UK = L ? K.UKOL_L : K.UKOL_R, OK = L ? K.ONKOL_L : K.ONKOL_R, EL = L ? K.EL_L : K.EL_R;
    const PA = L ? K.PA_L : K.PA_R, PB = L ? K.PB_L : K.PB_R, BA = L ? K.BA_L : K.BA_R, BB = L ? K.BB_L : K.BB_R;
    // ---- Kol (gömlek kolu): omuzdan bileğe ----
    const kol = [[0.990, 0.014], [0.983, 0.032], [0.965, 0.044], [0.93, 0.046], [0.87, 0.044], [0.825, 0.042], [0.785, 0.041], [0.745, 0.040], [0.70, 0.038], [0.655, 0.035], [0.632, 0.035]];
    m.tube(kol.map(([y, r]) => {
      const t = yum(0.825, 0.745, y); // dirsek çevresinde üst kol → ön kol geçişi
      return { c: [x, y, 0], rx: r, rz: r * 1.04, b: [UK, OK, 1 - t] };
    }), { n: 14, col: R.ust, capA: true });
    m.tube([{ c: [x, 0.632, 0], rx: 0.035, rz: 0.036 }, { c: [x, 0.640, 0], rx: 0.027, rz: 0.028 }], { n: 14, col: R.ustKoyu, b: [OK, OK, 1], capB: true, flip: true });
    // ---- El: avuç + dört parmak + başparmak ----
    m.tube([[0.642, 0.016, 0.021], [0.625, 0.0135, 0.023], [0.60, 0.0135, 0.031], [0.578, 0.0125, 0.0335], [0.566, 0.0105, 0.032]].map(([y, rx, rz]) =>
      ({ c: [x, y, 0.001], rx, rz, e: 2.7 })), { n: 12, col: R.ten, mat: TEN, b: [EL, EL, 1], capA: true, capB: true });
    const parmak = [[0.0235, 0.0082, 0.024], [0.0085, 0.0086, 0.028], [-0.0065, 0.0081, 0.0245], [-0.0205, 0.0071, 0.018]];
    for (const [z, r, uc] of parmak) {
      const yA = 0.567, yB = 0.540, yU = yB - uc;
      m.tube([{ c: [x, yA + 0.006, z], rx: r * 0.9, rz: r * 0.9, b: [EL, PA, 0.6] }, { c: [x, yA - 0.006, z], rx: r, rz: r, b: [PA, PA, 1] },
        { c: [x, yB + 0.007, z], rx: r, rz: r, b: [PA, PA, 1] }, { c: [x, yB, z], rx: r, rz: r, b: [PA, PB, 0.5] }, { c: [x, yB - 0.007, z], rx: r * 0.97, rz: r * 0.97, b: [PB, PB, 1] },
        { c: [x, yU + 0.004, z], rx: r * 0.88, rz: r * 0.88, b: [PB, PB, 1] }, { c: [x, yU - 0.003, z], rx: r * 0.5, rz: r * 0.5, b: [PB, PB, 1] }],
      { n: 8, col: R.ten, mat: TEN, capB: true });
    }
    { // başparmak: avucun ön kenarından öne-aşağı uzanır
      const d = E.basparYon, a = [x, E.basparA[1], E.basparA[2]], az = V.norm(V.cross(d, [1, 0, 0])), r = 0.0098;
      const nok = (t) => V.add(a, V.mul(d, t));
      m.tube([{ c: nok(-0.012), rx: r * 1.1, rz: r * 1.2, b: [EL, EL, 1] }, { c: nok(0.004), rx: r * 1.05, rz: r * 1.1, b: [EL, BA, 0.3] }, { c: nok(0.021), rx: r, rz: r, b: [BA, BA, 1] },
        { c: nok(0.028), rx: r, rz: r, b: [BA, BB, 0.5] }, { c: nok(0.036), rx: r * 0.96, rz: r * 0.96, b: [BB, BB, 1] }, { c: nok(0.048), rx: r * 0.86, rz: r * 0.86, b: [BB, BB, 1] },
        { c: nok(0.054), rx: r * 0.45, rz: r * 0.45, b: [BB, BB, 1] }].map((q) => ({ ...q, ax: [1, 0, 0], az })), { n: 8, col: R.ten, mat: TEN, capB: true });
    }

    // ---- Bacak (pantolon): kalçadan bileğe ----
    const bx = 0.078 * s, UY = L ? K.UYL_L : K.UYL_R, BL = L ? K.BAL_L : K.BAL_R, AY = L ? K.AYK_L : K.AYK_R, PR = L ? K.PRM_L : K.PRM_R;
    const bacak = [[0.70, 0.040], [0.665, 0.048], [0.61, 0.053], [0.57, 0.059], [0.50, 0.058], [0.42, 0.055], [0.39, 0.0535], [0.36, 0.0525], [0.33, 0.051], [0.30, 0.050], [0.21, 0.047], [0.12, 0.044], [0.092, 0.045]];
    m.tube(bacak.map(([y, r]) => {
      if (y > 0.68) return { c: [bx * 0.9, y, 0], rx: r, rz: r * 1.08, b: [K.LEGEN, UY, 0.5] };
      const t = yum(0.40, 0.32, y); // diz çevresinde uyluk → baldır geçişi
      return { c: [bx, y, y > 0.45 ? 0 : 0.002], rx: r, rz: r * 1.06, b: [UY, BL, 1 - t] };
    }), { n: 16, col: R.alt, capA: true });
    m.tube([{ c: [bx, 0.092, 0], rx: 0.045, rz: 0.046 }, { c: [bx, 0.10, 0], rx: 0.033, rz: 0.034 }], { n: 16, col: hex('#22304a'), b: [BL, BL, 1], capB: true, flip: true });
    // ---- Ayak bileği ve ayak (çıplak) ----
    m.tube([[0.112, 0.029], [0.085, 0.028], [0.06, 0.030], [0.042, 0.031]].map(([y, r]) => ({ c: [bx, y, -0.004], rx: r, rz: r * 1.15, b: y > 0.07 ? [BL, BL, 1] : [BL, AY, 0.3] })),
      { n: 12, col: R.ten, mat: TEN });
    // Ayak: topuktan tarağa doğru kesitler (tabanı düz)
    const ayak = [[-0.047, 0.014, 0.020], [-0.040, 0.026, 0.045], [-0.022, 0.031, 0.066], [0.0, 0.033, 0.074], [0.025, 0.035, 0.064], [0.05, 0.038, 0.048], [0.075, 0.041, 0.036], [0.095, 0.042, 0.029], [0.108, 0.040, 0.025]];
    m.tube(ayak.map(([z, hw, top], i) => ({
      c: [bx + s * (z > 0.03 ? 0.004 : 0), top / 2 + 0.001, z], ax: [1, 0, 0], az: [0, 1, 0], rx: hw, rz: top / 2, e: 2.8,
      b: z > 0.1 ? [PR, PR, 1] : z > 0.09 ? [AY, PR, 0.5] : [AY, AY, 1],
    })), { n: 14, col: R.ten, mat: TEN, capA: true, capB: true });
    // Ayak parmakları: başparmak içte
    const tp = [[-0.027, 0.0135, 0.021], [-0.009, 0.0105, 0.019], [0.006, 0.0098, 0.017], [0.019, 0.009, 0.015], [0.031, 0.0082, 0.012]];
    for (const [dx, r, len] of tp) {
      m.ball([bx + s * (dx + 0.004), r + 0.001, 0.108 + len * 0.55 - Math.abs(dx + 0.027) * 0.12], [r, r * 0.95, len], { col: R.ten, mat: TEN, b: [PR, PR, 1], lat: 6, lon: 8 });
    }
  }

  // ---- Boyun ----
  m.tube([[0.985, 0.040], [1.01, 0.037], [1.04, 0.037], [1.065, 0.040]].map(([y, r]) => ({ c: [0, y, -0.004], rx: r, rz: r, b: y < 1.0 ? [K.GOGUS, K.BOYUN, 0.6] : y < 1.05 ? [K.BOYUN, K.BAS, 0.6] : [K.BAS, K.BAS, 1] })),
    { n: 14, col: R.ten, mat: TEN });

  // ---- Baş: yüz shader ile boyanır; saç/örtü bölgesi hafifçe dışarı taşırılır (kalınlık) ----
  const hc = E.basMerkez, hr = E.basR;
  m.ball(hc, hr, {
    lat: 40, lon: 56, mat: MAT.YUZ, col: R.ten, b: [K.BAS, K.BAS, 1],
    f: (p, d) => {
      let sx = 1, sz = 1, sy = 1;
      if (d[1] < 0) { const q = d[1] * d[1]; sx -= 0.24 * q; sz -= 0.12 * q; }          // çeneye doğru daralma
      const yanak = Math.exp(-(((Math.abs(d[0]) - 0.55) ** 2) + ((d[1] + 0.32) ** 2)) / 0.05) * (d[2] > 0 ? 1 : 0);
      sx += yanak * 0.05; sz += yanak * 0.035;                                            // dolgun yanaklar
      const k = basMaske(d, kiz), t = clamp((k + 0.015) / 0.07, 0, 1), kal = t * t * (3 - 2 * t);
      let kab = kiz ? 0.085 * kal : 0.055 * kal;
      if (!kiz) kab += 0.03 * kal * Math.max(0, d[2]) * clamp(1 - Math.abs(d[1] - 0.5) * 3, 0, 1); // kâkül hafif kabarık
      if (kiz) kab += 0.03 * kal * clamp(-d[2] + 0.2, 0, 1);                             // örtünün arkası biraz dolgun
      const g = 1 + kab;
      return { p: [hc[0] + d[0] * hr[0] * sx * g, hc[1] + d[1] * hr[1] * sy * g, hc[2] + d[2] * hr[2] * sz * g] };
    },
  });
  // burun
  m.ball([0, 1.112, 0.127], [0.0125, 0.011, 0.014], { col: hex('#EFB590'), mat: TEN, b: [K.BAS, K.BAS, 1], lat: 8, lon: 12 });
  if (!kiz) {
    // kulaklar
    for (const s of [1, -1]) m.ball([0.117 * s, 1.137, -0.004], [0.013, 0.029, 0.021], { col: hex('#EDB692'), mat: TEN, b: [K.BAS, K.BAS, 1], lat: 8, lon: 10 });
    // takke: başın tepesinde, hafif arkaya yatık örgü başlık
    const egim = 13 * RAD, eks = [0, Math.cos(egim), -Math.sin(egim)], az = [0, Math.sin(egim), Math.cos(egim)];
    const hal = [[5, 1.125], [16, 1.125], [28, 1.125], [40, 1.122], [50, 1.118], [58, 1.112], [63, 1.115], [65, 1.07]];
    m.tube(hal.map(([a, g]) => {
      const c = Math.cos(a * RAD), sn = Math.sin(a * RAD);
      return { c: V.add(hc, V.mul(eks, hr[1] * g * c)), ax: [1, 0, 0], az, rx: hr[0] * g * sn, rz: hr[2] * g * sn };
    }), { n: 36, col: R.ortu, mat: MAT.ORGU, b: [K.BAS, K.BAS, 1], capA: true });
  } else {
    // başörtüsünün boyun ve omuzlara inen kısmı
    const ortu = [[1.085, 0.108, 0.088, -0.028, K.BAS, K.BAS, 1], [1.05, 0.097, 0.086, -0.016, K.BAS, K.BOYUN, 0.6], [1.01, 0.098, 0.082, -0.004, K.BOYUN, K.GOGUS, 0.6],
      [0.975, 0.126, 0.090, 0, K.GOGUS, K.GOGUS, 1], [0.94, 0.148, 0.102, 0.002, K.GOGUS, K.GOGUS, 1], [0.90, 0.156, 0.110, 0.004, K.GOGUS, K.GOGUS, 1], [0.875, 0.150, 0.108, 0.004, K.GOGUS, K.GOGUS, 1]];
    // ön tarafı biraz daha uzun, yuvarlak kenarlı
    m.tube(ortu.map(([y, rx, rz, z, b0, b1, w], i) => ({ c: [0, y, z], rx, rz, b: [b0, b1, w], f: i >= 5 ? (x, zz) => [x, zz] : null })), { n: 28, col: R.ortu });
    m.tube([{ c: [0, 0.875, 0.004], rx: 0.150, rz: 0.108 }, { c: [0, 0.89, 0.004], rx: 0.13, rz: 0.09 }], { n: 28, col: R.ustKoyu, b: [K.GOGUS, K.GOGUS, 1], flip: true });
  }
  return m.build();
}

// =====================================================================================
// Çözücü: duruş tarifinden kemiklerin yerini hesaplar.
// =====================================================================================

/**
 * İki kemikli uzuv (kol ya da bacak) için ters kinematik: kök a, hedef c, kemik boyları l1 ve l2.
 * "kutup": dirseğin/dizin bakmasını istediğimiz yön. Orta eklemin yerini ve kullanılan yönü döndürür.
 */
function ik2(a, c, l1, l2, kutup) {
  let ac = V.sub(c, a), d = V.len(ac);
  const maxD = (l1 + l2) * 0.9995, minD = Math.abs(l1 - l2) + 0.002;
  if (d > maxD) { ac = V.mul(ac, maxD / d); d = maxD; c = V.add(a, ac); }
  if (d < minD) { d = minD; ac = V.mul(V.norm(ac), d); c = V.add(a, ac); }
  const dir = V.mul(ac, 1 / d);
  let kp = V.sub(kutup, V.mul(dir, V.dot(kutup, dir)));
  if (V.len(kp) < 1e-5) kp = V.cross(dir, [1, 0, 0]);
  kp = V.norm(kp);
  const x = (d * d + l1 * l1 - l2 * l2) / (2 * d), h = Math.sqrt(Math.max(0, l1 * l1 - x * x));
  return { b: V.add(V.add(a, V.mul(dir, x)), V.mul(kp, h)), c, dir, kp };
}

/** Uzuv kemiğinin yönelimi: kemik "aşağı" (−Y) ekseni boyunca uzanır, X menteşe eksenidir. */
function uzuvMat(bas, son, X) {
  const Y = V.norm(V.sub(bas, son)), Z = V.norm(V.cross(X, Y)), X2 = V.cross(Y, Z);
  return M.basis(X2, Y, Z, bas);
}

const BAG = new Array(28).fill(null); // her kemiğin bağ duruşundaki yeri (deri kaplama için)
(function () {
  const mir = (p) => [-p[0], p[1], p[2]];
  BAG[K.KOK] = [0, 0, 0]; BAG[K.LEGEN] = E.legen; BAG[K.BEL] = E.bel; BAG[K.GOGUS] = E.gogus; BAG[K.BOYUN] = E.boyun; BAG[K.BAS] = E.bas;
  const kolL = [E.omuz, E.dirsek, E.bilek, E.parmakA, E.parmakB, E.basparA, V.add(E.basparA, V.mul(E.basparYon, E.basparBoy))];
  kolL.forEach((p, i) => { BAG[K.UKOL_L + i] = p; BAG[K.UKOL_R + i] = mir(p); });
  [E.kalca, E.diz, E.ayak, E.tarak].forEach((p, i) => { BAG[K.UYL_L + i] = p; BAG[K.UYL_R + i] = mir(p); });
})();

// El tarifinde kullanılan "bağlı olduğu yer" adları → kemik numarası
const CAPA = {
  dunya: K.KOK, legen: K.LEGEN, bel: K.BEL, gogus: K.GOGUS, bas: K.BAS,
  uylukL: K.UYL_L, uylukR: K.UYL_R, baldirL: K.BAL_L, baldirR: K.BAL_R, ayakL: K.AYK_L, ayakR: K.AYK_R,
  onkolL: K.ONKOL_L, onkolR: K.ONKOL_R, ukolL: K.UKOL_L, ukolR: K.UKOL_R,
};

/**
 * Duruşu çözer. D: duruş tarifi (duruslar.js'e bakın). Kol tarifleri {A, B, t} biçiminde de
 * olabilir: iki duruşun arasındaki an (geçiş) böyle verilir.
 * Döndürdüğü: { W: kemiklerin dünya matrisleri, deri: ekran kartına gidecek dizi }
 */
function coz(D) {
  const W = new Array(28);
  W[K.KOK] = M.ident();
  const pel = D.pel.slice();
  const Rpel = M.euler(D.pp, D.py, D.pr);
  // ---- Ayaklar: ayak tarağı (parmak kökü) yere basılı noktadır; bilek ondan hesaplanır ----
  const ayak = {};
  for (const s of ['L', 'R']) {
    const f = D['f' + s], Rf = M.euler(f.p, f.y, f.r);
    ayak[s] = { R: Rf, bilek: V.sub(f.b, M.td(Rf, TARAK_FARK)), f };
  }
  // ---- Leğen: bacaklar yetişmiyorsa leğeni alçalt (ayaklar yerden kesilmesin) ----
  const kalcaYer = (s) => V.add(pel, M.td(Rpel, [s === 'L' ? 0.078 : -0.078, -0.04, 0]));
  const Lmax = (UYLUK + BALDIR) * 0.9985;
  for (let it = 0; it < 2; it++) for (const s of ['L', 'R']) {
    const h = kalcaYer(s), a = ayak[s].bilek, yatay = Math.hypot(h[0] - a[0], h[2] - a[2]);
    if (yatay < Lmax) { const izin = a[1] + Math.sqrt(Lmax * Lmax - yatay * yatay); if (h[1] > izin) pel[1] -= h[1] - izin; }
  }
  W[K.LEGEN] = M.mul(M.trans(pel[0], pel[1], pel[2]), Rpel);
  // ---- Omurga ve baş ----
  W[K.BEL] = M.mul(W[K.LEGEN], M.mul(M.trans(0, 0.045, 0), M.euler(D.sp, D.st * 0.5, D.sl * 0.5)));
  W[K.GOGUS] = M.mul(W[K.BEL], M.mul(M.trans(0, 0.095, 0), M.euler(D.cp, D.st * 0.5, D.sl * 0.5)));
  W[K.BOYUN] = M.mul(W[K.GOGUS], M.mul(M.trans(0, 0.155, 0), M.euler(D.np, D.hy * 0.35, D.hr * 0.3)));
  W[K.BAS] = M.mul(W[K.BOYUN], M.mul(M.trans(0, 0.045, 0), M.euler(D.hp, D.hy * 0.65, D.hr * 0.7)));
  // ---- Bacaklar ----
  for (const s of ['L', 'R']) {
    const o = s === 'L' ? 0 : 4, h = kalcaYer(s), A = ayak[s];
    const r = ik2(h, A.bilek, UYLUK, BALDIR, A.f.k);
    const X = V.norm(V.cross(r.kp, r.dir));
    W[K.UYL_L + o] = uzuvMat(h, r.b, X);
    W[K.BAL_L + o] = uzuvMat(r.b, r.c, X);
    // bilek hedefe yetişemediyse ayak bacağın ucunda kalır
    W[K.AYK_L + o] = M.mul(M.trans(r.c[0], r.c[1], r.c[2]), A.R);
    W[K.PRM_L + o] = M.mul(W[K.AYK_L + o], M.mul(M.trans(TARAK_FARK[0], TARAK_FARK[1], TARAK_FARK[2]), M.rotX(A.f.t * RAD)));
  }
  const deriMat = (k) => M.mul(W[k], M.trans(-BAG[k][0], -BAG[k][1], -BAG[k][2]));
  // ---- Kollar ----
  // El hedefi: "bağ duruşunda şu noktada duran yer" + hangi kemiğe bağlı olduğu. Böylece
  // "el dizde", "el kulak hizasında", "el yerde" gibi tarifler gövde oynasa da doğru kalır.
  const elHedef = (t) => {
    const mm = deriMat(CAPA[t.at]);
    const f = V.norm(M.td(mm, t.f)), n0 = M.td(mm, t.n);
    const n = V.norm(V.sub(n0, V.mul(f, V.dot(n0, f))));
    return { p: M.tp(mm, t.p), f, n, k: M.td(W[K.GOGUS], t.k) };
  };
  const elMat = (h, s) => { // parmak yönü f ve avuç yönü n'den elin yönelimi
    const Y = V.mul(h.f, -1), X = s === 'L' ? V.mul(h.n, -1) : h.n, Z = V.cross(X, Y);
    return M.basis(X, Y, Z, [0, 0, 0]);
  };
  const kolCoz = (s) => {
    const o = s === 'L' ? 0 : 7, sg = s === 'L' ? 1 : -1, T = D['a' + s];
    let h, par;
    if (T.A) { // geçiş: iki tarifin o anki gövdeye göre yerleri arasında karışım
      const a = elHedef(T.A), b = elHedef(T.B), t = T.t;
      const q = Q.slerp(Q.fromMat(elMat(a, s)), Q.fromMat(elMat(b, s)), t);
      let p = V.lerp(a.p, b.p, t);
      if (T.B.yay) p = V.add(p, V.mul(M.td(W[K.GOGUS], T.B.yay), Math.sin(Math.PI * t)));
      h = { p, R: Q.toMat(q), k: V.lerp(a.k, b.k, t) };
      par = { c: lerp(T.A.c, T.B.c, t), c2: lerp(T.A.c2, T.B.c2, t), th: lerp(T.A.th, T.B.th, t), tf: lerp(T.A.tf, T.B.tf, t) };
    } else { const a = elHedef(T); h = { p: a.p, R: elMat(a, s), k: a.k }; par = T; }
    const omuz = M.tp(W[K.GOGUS], [0.14 * sg, 0.125, 0]);
    const r = ik2(omuz, h.p, UKOL, ONKOL, h.k);
    const X = V.norm(V.cross(r.dir, r.kp));
    W[K.UKOL_L + o] = uzuvMat(omuz, r.b, X);
    W[K.ONKOL_L + o] = uzuvMat(r.b, r.c, X);
    const el = h.R.slice(); el[12] = r.c[0]; el[13] = r.c[1]; el[14] = r.c[2];
    W[K.EL_L + o] = el;
    // parmaklar avuca doğru kıvrılır (c: kök boğum, c2: uç boğum, derece)
    const pa = M.mul(el, M.mul(M.trans(0, E.parmakA[1] - E.bilek[1], 0), M.rotZ(-sg * par.c * RAD)));
    W[K.PA_L + o] = pa;
    W[K.PB_L + o] = M.mul(pa, M.mul(M.trans(0, E.parmakB[1] - E.parmakA[1], 0), M.rotZ(-sg * par.c2 * RAD)));
    // başparmak: th işaret parmağına yaklaşma, tf avucun içine kapanma
    const ba = M.mul(el, M.mul(M.trans(0, E.basparA[1] - E.bilek[1], E.basparA[2]), M.mul(M.rotX(par.th * RAD), M.rotY(-sg * par.tf * RAD))));
    W[K.BA_L + o] = ba;
    const d = V.mul(E.basparYon, E.basparBoy);
    W[K.BB_L + o] = M.mul(ba, M.mul(M.trans(d[0], d[1], d[2]), M.rotY(-sg * par.tf * 0.6 * RAD)));
  };
  // Bir el öbür kola bağlıysa (kol yıkarken) önce o kol çözülür
  const bagli = (T) => { const a = (T.B || T).at, b = T.A ? T.A.at : a; return /kol/.test(a) || /kol/.test(b); };
  if (bagli(D.aL)) { kolCoz('R'); kolCoz('L'); } else { kolCoz('L'); kolCoz('R'); }

  const deri = new Float32Array(KEMIK_SAYISI * 16);
  for (let i = 0; i < 28; i++) deri.set(deriMat(i), i * 16);
  for (let i = 28; i < KEMIK_SAYISI; i++) deri.set(M.ident(), i * 16);
  return { W, deri, pel };
}
