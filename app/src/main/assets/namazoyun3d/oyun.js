// =====================================================================================
// oyun.js — Oyunla bağlantı: hareket sırasını oynatır, kamerayı yönetir, her kareyi çizer.
//
// Android tarafı (PrayerStageView.kt) şu işlevleri çağırır:
//   N3.kiz(true/false)             → Zeynep / Ahmet
//   N3.goster("kiyam", "NAMAZ")    → figürü hemen bir duruşa koyar
//   N3.oynat(hareketler, "NAMAZ", "HANDS") → doğru cevaptan sonraki hareketler
//   N3.yanlis()  N3.kutla()  N3.hizli()
// Sayfa Android'e şunları bildirir (window.Android varsa):
//   Android.hazir()  Android.okuma(hareketSırası, kelimeSayısı)  Android.soz(hareketSırası)  Android.bitti(oyunNo)  Android.hata(mesaj)
// =====================================================================================
'use strict';

const N3 = (function () {
  const tuval = document.getElementById('tuval'), balon = document.getElementById('balon');
  const bildir = (ad, ...a) => { try { if (window.Android && window.Android[ad]) window.Android[ad](...a); } catch (e) { /* tarayıcıda denerken Android yok */ } };
  let ciz;
  try { ciz = new Cizici(tuval); } catch (e) { bildir('hata', String(e.message || e)); document.body.classList.add('hata'); return { yok: true }; }

  // ---- Durum ----
  let kizMi = false, sahneAd = 'ABDEST';
  const DR = { false: null, true: null };           // duruşlar (erkek / kız)
  const govde = { false: null, true: null };        // yüklenmiş karakter örgüleri
  const sahne = {};                                 // yüklenmiş sahne örgüleri
  let simdi = null, simdiAd = 'a_sit';              // şu anki duruş (tarif) ve adı
  let oyun = null;                                  // oynatılan hareket sırası
  let oyunNo = 0;
  let zaman = 0, sonKare = 0;
  let yuz = { mutlu: 0, sasir: 0, kirp: 0, sonrakiKirp: 2, sallaT: -9 };
  let soluk = 0, solukHedef = 0, bekleyenSahne = null;   // sahne geçişinde kararma
  const kam = { bak: [0, 0.6, 0.1], uzak: 3.4, yaw: 40, egim: 13, ilk: true };
  let ozelKam = null, deneme = null;
  let yazi = null, yaziBitis = 0;

  // Abdesti her zaman Ahmet alır; Zeynep seçildiyse namaz sahnesinde Zeynep görünür
  const kizSahnede = () => kizMi && sahneAd !== 'ABDEST';
  const durusAl = (ad) => { const k = kizSahnede(), D = DR[k] || (DR[k] = duruslar(k)); return D[ad] || D.stand; };
  const sahneAl = (ad) => {
    if (!sahne[ad]) { const b = SAHNELER[ad].kur(); sahne[ad] = { ana: ciz.upload(b.ana), su: b.su ? ciz.upload(b.su, { blend: true, noShadow: true }) : null }; }
    return sahne[ad];
  };
  // Abdest sahnesinde figürün kolları sıvalıdır (erkekte takke de çıkmıştır): ayrı bir örgü
  const govdeAl = () => { const a = sahneAd === 'ABDEST', k = kizSahnede(), an = k + '|' + a; return govde[an] || (govde[an] = ciz.upload(karakterKur(k, a))); };
  let avuc = null;
  const avucAl = () => avuc || (avuc = { L: ciz.upload(avucSuKur(true), { blend: true, noShadow: true }), R: ciz.upload(avucSuKur(false), { blend: true, noShadow: true }) });
  // Hangi duruşa giderken avuçta su var: R sağ el, LR iki el
  const AVUC_SU = { a_cupr: 'R', a_mouth: 'R', a_nose: 'R', a_cup: 'LR', a_face1: 'LR' };
  // Suyun çarpıp sıçradığı yükseklik: eller musluğun altında, ayak daha aşağıda
  const SU_Y = { a_rfoot: 0.085, a_rfoot2: 0.085, a_lfoot: 0.085, a_lfoot2: 0.085 };

  // ---- Parçacıklar: su damlası (0), ışıltı (1), konfeti (2) ----
  const P = [], PV = new Float32Array(600 * 9);
  const rnd = (a = 1, b) => (b === undefined ? Math.random() * a : a + Math.random() * (b - a));
  function parcaEkle(tur, p, v, omur, boy, renk) { if (P.length < 600) P.push({ tur, p: p.slice(), v, t: 0, omur, boy, renk, don: rnd(), dv: rnd(-3, 3) }); }
  function isilti(yer, adet, yay = 0.07) {
    for (let i = 0; i < adet; i++) parcaEkle(1, [yer[0] + rnd(-yay, yay), yer[1] + rnd(-yay, yay), yer[2] + rnd(-yay, yay)], [rnd(-0.05, 0.05), rnd(0.03, 0.16), rnd(-0.05, 0.05)], rnd(0.7, 1.5), rnd(0.03, 0.06), i % 3 ? [1, 0.88, 0.5] : [1, 1, 1]);
  }
  function parcaGuncelle(dt, su, suYer) {
    if (su) for (let i = 0; i < 3; i++) parcaEkle(0, [suYer[0] + rnd(-0.02, 0.02), suYer[1] - 0.01, suYer[2] + rnd(-0.02, 0.02)], [rnd(-0.35, 0.35), rnd(0.1, 0.7), rnd(-0.35, 0.35)], rnd(0.3, 0.55), rnd(0.010, 0.02), [0.8, 0.93, 1]);
    let n = 0;
    for (let i = P.length - 1; i >= 0; i--) {
      const q = P[i]; q.t += dt;
      if (q.t > q.omur) { P.splice(i, 1); continue; }
      if (q.tur === 0) q.v[1] -= 5.5 * dt;
      else if (q.tur === 2) { q.v[1] = Math.max(q.v[1] - 1.2 * dt, -0.55); q.v[0] += Math.sin(q.t * 5 + q.don * 9) * 0.6 * dt; q.don += q.dv * dt * 0.2; }
      q.p[0] += q.v[0] * dt; q.p[1] += q.v[1] * dt; q.p[2] += q.v[2] * dt;
      if (q.p[1] < 0.004) { q.p[1] = 0.004; q.v = [0, 0, 0]; if (q.tur === 0) q.t = q.omur; }
    }
    for (const q of P) {
      const k = q.t / q.omur, a = q.tur === 1 ? Math.sin(Math.PI * k) : q.tur === 2 ? Math.min(1, (1 - k) * 4) : 0.75 * (1 - k * 0.5);
      PV.set([q.p[0], q.p[1], q.p[2], q.renk[0], q.renk[1], q.renk[2], a, q.boy * (q.tur === 1 ? 0.6 + 0.4 * Math.sin(k * 20 + q.don * 6) : 1), q.tur + ((q.don % 1) + 1) % 1 * 0.999], n * 9);
      n++;
    }
    return n;
  }

  // ---- Dışarıdan çağrılanlar ----
  function kiz(v) { kizMi = !!v; simdi = durusAl(simdiAd); }
  function goster(ad, sAd) {
    oyun = null; simdiAd = ad; yazi = null;
    if (sAd && sAd !== sahneAd) { sahneAd = sAd; kam.ilk = true; }
    simdi = durusAl(ad);
    soluk = 0; solukHedef = 0; bekleyenSahne = null;
  }
  /**
   * Hareket sırasını oynatır. hareketler: [{pose, ms, caption, water, recite: kelimeSayısı|null}]
   * Her hareket için önce o duruşa giden yol (ara duruşlarıyla) kurulur.
   */
  function oynat(hareketler, sAd, parca, no) {
    const liste = typeof hareketler === 'string' ? JSON.parse(hareketler) : hareketler;
    oyun = { no: no === undefined ? ++oyunNo : no, liste, i: -1, parca: parca || null, t0: 0, yol: [], yolSure: 0, sure: 0, kelime: -1, hizli: false };
    yuz.mutlu = 1;
    if (sAd && sAd !== sahneAd) { bekleyenSahne = sAd; solukHedef = 1; } // sahne değişecek: önce karar
    else sonraki();
    return oyun.no;
  }
  function sonraki() {
    const o = oyun; o.i++; o.kelime = -1; o.hizli = false;
    if (o.i >= o.liste.length) return bitir();
    const h = o.liste[o.i], A0 = simdi;
    o.yol = []; let t = 0, onceki = A0;
    if (h.pose !== simdiAd) {
      for (const [ad, sure, gec] of yolBul(simdiAd, h.pose, h.ms)) { const B = durusAl(ad); o.yol.push({ A: onceki, B, t0: t, sure, gec }); t += sure; onceki = B; }
    }
    o.yolSure = t; o.son = onceki;
    const oku = h.recite ? h.ms / 1000 : 0;
    // Hareketin toplam süresi: yol + (okuma ya da kısa bekleme)
    o.okuSure = oku;
    o.sure = h.recite ? Math.max(t + 0.15, oku) : (h.pose === simdiAd ? Math.max(0.3, h.ms / 1000) : t + (h.caption && !/^\d$/.test(h.caption) ? 0.5 : 0.08));
    o.t0 = zaman; simdiAd = h.pose;
    if (h.caption && !h.recite) bildir('soz', o.i); // "Allâhu Ekber" gibi sözler: Android ses kaydını çalar
  }
  function bitir() {
    const o = oyun; oyun = null;
    if (o.parca) parcaIsilti(o.parca);
    // "Allâhu Ekber" gibi sözler biraz daha kalır; okunan duanın son kelimesi hemen kaybolur
    const son = o.liste[o.liste.length - 1];
    yaziBitis = son && son.caption && !son.recite ? zaman + 1.6 : zaman;
    bildir('bitti', o.no);
    if (window.N3_bitti) window.N3_bitti(o.no);
  }
  function hizli() { if (oyun && oyun.i >= 0 && oyun.liste[oyun.i].recite) oyun.hizli = true; }
  function yanlis() { yuz.sasir = 1.3; yuz.sallaT = zaman; }
  function kutla() {
    const renk = [[0.91, 0.76, 0.35], [0.44, 0.83, 0.63], [0.95, 0.55, 0.69], [0.5, 0.72, 0.94], [1, 1, 1]];
    for (let i = 0; i < 150; i++) parcaEkle(2, [rnd(-1.3, 1.3), rnd(1.3, 2.6), rnd(-0.6, 1.3)], [rnd(-0.2, 0.2), rnd(-0.4, 0), rnd(-0.2, 0.2)], rnd(3.5, 6), rnd(0.03, 0.05), renk[i % 5]);
    parcaIsilti('ALL'); yuz.mutlu = 5;
  }
  let sonW = null;
  function parcaIsilti(parca) {
    if (!sonW) return;
    const yer = (k, p) => M.tp(M.mul(sonW[k], M.trans(-BAG[k][0], -BAG[k][1], -BAG[k][2])), p);
    const T = {
      HANDS: [[K.EL_L, [0.14, 0.58, 0]], [K.EL_R, [-0.14, 0.58, 0]]], MOUTH: [[K.BAS, [0, 1.09, 0.14]]], NOSE: [[K.BAS, [0, 1.12, 0.15]]], FACE: [[K.BAS, [0, 1.14, 0.14]]],
      R_ARM: [[K.ONKOL_R, [-0.14, 0.70, 0]]], L_ARM: [[K.ONKOL_L, [0.14, 0.70, 0]]], HEAD: [[K.BAS, [0, 1.27, 0]]], EARS: [[K.BAS, [0.13, 1.14, 0]], [K.BAS, [-0.13, 1.14, 0]]],
      R_FOOT: [[K.AYK_R, [-0.078, 0.04, 0.05]]], L_FOOT: [[K.AYK_L, [0.078, 0.04, 0.05]]],
      ALL: [[K.BAS, [0, 1.2, 0]], [K.GOGUS, [0, 0.9, 0.05]], [K.LEGEN, [0, 0.7, 0.05]], [K.EL_L, [0.14, 0.58, 0]], [K.EL_R, [-0.14, 0.58, 0]]],
    }[parca] || [];
    for (const [k, p] of T) isilti(yer(k, p), parca === 'ALL' ? 9 : Math.ceil(18 / T.length), parca === 'ALL' ? 0.16 : 0.07);
  }

  // ---- Her kare ----
  function kare(ms) {
    const dt = Math.min(0.05, sonKare ? (ms - sonKare) / 1000 : 0.016); sonKare = ms; zaman += dt;
    adim(dt);
    requestAnimationFrame(kare);
  }

  function adim(dt) {
    // Sahne geçişi: karar → sahneyi değiştir → aydınlan
    if (soluk !== solukHedef) {
      soluk = solukHedef > soluk ? Math.min(1, soluk + dt / 0.45) : Math.max(0, soluk - dt / 0.55);
      if (soluk >= 1 && bekleyenSahne) {
        sahneAd = bekleyenSahne; bekleyenSahne = null; solukHedef = 0; kam.ilk = true;
        if (oyun) { const ilk = oyun.liste[0]; simdiAd = ilk.pose; simdi = durusAl(ilk.pose); sonraki(); }
      }
    }
    // Hareket sırası
    let D = simdi, su = false, agiz = 0, avucSu = '', suY = 0.36; yazi = zaman < yaziBitis ? yazi : null;
    if (oyun && oyun.i >= 0) {
      const o = oyun, h = o.liste[o.i], t = zaman - o.t0;
      if (o.yol.length) {
        let parca = o.yol[o.yol.length - 1];
        for (const q of o.yol) if (t < q.t0 + q.sure) { parca = q; break; }
        D = t >= o.yolSure ? o.son : karistir(parca.A, parca.B, (t - parca.t0) / parca.sure, parca.gec);
      } else D = o.son;
      su = !!h.water; yazi = h.caption || null; avucSu = AVUC_SU[h.pose] || ''; suY = SU_Y[h.pose] || 0.36;
      if (h.caption && !/^\d$/.test(h.caption) && !/[→←]/.test(h.caption)) agiz = 1;
      let bittiMi = t >= o.sure;
      if (h.recite) {
        const f = o.hizli ? 1 : clamp(t / o.okuSure, 0, 1), n = clamp(Math.ceil(f * h.recite), 1, h.recite);
        if (n !== o.kelime) { o.kelime = n; bildir('okuma', o.i, n); if (window.N3_okuma) window.N3_okuma(o.i, n); }
        yazi = h.words ? String(h.words[n - 1]).replace(/[.,]+$/, '') : null;
        agiz = f < 1 ? 1 : 0;
        bittiMi = (f >= 1) && t >= o.yolSure;
      }
      if (bittiMi) {
        simdi = o.son;
        if (h.caption && !h.recite) yaziBitis = zaman + 1.6;
        sonraki();
      }
    }
    if (deneme) { su = !!deneme.su; avucSu = deneme.avuc || ''; suY = deneme.suY || 0.36; } // deneme için elle ayar
    // Yüz: göz kırpma, konuşma, gülümseme
    yuz.sonrakiKirp -= dt;
    if (yuz.sonrakiKirp < 0) { yuz.kirp = 0.16; yuz.sonrakiKirp = rnd(2.2, 5.5); }
    yuz.kirp = Math.max(0, yuz.kirp - dt);
    yuz.mutlu = Math.max(0, yuz.mutlu - dt * (oyun ? 0 : 1)); yuz.sasir = Math.max(0, yuz.sasir - dt);
    const kirpma = Math.sin(Math.min(1, yuz.kirp / 0.16) * Math.PI);
    const konus = agiz ? 0.25 + 0.75 * Math.abs(Math.sin(zaman * 10.5) * Math.sin(zaman * 3.7 + 1)) : 0;
    // Canlılık: nefes ve çok hafif baş oynaması; yanlış cevapta baş iki yana sallanır
    const nefes = Math.sin(zaman * 1.7);
    const sal = zaman - yuz.sallaT, salla = sal < 0.9 ? Math.sin(sal * 15) * 13 * (1 - sal / 0.9) : 0;
    const G = { ...D, cp: D.cp + nefes * 0.55, sp: D.sp - nefes * 0.2, np: D.np + Math.sin(zaman * 0.9) * 0.4, hy: D.hy + salla + Math.sin(zaman * 0.6) * 0.5 };
    const r = coz(G); sonW = r.W;

    // Kamera: figürü izler; yerdeki duruşlarda biraz yaklaşır
    const S = SAHNELER[sahneAd];
    const bas = M.tp(r.W[K.BAS], [0, 0.11, 0]), leg = r.pel;
    const yukseklik = clamp((bas[1] - 0.35) / 0.85, 0, 1);
    const hedefBak = [lerp(leg[0], bas[0], 0.5) * 0.5, lerp(0.36, S.bak[1], yukseklik) + (sahneAd === 'ABDEST' ? 0.0 : 0), lerp(leg[2], bas[2], 0.5) * 0.6 + 0.03];
    const hedefUzak = S.uzak * lerp(0.80, 1, yukseklik);
    const kk = kam.ilk ? 1 : 1 - Math.exp(-dt / 0.55);
    kam.bak = V.lerp(kam.bak, hedefBak, kk); kam.uzak = lerp(kam.uzak, hedefUzak, kk); kam.yaw = lerp(kam.yaw, S.yaw, kk); kam.egim = lerp(kam.egim, S.egim, kk); kam.ilk = false;
    const K2 = ozelKam || kam, cy = K2.yaw * RAD, ce = K2.egim * RAD;
    const goz = V.add(K2.bak, V.mul([Math.sin(cy) * Math.cos(ce), Math.sin(ce), Math.cos(cy) * Math.cos(ce)], K2.uzak));
    const oran = tuval.clientWidth / Math.max(1, tuval.clientHeight);
    // Dar (dik) ekranda figür sığsın diye görüş açısı genişler
    const fov = (ozelKam && ozelKam.fov ? ozelKam.fov : 25) * RAD * (oran < 1.2 ? lerp(1.55, 1, clamp((oran - 0.6) / 0.6, 0, 1)) : 1);
    const pr = M.persp(fov, oran, 0.3, 30), vw = M.lookAt(goz, K2.bak, [0, 1, 0]), vp = M.mul(pr, vw);
    const I = S.isik, lm = [0, 0.5, 0.35];
    const lightVP = M.mul(M.ortho(-1.7, 1.7, -1.7, 1.7, 1, 12), M.lookAt(V.add(lm, V.mul(I.sun, 6)), lm, [0, 1, 0]));
    // Yere yakın uzuvların altına yumuşak temas gölgesi
    const blobs = new Float32Array(24);
    [[K.AYK_L, 0.11], [K.AYK_R, 0.11], [K.BAL_L, 0.13], [K.BAL_R, 0.13], [K.EL_L, 0.10], [K.EL_R, 0.10]].forEach(([k, rad], i) => {
      const p = M.pos(r.W[k]);
      blobs.set([p[0], 0, p[2], p[1] < 0.2 ? rad * clamp(1.4 - p[1] * 4, 0.3, 1) : 0], i * 4);
    });
    const sah = sahneAl(sahneAd), kar = govdeAl();
    kar.bones = r.deri;
    if (sah.su) sah.su.visible = su;
    const av = avucAl();
    av.L.bones = r.deri; av.R.bones = r.deri;
    av.L.visible = avucSu === 'LR'; av.R.visible = avucSu !== '';
    const n = parcaGuncelle(dt, su, [0, suY, 0.445]);
    const env = {
      vp, lightVP, cam: goz, sun: I.sun, sunCol: I.sunCol, sky: I.sky, gnd: I.gnd, fill: I.fill, fillCol: I.fillCol, time: zaman,
      face: [Math.max(D.lid, kirpma, yuz.sasir > 0 ? 0 : 0), konus, clamp(D.sm + (yuz.mutlu > 0 ? 0.4 : 0) - (yuz.sasir > 0 ? 0.6 : 0), 0, 1), yuz.sasir > 0 ? 1 : 0],
      gaze: [D.gz[0], D.gz[1], kizSahnede() ? 1 : 0, 0], head: E.basMerkez, scarf: renkler(true).ortu, rug: S.hali, blobs, fade: smooth(soluk), projScale: 1 / Math.tan(fov / 2),
    };
    ciz.draw([sah.ana, kar, sah.su, av.L, av.R].filter(Boolean), env, PV, n);

    // Konuşma balonu: başın üstünde, ekrandaki yerine göre konur
    if (yazi && soluk < 0.3) {
      const tepe = M.tp(r.W[K.BAS], [0, 0.30, 0]);
      const c = [vp[0] * tepe[0] + vp[4] * tepe[1] + vp[8] * tepe[2] + vp[12], vp[1] * tepe[0] + vp[5] * tepe[1] + vp[9] * tepe[2] + vp[13], 0, vp[3] * tepe[0] + vp[7] * tepe[1] + vp[11] * tepe[2] + vp[15]];
      const x = (c[0] / c[3] * 0.5 + 0.5) * tuval.clientWidth, y = (1 - (c[1] / c[3] * 0.5 + 0.5)) * tuval.clientHeight;
      if (balon.textContent !== yazi) balon.textContent = yazi;
      balon.style.left = clamp(x, 70, tuval.clientWidth - 70) + 'px'; balon.style.top = Math.max(34, y) + 'px'; balon.classList.add('acik');
    } else balon.classList.remove('acik');
  }

  // ---- Başlat ----
  simdi = durusAl('a_sit');
  requestAnimationFrame(kare);
  setTimeout(() => bildir('hazir'), 0);

  return {
    kiz, goster, oynat, hizli, yanlis, kutla,
    // deneme için: kamerayı elle ayarla, zamanı elle ilerlet
    _kam: (k) => { ozelKam = k; }, _su: (d) => { deneme = d; }, _adim: (dt) => { zaman += dt; adim(dt); }, _durum: () => ({ oyun: !!oyun, ad: simdiAd, sahne: sahneAd }),
  };
})();
