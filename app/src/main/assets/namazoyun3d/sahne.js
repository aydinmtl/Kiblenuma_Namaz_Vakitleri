// =====================================================================================
// sahne.js — İki sahnenin (abdesthane ve namaz odası) kodla kurulması, ışık ve kamera ayarları.
// Çocuk sahnenin ortasında (0,0,0) durur ve +Z yönüne (kıbleye) bakar. Kamera kıble
// tarafındadır; böylece çocuk kameraya dönük görünür.
// =====================================================================================
'use strict';

/** Bir yol boyunca boru (musluk gibi kıvrımlı parçalar için). */
function boru(m, noktalar, r, o) {
  const halkalar = noktalar.map((p, i) => {
    const a = noktalar[Math.max(0, i - 1)], b = noktalar[Math.min(noktalar.length - 1, i + 1)];
    const t = V.norm(V.sub(b, a));
    let ax = V.cross([0, 1, 0], t);
    if (V.len(ax) < 0.01) ax = [1, 0, 0];
    ax = V.norm(ax);
    const rr = Array.isArray(r) ? r[i] : r;
    return { c: p, ax, az: V.cross(t, ax), rx: rr, rz: rr };
  });
  m.tube(halkalar, { n: 12, capA: true, capB: true, ...o });
}

const SAHNELER = {
  // ------------------------------------------------------------------ Namaz odası
  NAMAZ: {
    yaw: 42, egim: 12, uzak: 3.9, bak: [0, 0.79, 0.08],
    isik: {
      sun: V.norm([-0.50, 0.90, 0.62]), sunCol: [2.25, 1.88, 1.40],
      sky: [0.30, 0.36, 0.52], gnd: [0.22, 0.15, 0.10], fill: V.norm([0.8, 0.25, 0.55]), fillCol: [0.20, 0.19, 0.20],
    },
    hali: [0, 0.42, 0.34, 0.66],
    kur() {
      const m = new Mesh(), H = 3.2, x0 = -2.5, x1 = 4.2, z0 = -1.95, z1 = 5.0;
      const duvar = hex('#E9DFCB');
      // zemin (parke), arka duvar ve yan duvar (altında çini kuşağı)
      m.plane([x0, 0, z1], [x1, 0, z1], [x1, 0, z0], [x0, 0, z0], { col: hex('#8a5a33'), mat: MAT.AHSAP });
      m.plane([x0, 0, z0], [x1, 0, z0], [x1, H, z0], [x0, H, z0], { col: duvar, mat: MAT.DUVAR });
      m.plane([x0, 0, z1], [x0, 0, z0], [x0, H, z0], [x0, H, z1], { col: duvar, mat: MAT.DUVAR });
      // seccade ve iki ucundaki saçaklar
      const [hx, hz, hw, hl] = this.hali;
      m.box([hx, 0.004, hz], [hw, 0.004, hl], { col: hex('#7d1220'), mat: MAT.HALI });
      for (const s of [1, -1]) {
        const za = hz + s * hl, zb = hz + s * (hl + 0.055);
        const p = s > 0 ? [[hx - hw, 0.003, zb], [hx + hw, 0.003, zb], [hx + hw, 0.005, za], [hx - hw, 0.005, za]] : [[hx - hw, 0.005, za], [hx + hw, 0.005, za], [hx + hw, 0.003, zb], [hx - hw, 0.003, zb]];
        m.plane(...p, { col: hex('#EFE6CF'), mat: MAT.SACAK });
      }
      // pencere: sabah namazı vakti — gökyüzü lacivertten şafak kızıllığına
      const px = -1.15, py = 1.25, pw = 0.50, ph = 0.72, pz = z0 + 0.012;
      const gokUst = hex('#16234f'), gokOrta = hex('#5b5a8c'), gokAlt = hex('#f0a060');
      m.plane([px - pw, py, pz], [px + pw, py, pz], [px + pw, py + ph * 0.55, pz], [px - pw, py + ph * 0.55, pz], { cols: [gokAlt, gokAlt, gokOrta, gokOrta], mat: MAT.ISIK });
      m.plane([px - pw, py + ph * 0.55, pz], [px + pw, py + ph * 0.55, pz], [px + pw, py + ph, pz], [px - pw, py + ph, pz], { cols: [gokOrta, gokOrta, gokUst, gokUst], mat: MAT.ISIK });
      // kemerli üst kısım: yarım daire dilimleri
      const N = 14, yk = py + ph;
      for (let i = 0; i < N; i++) {
        const a0 = Math.PI * i / N, a1 = Math.PI * (i + 1) / N;
        const id = [[px, yk, pz], [px + pw * Math.cos(a0), yk + pw * Math.sin(a0), pz], [px + pw * Math.cos(a1), yk + pw * Math.sin(a1), pz]].map((p) => m.vert(p, gokUst, MAT.ISIK));
        m.tri(id[0], id[1], id[2]);
        // kemer çerçevesi
        const r0 = pw, r1 = pw + 0.07, q = (r, a) => [px + r * Math.cos(a), yk + r * Math.sin(a), pz + 0.02];
        m.plane(q(r0, a0), q(r1, a0), q(r1, a1), q(r0, a1), { col: hex('#6b4526'), mat: MAT.MAT_BOYA });
      }
      // hilal ve yıldız (pencereden görünen)
      m.ball([px + 0.20, yk + 0.12, pz + 0.004], [0.075, 0.075, 0.002], { col: hex('#fff3c4'), mat: MAT.ISIK, lat: 6, lon: 20 });
      m.ball([px + 0.235, yk + 0.14, pz + 0.006], [0.066, 0.066, 0.002], { col: hex('#1a2854'), mat: MAT.ISIK, lat: 6, lon: 20 });
      m.ball([px - 0.18, yk + 0.02, pz + 0.004], [0.014, 0.014, 0.002], { col: hex('#fff3c4'), mat: MAT.ISIK, lat: 4, lon: 8 });
      m.ball([px - 0.02, yk + 0.30, pz + 0.004], [0.010, 0.010, 0.002], { col: hex('#fff3c4'), mat: MAT.ISIK, lat: 4, lon: 8 });
      // pencere çerçevesi, orta kayıtlar ve denizlik
      const ah = hex('#6b4526');
      for (const s of [1, -1]) m.box([px + s * (pw + 0.035), py + ph / 2, pz + 0.02], [0.035, ph / 2 + 0.01, 0.02], { col: ah, mat: MAT.MAT_BOYA });
      m.box([px, py + ph * 0.5, pz + 0.012], [0.012, ph * 0.5 + pw * 0.5, 0.012], { col: ah, mat: MAT.MAT_BOYA });
      m.box([px, yk, pz + 0.012], [pw, 0.012, 0.012], { col: ah, mat: MAT.MAT_BOYA });
      m.box([px, py - 0.03, pz + 0.05], [pw + 0.11, 0.03, 0.06], { col: ah, mat: MAT.MAT_BOYA });
      // rahle ve açık Kur'an (arkada, sağda)
      {
        const T = M.mul(M.trans(-1.25, 0, -0.95), M.rotY(28 * RAD)), c = hex('#7a4a26');
        for (const s of [1, -1]) m.box([0, 0, 0], [0.19, 0.24, 0.012], { col: c, mat: MAT.PARLAK, m: M.mul(T, M.mul(M.trans(0, 0.21, 0), M.rotX(s * 30 * RAD))) });
        for (const s of [1, -1]) m.box([0, 0, 0], [0.13, 0.008, 0.10], { col: hex('#f3ecd8'), mat: MAT.MAT_BOYA, m: M.mul(T, M.mul(M.trans(0, 0.40, s * 0.09), M.rotX(-s * 30 * RAD))) });
      }
      // duvar dibinde minderler
      const minder = [['#2f6d62', -2.02, -1.55], ['#a8433a', -1.30 + 3.0, -1.62], ['#c79a3c', 2.42, -1.60]];
      for (const [c, x, z] of minder) m.tube([[0.0, 0.26], [0.03, 0.30], [0.09, 0.30], [0.12, 0.26]].map(([y, r]) => ({ c: [x, y, z], rx: r, rz: r * 0.8, e: 3.2 })), { n: 20, col: hex(c), capB: true });
      // alçak kitaplık
      {
        const x = 1.75, z = z0 + 0.16, c = hex('#6b4526');
        m.box([x, 0.30, z], [0.50, 0.30, 0.15], { col: c, mat: MAT.MAT_BOYA });
        const kitap = ['#2f6d62', '#a8433a', '#c79a3c', '#35518a', '#7a3b6b', '#d8cdb0'];
        for (let i = 0; i < 11; i++) m.box([x - 0.40 + i * 0.07, 0.60 + 0.10 + (i % 3) * 0.012, z], [0.028, 0.10 + (i % 3) * 0.012, 0.10], { col: hex(kitap[i % 6]), mat: MAT.MAT_BOYA });
      }
      // sarkıt lamba (odanın sıcak ışığı)
      boru(m, [[-0.2, H, -0.9], [-0.2, 2.42, -0.9]], 0.006, { col: hex('#3a2a18') });
      m.tube([[2.44, 0.02], [2.40, 0.10], [2.30, 0.15], [2.22, 0.16]].map(([y, r]) => ({ c: [-0.2, y, -0.9], rx: r, rz: r })), { n: 20, col: hex('#b8862e'), mat: MAT.PARLAK, capA: true });
      m.ball([-0.2, 2.24, -0.9], [0.09, 0.07, 0.09], { col: hex('#ffe2a0'), mat: MAT.ISIK, lat: 8, lon: 14 });
      return { ana: m.build() };
    },
  },

  // ------------------------------------------------------------------ Abdesthane
  ABDEST: {
    yaw: 40, egim: 13, uzak: 3.5, bak: [0, 0.80, 0.16],
    isik: {
      sun: V.norm([-0.42, 0.90, 0.55]), sunCol: [2.35, 2.25, 2.05],
      sky: [0.42, 0.52, 0.58], gnd: [0.20, 0.21, 0.20], fill: V.norm([0.8, 0.3, 0.5]), fillCol: [0.20, 0.21, 0.22],
    },
    hali: [0, 0, 0.001, 0.001],
    kur() {
      const m = new Mesh(), H = 3.0, x0 = -1.9, x1 = 4.2, z0 = -1.25, z1 = 5.0;
      m.plane([x0, 0, z1], [x1, 0, z1], [x1, 0, z0], [x0, 0, z0], { col: hex('#c8c6ba'), mat: MAT.TAS });
      m.plane([x0, 0, z0], [x1, 0, z0], [x1, H, z0], [x0, H, z0], { col: hex('#e6ebe8'), mat: MAT.CINI });
      m.plane([x0, 0, z1], [x0, 0, z0], [x0, H, z0], [x0, H, z1], { col: hex('#e6ebe8'), mat: MAT.CINI });
      // tabure: yuvarlak oturak + üç ayak
      const ah = hex('#8b5a35');
      m.tube([[0.222, 0.150], [0.232, 0.160], [0.256, 0.160], [0.262, 0.150]].map(([y, r]) => ({ c: [0, y, -0.01], rx: r, rz: r })), { n: 28, col: ah, mat: MAT.PARLAK, capA: true, capB: true });
      for (let i = 0; i < 3; i++) { const a = i * 2.094 + 0.5; boru(m, [[Math.cos(a) * 0.085, 0.225, -0.01 + Math.sin(a) * 0.085], [Math.cos(a) * 0.125, 0, -0.01 + Math.sin(a) * 0.125]], 0.017, { col: hex('#75492a'), mat: MAT.MAT_BOYA }); }
      // mermer musluk taşı ve pirinç musluk
      const mer = hex('#dcdad0'), pir = hex('#c79a3c');
      m.box([0, 0.27, 0.74], [0.125, 0.27, 0.075], { col: mer, mat: MAT.PARLAK });
      m.box([0, 0.555, 0.74], [0.145, 0.016, 0.092], { col: hex('#c9c6ba'), mat: MAT.PARLAK });
      boru(m, [[0, 0.46, 0.67], [0, 0.46, 0.60], [0, 0.468, 0.52], [0, 0.462, 0.468], [0, 0.445, 0.448], [0, 0.425, 0.445]], [0.017, 0.016, 0.014, 0.013, 0.012, 0.012], { col: pir, mat: MAT.PARLAK });
      boru(m, [[0, 0.46, 0.625], [0, 0.505, 0.625]], 0.010, { col: pir, mat: MAT.PARLAK });
      m.box([0, 0.510, 0.625], [0.035, 0.006, 0.008], { col: pir, mat: MAT.PARLAK });
      // gider ızgarası (suyun aktığı yer)
      m.box([0, 0.002, 0.445], [0.16, 0.002, 0.055], { col: hex('#3c4444'), mat: MAT.MAT_BOYA });
      for (let i = -3; i <= 3; i++) m.box([i * 0.045, 0.005, 0.445], [0.006, 0.002, 0.05], { col: hex('#8d9796'), mat: MAT.PARLAK });
      // taşın üstünde bakır ibrik
      {
        const c = hex('#b5652f'), x = -0.03, z = 0.74, y = 0.571;
        m.tube([[0, 0.030], [0.02, 0.046], [0.05, 0.050], [0.08, 0.040], [0.10, 0.022], [0.125, 0.018], [0.135, 0.026]].map(([dy, r]) => ({ c: [x, y + dy, z], rx: r, rz: r })), { n: 16, col: c, mat: MAT.PARLAK, capA: true, capB: true });
        boru(m, [[x + 0.04, y + 0.05, z], [x + 0.085, y + 0.075, z], [x + 0.10, y + 0.115, z]], [0.009, 0.007, 0.005], { col: c, mat: MAT.PARLAK });
        boru(m, [[x - 0.02, y + 0.12, z], [x - 0.065, y + 0.10, z], [x - 0.062, y + 0.05, z], [x - 0.045, y + 0.035, z]], 0.005, { col: c, mat: MAT.PARLAK });
      }
      // arka duvarda askılık ve havlular
      m.box([-0.55, 1.22, z0 + 0.03], [0.42, 0.012, 0.012], { col: hex('#75492a'), mat: MAT.MAT_BOYA });
      const havlu = [['#f3efe4', -0.80], ['#2f8f8a', -0.52], ['#f3efe4', -0.25]];
      for (const [c, x] of havlu) {
        m.tube([[1.235, 0.10, 0.014], [1.20, 0.105, 0.020], [0.95, 0.105, 0.020], [0.80, 0.105, 0.016]].map(([y, rx, rz]) => ({ c: [x, y, z0 + 0.045], rx, rz, e: 4 })), { n: 16, col: hex(c), capA: true, capB: true });
      }
      // köşede saksı
      {
        const x = 1.75, z = -0.85;
        m.tube([[0, 0.11], [0.02, 0.13], [0.26, 0.17], [0.28, 0.175]].map(([y, r]) => ({ c: [x, y, z], rx: r, rz: r })), { n: 20, col: hex('#b5653a'), mat: MAT.MAT_BOYA, capA: true, capB: true });
        for (let i = 0; i < 9; i++) {
          const a = i * 0.7, e = 0.25 + (i % 3) * 0.2, boy = 0.42 + (i % 4) * 0.10;
          const uc = [x + Math.cos(a) * e * boy, 0.28 + boy * Math.cos(e), z + Math.sin(a) * e * boy];
          const orta = [x + Math.cos(a) * e * boy * 0.45, 0.28 + boy * 0.55, z + Math.sin(a) * e * boy * 0.45];
          m.tube([{ c: [x, 0.27, z], r: 0.012 }, { c: orta, r: 0.05 }, { c: uc, r: 0.004 }].map((q) => ({ c: q.c, ax: [Math.sin(a), 0, -Math.cos(a)], az: [0, 0.2, 1], rx: q.r, rz: 0.004 })), { n: 8, col: hex(i % 2 ? '#2f7d4f' : '#3d9460') });
        }
      }
      // akan su (yalnızca musluk açıkken çizilir)
      const su = new Mesh();
      su.tube([[0.428, 0.0085], [0.30, 0.0075], [0.15, 0.0068], [0.008, 0.0062]].map(([y, r]) => ({ c: [0, y, 0.445], rx: r, rz: r })), { n: 10, col: hex('#bfe8ff'), mat: MAT.SU });
      return { ana: m.build(), su: su.build() };
    },
  },
};
