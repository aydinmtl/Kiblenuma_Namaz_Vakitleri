// =====================================================================================
// motor.js — Küçük 3B çizim motoru (WebGL2). Hazır kütüphane, model ya da doku YOK:
// her şey bu dosyalardaki kodla üretilir.
//
// İçindekiler:
//   1) Vektör / matris / dörtlü (quaternion) yardımcıları
//   2) Örgü (mesh) kurucu: tüp, küre, kutu gibi şekilleri üçgenlere çevirir
//   3) Çizim programları (shader): ışık, gölge, kodla üretilen desenler, yüz boyama
//   4) Çizici: gölge haritası + ana çizim + parçacıklar
// =====================================================================================
'use strict';

// ---------------------------------------------------------------- 1) Matematik
const V = {
  add: (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]],
  sub: (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]],
  mul: (a, s) => [a[0] * s, a[1] * s, a[2] * s],
  dot: (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2],
  cross: (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]],
  len: (a) => Math.hypot(a[0], a[1], a[2]),
  norm: (a) => { const l = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; },
  lerp: (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t],
  dist: (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]),
};
const RAD = Math.PI / 180;
const clamp = (x, a, b) => Math.min(b, Math.max(a, x));
const lerp = (a, b, t) => a + (b - a) * t;
/** Yumuşak başlayıp yumuşak biten geçiş eğrisi (0..1). */
const smooth = (t) => { t = clamp(t, 0, 1); return t * t * t * (t * (t * 6 - 15) + 10); };

// 4x4 matris: sütun öncelikli 16 sayı (WebGL'in beklediği düzen)
const M = {
  ident: () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1],
  mul(a, b) {
    const o = new Array(16);
    for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) {
      o[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
    }
    return o;
  },
  trans: (x, y, z) => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1],
  rotX(a) { const c = Math.cos(a), s = Math.sin(a); return [1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1]; },
  rotY(a) { const c = Math.cos(a), s = Math.sin(a); return [c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1]; },
  rotZ(a) { const c = Math.cos(a), s = Math.sin(a); return [c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; },
  /** Üç eksen (X, Y, Z sütunları) ve konumdan matris. */
  basis: (X, Y, Z, p) => [X[0], X[1], X[2], 0, Y[0], Y[1], Y[2], 0, Z[0], Z[1], Z[2], 0, p[0], p[1], p[2], 1],
  /** Noktayı matrisle taşı. */
  tp: (m, p) => [m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12], m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13], m[2] * p[0] + m[6] * p[1] + m[10] * p[2] + m[14]],
  /** Yönü matrisle döndür (konum eklenmez). */
  td: (m, d) => [m[0] * d[0] + m[4] * d[1] + m[8] * d[2], m[1] * d[0] + m[5] * d[1] + m[9] * d[2], m[2] * d[0] + m[6] * d[1] + m[10] * d[2]],
  pos: (m) => [m[12], m[13], m[14]],
  persp(fovy, asp, n, f) {
    const t = 1 / Math.tan(fovy / 2);
    return [t / asp, 0, 0, 0, 0, t, 0, 0, 0, 0, (f + n) / (n - f), -1, 0, 0, 2 * f * n / (n - f), 0];
  },
  ortho(l, r, b, t, n, f) {
    return [2 / (r - l), 0, 0, 0, 0, 2 / (t - b), 0, 0, 0, 0, -2 / (f - n), 0, -(r + l) / (r - l), -(t + b) / (t - b), -(f + n) / (f - n), 1];
  },
  lookAt(eye, at, up) {
    const z = V.norm(V.sub(eye, at)), x = V.norm(V.cross(up, z)), y = V.cross(z, x);
    return [x[0], y[0], z[0], 0, x[1], y[1], z[1], 0, x[2], y[2], z[2], 0, -V.dot(x, eye), -V.dot(y, eye), -V.dot(z, eye), 1];
  },
  /** Euler açılarından dönüş: önce Y (sağa sola), sonra X (öne eğilme), sonra Z (yana yatma). Derece. */
  euler: (pitch, yaw, roll) => M.mul(M.mul(M.rotY((yaw || 0) * RAD), M.rotX((pitch || 0) * RAD)), M.rotZ((roll || 0) * RAD)),
};

// Dörtlü (quaternion): iki yönelim arasında düzgün dönmek için. [x, y, z, w]
const Q = {
  fromMat(m) {
    const m00 = m[0], m11 = m[5], m22 = m[10], tr = m00 + m11 + m22;
    let x, y, z, w;
    if (tr > 0) { const s = Math.sqrt(tr + 1) * 2; w = s / 4; x = (m[6] - m[9]) / s; y = (m[8] - m[2]) / s; z = (m[1] - m[4]) / s; }
    else if (m00 > m11 && m00 > m22) { const s = Math.sqrt(1 + m00 - m11 - m22) * 2; w = (m[6] - m[9]) / s; x = s / 4; y = (m[4] + m[1]) / s; z = (m[8] + m[2]) / s; }
    else if (m11 > m22) { const s = Math.sqrt(1 + m11 - m00 - m22) * 2; w = (m[8] - m[2]) / s; x = (m[4] + m[1]) / s; y = s / 4; z = (m[9] + m[6]) / s; }
    else { const s = Math.sqrt(1 + m22 - m00 - m11) * 2; w = (m[1] - m[4]) / s; x = (m[8] + m[2]) / s; y = (m[9] + m[6]) / s; z = s / 4; }
    return [x, y, z, w];
  },
  slerp(a, b, t) {
    let d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
    if (d < 0) { b = [-b[0], -b[1], -b[2], -b[3]]; d = -d; }
    let ka, kb;
    if (d > 0.9995) { ka = 1 - t; kb = t; } else { const th = Math.acos(d), s = Math.sin(th); ka = Math.sin((1 - t) * th) / s; kb = Math.sin(t * th) / s; }
    const q = [a[0] * ka + b[0] * kb, a[1] * ka + b[1] * kb, a[2] * ka + b[2] * kb, a[3] * ka + b[3] * kb];
    const l = Math.hypot(q[0], q[1], q[2], q[3]);
    return [q[0] / l, q[1] / l, q[2] / l, q[3] / l];
  },
  toMat(q, p) {
    const [x, y, z, w] = q; p = p || [0, 0, 0];
    return [1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0,
      2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0,
      2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0, p[0], p[1], p[2], 1];
  },
};

/** "#RRGGBB" → [r, g, b] (0..1). */
function hex(h) { const n = parseInt(h.slice(1), 16); return [(n >> 16 & 255) / 255, (n >> 8 & 255) / 255, (n & 255) / 255]; }

// ---------------------------------------------------------------- 2) Örgü kurucu
// Malzeme numaraları (çizim programındaki dallarla aynı olmalı)
const MAT = { KUMAS: 0, TEN: 1, YUZ: 2, PARLAK: 3, AHSAP: 4, HALI: 5, DUVAR: 6, TAS: 7, ISIK: 8, ORGU: 9, SU: 10, SACAK: 11, MAT_BOYA: 12, CINI: 13 };

/**
 * Köşe (vertex) düzeni: konum(3) + renk(3) + malzeme(1) + kemik(2) + ağırlık(1) = 10 sayı.
 * Yüzey yönleri (normal) kurulduktan sonra üçgenlerden hesaplanır.
 */
class Mesh {
  constructor() { this.v = []; this.idx = []; this.n = 0; }
  /** Köşe ekler; sırasını döndürür. b0/b1: bağlı olduğu iki kemik, w: ilk kemiğin ağırlığı. */
  vert(p, col, mat, b0 = 0, b1 = 0, w = 1) {
    this.v.push(p[0], p[1], p[2], col[0], col[1], col[2], mat, b0, b1, w);
    return this.n++;
  }
  tri(a, b, c) { this.idx.push(a, b, c); }
  quad(a, b, c, d) { this.idx.push(a, b, c, a, c, d); }

  /**
   * Tüp: ardışık halkaları birleştirir. Her halka:
   *  { c:[x,y,z] merkez, ax, az: halka düzleminin iki ekseni, rx, rz: yarıçaplar,
   *    e: köşelilik (2 = elips, büyüdükçe kutuya yaklaşır), b:[kemik0, kemik1, ağırlık], col }
   * capA / capB: uçları kapat.
   */
  tube(rings, o = {}) {
    const N = o.n || 20, mat = o.mat || 0, start = [];
    for (const r of rings) {
      const ax = r.ax || [1, 0, 0], az = r.az || [0, 0, 1], e = r.e || 2, col = r.col || o.col, b = r.b || o.b || [0, 0, 1];
      start.push(this.n);
      for (let j = 0; j < N; j++) {
        const th = j / N * Math.PI * 2;
        let cx = Math.cos(th), sz = Math.sin(th);
        if (e !== 2) { cx = Math.sign(cx) * Math.pow(Math.abs(cx), 2 / e); sz = Math.sign(sz) * Math.pow(Math.abs(sz), 2 / e); }
        let x = cx * r.rx, z = sz * r.rz;
        if (r.f) [x, z] = r.f(x, z, th); // isteğe bağlı biçim düzeltmesi
        this.vert([r.c[0] + ax[0] * x + az[0] * z, r.c[1] + ax[1] * x + az[1] * z, r.c[2] + ax[2] * x + az[2] * z], col, r.mat ?? mat, b[0], b[1], b[2]);
      }
    }
    // Yüzlerin dışarı bakması için dolanma yönü: halkaların ilerleme yönüne göre otomatik seçilir.
    // o.flip verilirse tersine çevrilir (içe bakan yüzeyler: kol ağzının içi gibi).
    const r0 = rings[0], yon = V.sub(rings[rings.length - 1].c, r0.c);
    const oto = V.dot(V.cross(r0.ax || [1, 0, 0], r0.az || [0, 0, 1]), yon) > 0;
    const flip = oto !== !!o.flip;
    for (let i = 0; i + 1 < rings.length; i++) for (let j = 0; j < N; j++) {
      const a = start[i] + j, b = start[i] + (j + 1) % N, c = start[i + 1] + (j + 1) % N, d = start[i + 1] + j;
      if (flip) this.quad(a, b, c, d); else this.quad(a, d, c, b);
    }
    const cap = (ri, rev) => {
      const r = rings[ri], b = r.b || o.b || [0, 0, 1];
      const cv = this.vert(r.c, r.col || o.col, r.mat ?? mat, b[0], b[1], b[2]);
      for (let j = 0; j < N; j++) {
        const a = start[ri] + j, bb = start[ri] + (j + 1) % N;
        if (rev !== flip) this.tri(cv, a, bb); else this.tri(cv, bb, a);
      }
    };
    if (o.capA) cap(0, true);
    if (o.capB) cap(rings.length - 1, false);
    return this;
  }

  /**
   * Küre / elipsoit. f(p, yön) verilirse her köşe için çağrılır: {p, col, mat, b} döndürerek
   * şekli, rengi ya da kemik bağını değiştirebilir (ör. baş biçimi).
   */
  ball(c, r, o = {}) {
    const LA = o.lat || 14, LO = o.lon || 20, mat = o.mat || 0, b = o.b || [0, 0, 1], first = this.n;
    for (let i = 0; i <= LA; i++) {
      const ph = i / LA * Math.PI;
      for (let j = 0; j < LO; j++) {
        const th = j / LO * Math.PI * 2;
        const d = [Math.sin(ph) * Math.sin(th), Math.cos(ph), Math.sin(ph) * Math.cos(th)];
        let p = [c[0] + d[0] * r[0], c[1] + d[1] * r[1], c[2] + d[2] * r[2]], col = o.col, m = mat, bb = b;
        if (o.f) { const q = o.f(p, d); if (q) { p = q.p || p; col = q.col || col; m = q.mat ?? m; bb = q.b || bb; } }
        if (o.m) p = M.tp(o.m, p);
        this.vert(p, col, m, bb[0], bb[1], bb[2]);
      }
    }
    for (let i = 0; i < LA; i++) for (let j = 0; j < LO; j++) {
      const a = first + i * LO + j, b2 = first + i * LO + (j + 1) % LO, c2 = first + (i + 1) * LO + (j + 1) % LO, d2 = first + (i + 1) * LO + j;
      this.quad(a, d2, c2, b2);
    }
    return this;
  }

  /** Kutu: merkez c, yarı boyutlar h; m verilirse o matrisle döndürülür/taşınır. Köşeleri keskin kalır. */
  box(c, h, o = {}) {
    const mat = o.mat || 0, b = o.b || [0, 0, 1], col = o.col;
    const F = [[[1, 0, 0], [0, 1, 0], [0, 0, 1]], [[-1, 0, 0], [0, 0, 1], [0, 1, 0]], [[0, 1, 0], [0, 0, 1], [1, 0, 0]],
      [[0, -1, 0], [1, 0, 0], [0, 0, 1]], [[0, 0, 1], [1, 0, 0], [0, 1, 0]], [[0, 0, -1], [0, 1, 0], [1, 0, 0]]];
    for (const [n, u, w] of F) {
      const id = [];
      for (const [su, sw] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
        let p = [c[0] + (n[0] + u[0] * su + w[0] * sw) * h[0], c[1] + (n[1] + u[1] * su + w[1] * sw) * h[1], c[2] + (n[2] + u[2] * su + w[2] * sw) * h[2]];
        if (o.m) p = M.tp(o.m, p);
        id.push(this.vert(p, (o.cols && o.cols(n)) || col, mat, b[0], b[1], b[2]));
      }
      this.quad(id[0], id[1], id[2], id[3]);
    }
    return this;
  }

  /** Dört köşeli düz yüzey (köşeler saat yönünün tersine; yüz o tarafa bakar). */
  plane(p0, p1, p2, p3, o = {}) {
    const cs = o.cols || [o.col, o.col, o.col, o.col], mat = o.mat || 0;
    const id = [p0, p1, p2, p3].map((p, i) => this.vert(p, cs[i], mat));
    this.quad(id[0], id[1], id[2], id[3]);
    return this;
  }

  /** Köşe yönlerini (normal) üçgenlerden hesaplayıp çizime hazır diziyi döndürür. */
  build() {
    const n = this.n, v = this.v, nr = new Float32Array(n * 3);
    for (let i = 0; i < this.idx.length; i += 3) {
      const a = this.idx[i] * 10, b = this.idx[i + 1] * 10, c = this.idx[i + 2] * 10;
      const e1 = [v[b] - v[a], v[b + 1] - v[a + 1], v[b + 2] - v[a + 2]], e2 = [v[c] - v[a], v[c + 1] - v[a + 1], v[c + 2] - v[a + 2]];
      const f = V.cross(e1, e2);
      for (const k of [this.idx[i], this.idx[i + 1], this.idx[i + 2]]) { nr[k * 3] += f[0]; nr[k * 3 + 1] += f[1]; nr[k * 3 + 2] += f[2]; }
    }
    const out = new Float32Array(n * 13);
    for (let i = 0; i < n; i++) {
      const l = Math.hypot(nr[i * 3], nr[i * 3 + 1], nr[i * 3 + 2]) || 1;
      out.set(v.slice(i * 10, i * 10 + 3), i * 13);
      out[i * 13 + 3] = nr[i * 3] / l; out[i * 13 + 4] = nr[i * 3 + 1] / l; out[i * 13 + 5] = nr[i * 3 + 2] / l;
      out.set(v.slice(i * 10 + 3, i * 10 + 10), i * 13 + 6);
    }
    return { verts: out, idx: new Uint32Array(this.idx) };
  }
}

// ---------------------------------------------------------------- 3) Çizim programları
const KEMIK_SAYISI = 30;

const VS = `#version 300 es
layout(location=0) in vec3 aPos; layout(location=1) in vec3 aNrm; layout(location=2) in vec3 aCol;
layout(location=3) in float aMat; layout(location=4) in vec2 aBone; layout(location=5) in float aW;
uniform mat4 uBones[${KEMIK_SAYISI}]; uniform mat4 uVP; uniform mat4 uLightVP;
out vec3 vW; out vec3 vN; out vec3 vC; out vec3 vB; out vec4 vL; flat out int vMat;
void main(){
  // Deri kaplama (skinning): köşe, bağlı olduğu iki kemiğin hareketinin karışımıyla taşınır
  mat4 m = uBones[int(aBone.x)]*aW + uBones[int(aBone.y)]*(1.0-aW);
  vec4 w = m*vec4(aPos,1.0);
  vN = normalize(mat3(m)*aNrm);
  vW = w.xyz; vC = aCol; vB = aPos; vMat = int(aMat+0.5);
  vL = uLightVP*vec4(w.xyz + vN*0.006, 1.0);
  gl_Position = uVP*w;
}`;

// Gölge haritası için: yalnızca derinlik yazılır
const VS_DEPTH = `#version 300 es
layout(location=0) in vec3 aPos; layout(location=4) in vec2 aBone; layout(location=5) in float aW;
uniform mat4 uBones[${KEMIK_SAYISI}]; uniform mat4 uLightVP;
void main(){ mat4 m = uBones[int(aBone.x)]*aW + uBones[int(aBone.y)]*(1.0-aW); gl_Position = uLightVP*(m*vec4(aPos,1.0)); }`;
const FS_DEPTH = `#version 300 es
precision mediump float; void main(){}`;

const FS = `#version 300 es
precision highp float;
in vec3 vW; in vec3 vN; in vec3 vC; in vec3 vB; in vec4 vL; flat in int vMat;
uniform highp sampler2DShadow uShadow;
uniform vec3 uCam; uniform vec3 uSun; uniform vec3 uSunCol; uniform vec3 uSky; uniform vec3 uGnd;
uniform vec3 uFill; uniform vec3 uFillCol;
uniform float uTime; uniform vec2 uRes;
uniform vec4 uFace;   // x: göz kapağı (0 açık, 1 kapalı), y: ağız açıklığı, z: gülümseme, w: şaşkınlık
uniform vec4 uGaze;   // xy: bakış yönü, z: kız mı (başörtüsü), w: kullanılmıyor
uniform vec3 uHead;   // başın merkezi (duruş öncesi konum) — yüz boyaması bununla hizalanır
uniform vec4 uRug;    // seccade: merkez x, merkez z, yarı en, yarı boy
uniform vec4 uBlob[6]; // yere düşen yumuşak temas gölgeleri: xyz konum, w yarıçap
uniform vec3 uScarf;  // başörtüsü rengi
uniform float uFade;  // sahne geçişinde kararma (0 normal, 1 tamamen karanlık)
out vec4 oCol;

float hash(vec2 p){ p = fract(p*vec2(123.34, 456.21)); p += dot(p, p+45.32); return fract(p.x*p.y); }
float noise(vec2 p){ vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
  return mix(mix(hash(i),hash(i+vec2(1,0)),f.x), mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x), f.y); }
float fbm(vec2 p){ float a=0.5, s=0.0; for(int i=0;i<4;i++){ s+=a*noise(p); p=p*2.03+7.1; a*=0.5; } return s; }
vec3 lin(vec3 c){ return pow(c, vec3(2.2)); }
// Kenarları yumuşatılmış adım (desen kenarları tırtıklı görünmesin)
float aa(float d){ float w = fwidth(d)*0.75 + 1e-5; return smoothstep(-w, w, d); }

float shadow(){
  vec3 p = vL.xyz/vL.w*0.5+0.5;
  if(p.x<0.0||p.x>1.0||p.y<0.0||p.y>1.0||p.z>1.0) return 1.0;
  float s=0.0; float t = 1.0/2048.0;
  // 9 örnekle yumuşatılmış gölge kenarı
  for(int i=-1;i<=1;i++) for(int j=-1;j<=1;j++) s += texture(uShadow, vec3(p.xy+vec2(float(i),float(j))*t*1.4, p.z-0.0012));
  return s/9.0;
}

// Başta saç (erkek) ya da başörtüsü (kız) nerede? Pozitif: saç/örtü, negatif: yüz.
// Aynı formül karakter.js içinde de var (basMaske); ikisi birlikte değiştirilmeli.
float basMaske(vec3 d, float kiz){
  if(kiz < 0.5){ float f = smoothstep(-0.25, 0.55, d.z); return d.y - mix(-0.42, 0.50 + 0.05*sin(d.x*10.0+1.0), f); }
  return d.z > 0.0 ? length(vec2(d.x/0.78, (d.y+0.10)/0.72)) - 1.0 : 1.0;
}

// ---- Yüz: gözler, kaşlar, ağız ve yanaklar başın ön yüzüne boyanır ----
vec3 yuz(vec3 ten, vec3 d, out float parlak){
  parlak = 0.0;
  vec2 q = d.xy / max(d.z, 0.2);          // ön yüzde düzlemsel koordinat
  if(d.z < 0.15) return ten;
  vec3 col = ten;
  // yanak allığı
  float al = smoothstep(0.26, 0.0, length((vec2(abs(q.x), q.y) - vec2(0.56, -0.36))*vec2(1.0,1.25)));
  col = mix(col, vec3(0.93, 0.48, 0.44), al*0.42);
  float sx = sign(q.x);
  vec2 e = vec2(abs(q.x) - 0.365, q.y + 0.07);
  // göz akı (dik oval)
  float ak = 1.0 - length(e/vec2(0.150, 0.195));
  float lidY = mix(0.215, -0.205, uFace.x);            // üst göz kapağı çizgisi
  float acik = aa(ak) * aa(lidY - e.y + abs(e.x)*0.25*uFace.x);
  // iris ve göz bebeği bakışa göre kayar
  vec2 g = e - vec2(uGaze.x*0.05*sx, uGaze.y*0.065);
  float ir = length(g/vec2(0.108, 0.128));
  vec3 goz = vec3(0.985,0.975,0.96);
  goz = mix(goz, vec3(0.80,0.83,0.88), smoothstep(0.03, 0.22, e.y)*0.45);        // kapak gölgesi
  vec3 iris = mix(vec3(0.33,0.19,0.09), vec3(0.12,0.065,0.03), smoothstep(0.35,1.0,ir));
  iris = mix(iris, vec3(0.55,0.36,0.17), smoothstep(0.55,0.0,length((g-vec2(0.0,-0.045))/vec2(0.09,0.065)))*0.55);
  goz = mix(goz, iris, aa(1.0-ir));
  goz = mix(goz, vec3(0.02,0.015,0.015), aa(0.42-ir));                           // göz bebeği
  float isik = aa(1.0 - length((g - vec2(-0.036, 0.046))/vec2(0.034)));        // ışıltı
  goz = mix(goz, vec3(1.0), isik);
  goz = mix(goz, vec3(1.0), aa(1.0 - length((g - vec2(0.04, -0.04))/vec2(0.015)))*0.7);
  col = mix(col, goz, acik);
  parlak = acik;
  // kirpik çizgisi (üst kapak kenarı) ve kapalı göz çizgisi
  float ust = abs(ak) - 0.07;
  float kenar = (1.0-aa(ust)) * aa(e.y - mix(0.02,-0.4,uFace.x)) * aa(lidY - e.y + 0.03);
  col = mix(col, vec3(0.13,0.08,0.06), kenar*0.9);
  float kapali = (1.0-aa(abs(e.y - lidY + abs(e.x)*0.25*uFace.x) - 0.014)) * aa(ak+0.02) * smoothstep(0.25,0.6,uFace.x);
  col = mix(col, vec3(0.13,0.08,0.06), kapali*0.9);
  // kaşlar
  float kx = abs(q.x) - 0.365;
  vec2 k = vec2(kx, q.y - 0.262 - uFace.w*0.05 + kx*kx*1.6 + kx*(uFace.w*0.45 - 0.04));
  float kas = 1.0 - length(k/vec2(0.135, 0.026));
  col = mix(col, vec3(0.20,0.12,0.08), aa(kas)*0.85);
  // ağız: gülümseme yayı; konuşurken açılır
  vec2 m = vec2(q.x, q.y + 0.56);
  float w = 0.135 + uFace.z*0.03;
  float egri = m.y - (uFace.z*0.95 - uFace.w*0.6)*(m.x*m.x)/w + uFace.z*0.03;
  float ac = uFace.y*0.075;
  float agizIc = aa(1.0 - length(vec2(m.x/(w*0.82), (egri+ac*0.6)/max(ac,0.001))));
  agizIc *= step(0.004, ac);
  float cizgi = (1.0-aa(abs(egri) - 0.012)) * aa(w - abs(m.x));
  col = mix(col, vec3(0.50,0.20,0.17), cizgi*(1.0-agizIc)*0.9);
  vec3 ic = mix(vec3(0.36,0.09,0.09), vec3(0.85,0.42,0.42), smoothstep(-0.3,-1.0,(egri+ac*0.6)/max(ac,0.001)));
  col = mix(col, ic, agizIc);
  return col;
}

// ---- Seccade deseni ----
vec3 seccade(vec2 p, vec2 h, out float tuy){
  vec2 a = abs(p);
  vec3 kirmizi = vec3(0.47,0.07,0.10), lacivert = vec3(0.07,0.11,0.26), altin = vec3(0.76,0.57,0.24), yesil = vec3(0.04,0.23,0.21), krem = vec3(0.86,0.80,0.66);
  vec3 col = kirmizi;
  float kenar = min(h.x-a.x, h.y-a.y);                 // kenara uzaklık
  // dış bordür
  col = mix(col, lacivert, 1.0-aa(kenar-0.075));
  col = mix(col, altin, (1.0-aa(abs(kenar-0.075)-0.006)));
  col = mix(col, altin, (1.0-aa(abs(kenar-0.012)-0.004)));
  // bordürde baklava dizisi
  if(kenar < 0.07 && kenar > 0.018){
    float t = (h.x-a.x < h.y-a.y) ? p.y : p.x;
    float u = abs(fract(t/0.075)-0.5)*0.075, v = abs(kenar-0.044);
    float bak = 0.021 - (u*0.75 + v);
    col = mix(col, krem, aa(bak));
    col = mix(col, kirmizi, aa(bak-0.009));
  }
  // orta alan: mihrap kemeri (ucu kıbleyi, yani +y yönünü gösterir)
  vec2 ih = h - 0.105;
  if(a.x < ih.x && a.y < ih.y){
    float r = ih.x;
    float yTop = ih.y - r*1.25;
    float d;                                              // kemerin içi (pozitif = içeride)
    if(p.y < yTop) d = min(ih.x - a.x, p.y + ih.y);
    else { // sivri kemer: iki daire yayının kesişimi
      d = r*1.62 - length(vec2(a.x + r*0.62, p.y - yTop));
    }
    d -= 0.03;
    vec3 ic = yesil;
    // kemerin içinde küçük yıldız serpintisi
    vec2 c = (fract(p/0.11 + vec2(0.0, 0.5*floor(p.x/0.11))) - 0.5)*0.11;
    float yil = 0.011 - (abs(c.x)+abs(c.y));
    ic = mix(ic, vec3(0.07,0.31,0.28), aa(yil+0.008));
    ic = mix(ic, altin, aa(yil));
    // kandil motifi (kemerin tepesinde asılı)
    vec2 kp = p - vec2(0.0, yTop - 0.02);
    float kan = 0.035 - length(kp*vec2(1.0,0.75));
    ic = mix(ic, altin, aa(kan));
    ic = mix(ic, kirmizi, aa(kan-0.014));
    ic = mix(ic, altin, (1.0-aa(abs(kp.x)-0.003))*step(0.0,kp.y)*step(kp.y,0.16));
    col = mix(col, altin, aa(d+0.012));
    col = mix(col, ic, aa(d));
    // köşe süsleri (kemerin dışında kalan üçgenlerde)
    vec2 ks = vec2(a.x - ih.x*0.72, p.y - (ih.y-0.07));
    float kos = 0.03 - length(ks);
    col = mix(col, krem, aa(kos)*(1.0-aa(d+0.012)));
    col = mix(col, lacivert, aa(kos-0.013)*(1.0-aa(d+0.012)));
  }
  // dokuma hissi
  tuy = noise(p*900.0)*0.5 + noise(p*vec2(260.0, 40.0))*0.5;
  return col * (0.82 + 0.3*tuy);
}

// ---- Çini karo deseni (duvar süslemesi) ----
vec3 cini(vec2 uv, float s){
  vec2 id = floor(uv/s); vec2 f = (fract(uv/s)-0.5)*2.0;
  vec3 beyaz = vec3(0.90,0.92,0.90), turkuaz = vec3(0.05,0.50,0.55), kobalt = vec3(0.06,0.16,0.50), mercan = vec3(0.72,0.22,0.16);
  vec3 col = beyaz;
  float r = length(f), a = atan(f.y, f.x);
  float yap = 0.62 + 0.20*cos(a*8.0);
  col = mix(col, turkuaz, aa(yap - r));
  col = mix(col, beyaz, aa(yap*0.72 - r));
  col = mix(col, kobalt, aa(0.34 + 0.07*cos(a*8.0+3.14159) - r));
  col = mix(col, mercan, aa(0.13 - r));
  vec2 k = abs(f) - 1.0;                                 // köşelerde çeyrek daireler
  col = mix(col, kobalt, aa(0.30 - length(k)));
  col = mix(col, beyaz, aa(0.17 - length(k)));
  float derz = min(1.0-abs(f.x), 1.0-abs(f.y));
  col = mix(vec3(0.62,0.66,0.66), col, aa(derz - 0.035));
  return col * (0.94 + 0.08*hash(id));
}

void main(){
  vec3 N = normalize(vN);
  vec3 Vd = normalize(uCam - vW);
  vec3 alb = lin(vC);
  float rough = 0.85, spec = 0.04, wrap = 0.25, sheen = 0.0;
  vec3 sss = vec3(0.0);
  float emis = 0.0, alpha = 1.0;

  if(vMat == 1){ // ten
    rough = 0.55; spec = 0.10; wrap = 0.45; sss = vec3(0.55,0.16,0.10);
  } else if(vMat == 2){ // baş: yüz, saç ya da başörtüsü
    vec3 d = normalize((vB - uHead)/vec3(0.118,0.125,0.122));
    float pr;
    vec3 ten = yuz(vec3(0.949,0.761,0.608), d, pr);
    float k = aa(basMaske(d, uGaze.z));               // 1: saç ya da örtü, 0: yüz
    vec3 ust;
    if(uGaze.z < 0.5){ // saç telleri
      float tel = noise(vec2(atan(d.x,d.z)*30.0 + d.y*4.0, d.y*6.0));
      ust = vec3(0.16,0.095,0.06) * (0.7 + 0.6*tel);
    } else { ust = uScarf * (0.95 + 0.1*noise(vB.xy*300.0)); }
    alb = lin(mix(ten, ust, k));
    pr *= 1.0-k;
    rough = mix(mix(0.55, 0.12, pr), uGaze.z < 0.5 ? 0.6 : 0.9, k);
    spec = mix(mix(0.10, 0.5, pr), uGaze.z < 0.5 ? 0.07 : 0.02, k);
    wrap = 0.45; sss = vec3(0.55,0.16,0.10)*(1.0-pr)*(1.0-k);
    sheen = 0.3*k*step(0.5, uGaze.z);
  } else if(vMat == 3){ // parlak (pirinç, seramik, cilalı ahşap)
    rough = 0.25; spec = 0.55; wrap = 0.05;
  } else if(vMat == 4){ // ahşap zemin (parke)
    vec2 p = vW.xz; float w = 0.16;
    float sira = floor(p.x/w); float kay = hash(vec2(sira, 3.0))*1.3;
    float boy = floor((p.y + kay)/1.1);
    float ton = hash(vec2(sira, boy));
    float damar = fbm(vec2(p.x*38.0 + ton*40.0, (p.y+kay)*2.6));
    alb = mix(lin(vec3(0.50,0.31,0.16)), lin(vec3(0.69,0.47,0.27)), ton*0.6 + damar*0.5);
    alb *= 0.86 + 0.28*fbm(vec2(p.x*90.0, p.y*5.0 + ton*9.0));
    float dx = abs(fract(p.x/w)-0.5)*w, dy = abs(fract((p.y+kay)/1.1)-0.5)*1.1;
    float derz = min(w*0.5-dx, 0.55-dy);
    alb *= mix(0.35, 1.0, aa(derz-0.0022));
    rough = 0.34; spec = 0.22; wrap = 0.0;
  } else if(vMat == 5){ // seccade
    float tuy; alb = lin(seccade(vB.xz - uRug.xy, uRug.zw, tuy));
    N = normalize(N + vec3(tuy-0.5, 0.0, noise(vB.xz*700.0)-0.5)*0.35);
    rough = 0.95; spec = 0.0; wrap = 0.35; sheen = 0.25;
  } else if(vMat == 6){ // sıvalı duvar + altta çini kuşağı
    float u = abs(N.z) > 0.5 ? vB.x : vB.z;
    vec3 siva = vC * (0.93 + 0.10*fbm(vec2(u, vB.y)*7.0)) * (0.97 + 0.05*noise(vec2(u, vB.y)*160.0));
    alb = lin(siva);
    float yk = 0.84;
    if(vB.y < yk + 0.09){
      vec3 c = cini(vec2(u + 0.07, vB.y - 0.06), 0.195);
      // üst bordür: kobalt şerit + altın çizgi
      vec3 bord = mix(vec3(0.06,0.16,0.50), vec3(0.80,0.62,0.25), 1.0-aa(abs(vB.y - (yk+0.045)) - 0.012));
      c = mix(c, bord, aa(vB.y - yk));
      c = mix(c, vec3(0.30,0.20,0.12), 1.0-aa(vB.y - 0.06));   // süpürgelik
      alb = lin(vB.y > 0.06 ? mix(c, vC*vec3(0.93,0.97,0.96), 0.52) : c); rough = 0.3; spec = vB.y > 0.06 ? 0.3 : 0.1; wrap = 0.0;
    }
  } else if(vMat == 7){ // taş / mermer karo zemin
    vec2 p = vW.xz; float s = 0.42;
    vec2 id = floor(p/s); vec2 f = fract(p/s);
    float dam = fbm(p*3.0 + fbm(p*5.0)*2.2);
    alb = lin(mix(vec3(0.80,0.79,0.74), vec3(0.62,0.64,0.62), dam) * (0.93+0.1*hash(id)));
    alb = mix(alb, lin(vec3(0.42,0.47,0.47)), smoothstep(0.52,0.60,fbm(p*9.0 + 3.0))*0.5);
    float derz = min(min(f.x,1.0-f.x), min(f.y,1.0-f.y))*s;
    alb *= mix(0.45, 1.0, aa(derz-0.004));
    rough = 0.22; spec = 0.4; wrap = 0.0;
  } else if(vMat == 8){ // kendi ışığı olan yüzey (pencere, lamba)
    emis = 1.0;
  } else if(vMat == 9){ // örgü takke
    vec3 d = normalize(vB - uHead);
    float a = atan(d.x, d.z), y = d.y;
    float orgu = sin(a*46.0 + sin(y*60.0)*1.2)*sin(y*72.0);
    alb *= 0.82 + 0.16*orgu;
    alb = mix(alb, alb*vec3(0.80,0.84,0.95), (1.0-aa(abs(fract(y*5.5)-0.5)-0.07))*0.5);
    N = normalize(N + vec3(orgu*0.25)*N.yzx);
    rough = 0.9; spec = 0.0; wrap = 0.4; sheen = 0.3;
  } else if(vMat == 10){ // akan su
    float ak = noise(vec2(vB.x*90.0 + vB.z*90.0, vB.y*22.0 + uTime*9.0));
    alb = lin(vec3(0.62,0.84,0.95)) * (0.8+0.6*ak);
    rough = 0.1; spec = 0.9; alpha = 0.50 + 0.3*ak; wrap = 0.6;
  } else if(vMat == 11){ // seccade saçağı (ipler arası boş)
    float ip = abs(fract(vB.x*130.0)-0.5);
    if(ip > 0.30 + 0.15*noise(vec2(vB.x*130.0, 1.0))) discard;
    rough = 0.95; spec = 0.0; wrap = 0.4;
  } else if(vMat == 12){ // mat boya / sıva / ahşap
    alb *= 0.9 + 0.2*fbm(vB.xy*14.0 + vB.z*9.0);
    rough = 0.7; spec = 0.06;
  } else if(vMat == 13){ // tamamen çini kaplı duvar (abdesthane)
    float u = abs(N.z) > 0.5 ? vB.x : vB.z;
    vec3 c;
    if(vB.y < 1.44){ c = cini(vec2(u + 0.07, vB.y), 0.24); }
    else {
      c = vec3(0.90,0.92,0.90);
      vec2 f = fract(vec2(u + 0.07, vB.y)/0.24);
      float derz = min(min(f.x,1.0-f.x), min(f.y,1.0-f.y));
      c = mix(vec3(0.62,0.66,0.66), c, aa(derz-0.018)) * (0.95 + 0.07*hash(floor(vec2(u + 0.07, vB.y)/0.24)));
      vec3 bord = mix(vec3(0.05,0.50,0.55), vec3(0.80,0.62,0.25), 1.0-aa(abs(vB.y - 1.50) - 0.012));
      c = mix(c, bord, 1.0-aa(abs(vB.y-1.50)-0.06));
    }
    alb = lin(mix(c, vec3(0.90,0.93,0.92), 0.58)); rough = 0.25; spec = 0.4; wrap = 0.0;
  } else { // kumaş
    float doku = noise(vB.xy*420.0 + vB.z*300.0);
    alb *= 0.93 + 0.12*doku; sheen = 0.25; rough = 0.9; spec = 0.02; wrap = 0.35;
  }

  vec3 col;
  if(emis > 0.5){ col = lin(vC)*2.2; }
  else {
    vec3 L = normalize(uSun);
    float sh = shadow();
    float ndl = dot(N, L);
    float dif = clamp((ndl + wrap)/(1.0 + wrap), 0.0, 1.0);
    // gövdenin ışığa bakmayan yüzü zaten karanlık; gölge yalnızca doğrudan ışığı keser
    float direct = dif * mix(1.0, sh, smoothstep(-wrap, 0.25, ndl + 0.05));
    vec3 amb = mix(uGnd, uSky, N.y*0.5+0.5);
    // yere yakın temas gölgeleri (ayak, diz, el altı yumuşak kararma)
    float ao = 1.0;
    for(int i=0;i<6;i++){
      vec3 dv = vW - uBlob[i].xyz; float r = uBlob[i].w;
      if(r > 0.0){ float dd = length(dv*vec3(1.0, 1.6, 1.0)); ao *= 1.0 - 0.55*smoothstep(r, r*0.15, dd)*smoothstep(-0.9, 0.3, N.y); }
    }
    amb *= ao;
    // dolgu ışığı: gölgede kalan tarafı hafifçe aydınlatır
    float fil = clamp(dot(N, normalize(uFill))*0.5+0.5, 0.0, 1.0);
    vec3 lit = uSunCol*direct + amb + uFillCol*fil*fil*ao;
    col = alb * lit;
    // ten: ışık-gölge sınırında hafif kızıllık (derinin ışığı geçirmesi)
    col += alb * sss * uSunCol * smoothstep(0.0, 0.5, dif) * smoothstep(1.0, 0.35, dif) * 0.22;
    // parlama
    vec3 H = normalize(L + Vd);
    float sp = pow(max(dot(N, H), 0.0), 2.0/(rough*rough*rough + 0.004));
    col += uSunCol * sp * spec * direct * 1.2;
    vec3 H2 = normalize(normalize(uFill) + Vd);
    col += uFillCol * pow(max(dot(N, H2), 0.0), 2.0/(rough*rough*rough + 0.004)) * spec * 0.6;
    // kenar ışığı ve kumaş yumuşaklığı
    float fr = pow(1.0 - max(dot(N, Vd), 0.0), 3.0);
    col += uSky * fr * (0.20 + spec*1.5) * (0.4 + 0.6*clamp(N.y+0.6,0.0,1.0)) * ao;
    col += alb * sheen * fr * (uSunCol*0.35 + uSky);
  }
  // film eğrisi (ACES yaklaşık) + gama
  col *= 1.05;
  col = (col*(2.51*col+0.03))/(col*(2.43*col+0.59)+0.14);
  col = pow(clamp(col, 0.0, 1.0), vec3(1.0/2.2));
  // köşe karartması ve ince kumlanma (renk bantlarını önler)
  vec2 uv = gl_FragCoord.xy/uRes - 0.5;
  col *= 1.0 - 0.38*dot(uv, uv)*1.6;
  col += (hash(gl_FragCoord.xy + uTime) - 0.5)/255.0*1.5;
  col *= 1.0 - uFade;
  oCol = vec4(col, alpha);
}`;

// Parçacıklar (ışıltı, su damlası, konfeti): ekranda küçük noktalar
const VS_P = `#version 300 es
layout(location=0) in vec3 aPos; layout(location=1) in vec4 aCol; layout(location=2) in vec2 aSz;
uniform mat4 uVP; uniform float uH;
out vec4 vC; out float vK; out float vR;
void main(){ gl_Position = uVP*vec4(aPos,1.0); gl_PointSize = max(1.5, aSz.x*uH/gl_Position.w); vC = aCol; vK = floor(aSz.y); vR = fract(aSz.y)*6.2832; }`;
const FS_P = `#version 300 es
precision highp float; in vec4 vC; in float vK; in float vR; out vec4 oCol;
void main(){
  vec2 p = gl_PointCoord*2.0-1.0; float a;
  if(vK < 0.5){ a = smoothstep(1.0, 0.5, length(p)); }                 // su damlası
  else if(vK < 1.5){ // ışıltı: dört kollu yıldız
    float s = max(0.0, 1.0 - (abs(p.x)*abs(p.y)*14.0 + dot(p,p)*0.9)); a = s*s + smoothstep(0.35,0.0,length(p));
  } else { // konfeti: dönen dikdörtgen
    float c = cos(vR), s = sin(vR); vec2 q = vec2(c*p.x - s*p.y, s*p.x + c*p.y);
    a = step(abs(q.x), 0.85)*step(abs(q.y), 0.45);
  }
  oCol = vec4(vC.rgb, vC.a*clamp(a,0.0,1.0));
}`;

// ---------------------------------------------------------------- 4) Çizici
class Cizici {
  constructor(canvas) {
    this.canvas = canvas;
    const gl = canvas.getContext('webgl2', { antialias: true, alpha: false, powerPreference: 'high-performance' });
    if (!gl) throw new Error('WebGL2 yok');
    this.gl = gl;
    this.prog = this.program(VS, FS);
    this.depth = this.program(VS_DEPTH, FS_DEPTH);
    this.pprog = this.program(VS_P, FS_P);
    // Gölge haritası: ışığın gözünden derinlik dokusu
    this.SH = 2048;
    this.shTex = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, this.shTex);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.DEPTH_COMPONENT24, this.SH, this.SH, 0, gl.DEPTH_COMPONENT, gl.UNSIGNED_INT, null);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_COMPARE_MODE, gl.COMPARE_REF_TO_TEXTURE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_COMPARE_FUNC, gl.LEQUAL);
    this.shFbo = gl.createFramebuffer();
    gl.bindFramebuffer(gl.FRAMEBUFFER, this.shFbo);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.TEXTURE_2D, this.shTex, 0);
    gl.drawBuffers([gl.NONE]); gl.readBuffer(gl.NONE);
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    // Parçacık tamponu
    this.pvao = gl.createVertexArray(); this.pbuf = gl.createBuffer();
    gl.bindVertexArray(this.pvao); gl.bindBuffer(gl.ARRAY_BUFFER, this.pbuf);
    gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 3, gl.FLOAT, false, 36, 0);
    gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 4, gl.FLOAT, false, 36, 12);
    gl.enableVertexAttribArray(2); gl.vertexAttribPointer(2, 2, gl.FLOAT, false, 36, 28);
    gl.bindVertexArray(null);
    this.identBones = new Float32Array(KEMIK_SAYISI * 16);
    for (let i = 0; i < KEMIK_SAYISI; i++) this.identBones.set(M.ident(), i * 16);
  }

  program(vs, fs) {
    const gl = this.gl, p = gl.createProgram();
    for (const [t, src] of [[gl.VERTEX_SHADER, vs], [gl.FRAGMENT_SHADER, fs]]) {
      const s = gl.createShader(t); gl.shaderSource(s, src); gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error('Shader: ' + gl.getShaderInfoLog(s));
      gl.attachShader(p, s);
    }
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error('Program: ' + gl.getProgramInfoLog(p));
    const u = {}, n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
    for (let i = 0; i < n; i++) { const name = gl.getActiveUniform(p, i).name.replace('[0]', ''); u[name] = gl.getUniformLocation(p, name); }
    return { p, u };
  }

  /** Mesh.build() çıktısını ekran kartına yükler. */
  upload(built, o = {}) {
    const gl = this.gl, vao = gl.createVertexArray();
    gl.bindVertexArray(vao);
    const vb = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, vb); gl.bufferData(gl.ARRAY_BUFFER, built.verts, gl.STATIC_DRAW);
    const S = 52;
    [[0, 3, 0], [1, 3, 12], [2, 3, 24], [3, 1, 36], [4, 2, 40], [5, 1, 48]].forEach(([loc, sz, off]) => {
      gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, sz, gl.FLOAT, false, S, off);
    });
    const ib = gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ib); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, built.idx, gl.STATIC_DRAW);
    gl.bindVertexArray(null);
    return { vao, count: built.idx.length, bones: null, blend: !!o.blend, noShadow: !!o.noShadow, twoSided: !!o.twoSided, visible: true };
  }

  resize() {
    const c = this.canvas, dpr = Math.min(window.devicePixelRatio || 1, 2.5);
    const w = Math.max(2, Math.round(c.clientWidth * dpr)), h = Math.max(2, Math.round(c.clientHeight * dpr));
    if (c.width !== w || c.height !== h) { c.width = w; c.height = h; }
  }

  /**
   * Bir kare çizer.
   * items: [{vao, count, bones(Float32Array|null), blend, noShadow, visible}]
   * env: kamera ve ışık ayarları; parts: parçacık dizisi (Float32Array, köşe başına 9 sayı)
   */
  draw(items, env, parts, partCount) {
    const gl = this.gl; this.resize();
    const W = this.canvas.width, H = this.canvas.height;
    // --- 1. geçiş: gölge haritası ---
    gl.bindFramebuffer(gl.FRAMEBUFFER, this.shFbo);
    gl.viewport(0, 0, this.SH, this.SH);
    gl.enable(gl.DEPTH_TEST); gl.depthMask(true); gl.disable(gl.BLEND);
    gl.clear(gl.DEPTH_BUFFER_BIT);
    gl.useProgram(this.depth.p);
    gl.uniformMatrix4fv(this.depth.u.uLightVP, false, env.lightVP);
    gl.disable(gl.CULL_FACE);
    gl.enable(gl.POLYGON_OFFSET_FILL); gl.polygonOffset(2.0, 4.0);
    for (const it of items) {
      if (!it.visible || it.noShadow || it.blend) continue;
      gl.uniformMatrix4fv(this.depth.u.uBones, false, it.bones || this.identBones);
      gl.bindVertexArray(it.vao); gl.drawElements(gl.TRIANGLES, it.count, gl.UNSIGNED_INT, 0);
    }
    gl.disable(gl.POLYGON_OFFSET_FILL);
    // --- 2. geçiş: ekran ---
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    gl.viewport(0, 0, W, H);
    gl.clearColor(0.03, 0.045, 0.04, 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    const P = this.prog; gl.useProgram(P.p);
    gl.uniformMatrix4fv(P.u.uVP, false, env.vp);
    gl.uniformMatrix4fv(P.u.uLightVP, false, env.lightVP);
    gl.uniform3fv(P.u.uCam, env.cam); gl.uniform3fv(P.u.uSun, env.sun); gl.uniform3fv(P.u.uSunCol, env.sunCol);
    gl.uniform3fv(P.u.uSky, env.sky); gl.uniform3fv(P.u.uGnd, env.gnd);
    gl.uniform3fv(P.u.uFill, env.fill); gl.uniform3fv(P.u.uFillCol, env.fillCol);
    gl.uniform1f(P.u.uTime, env.time % 1000); gl.uniform2f(P.u.uRes, W, H);
    gl.uniform4fv(P.u.uFace, env.face); gl.uniform4fv(P.u.uGaze, env.gaze); gl.uniform3fv(P.u.uHead, env.head);
    gl.uniform3fv(P.u.uScarf, env.scarf);
    gl.uniform4fv(P.u.uRug, env.rug); gl.uniform4fv(P.u.uBlob, env.blobs); gl.uniform1f(P.u.uFade, env.fade);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, this.shTex); gl.uniform1i(P.u.uShadow, 0);
    for (const pass of [0, 1]) {
      if (pass === 1) { gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); gl.depthMask(false); }
      for (const it of items) {
        if (!it.visible || (it.blend ? 1 : 0) !== pass) continue;
        if (it.twoSided) gl.disable(gl.CULL_FACE); else { gl.enable(gl.CULL_FACE); gl.cullFace(gl.BACK); }
        gl.uniformMatrix4fv(P.u.uBones, false, it.bones || this.identBones);
        gl.bindVertexArray(it.vao); gl.drawElements(gl.TRIANGLES, it.count, gl.UNSIGNED_INT, 0);
      }
    }
    // --- parçacıklar ---
    if (partCount > 0) {
      gl.useProgram(this.pprog.p);
      gl.uniformMatrix4fv(this.pprog.u.uVP, false, env.vp); gl.uniform1f(this.pprog.u.uH, H * env.projScale);
      gl.bindVertexArray(this.pvao); gl.bindBuffer(gl.ARRAY_BUFFER, this.pbuf);
      gl.bufferData(gl.ARRAY_BUFFER, parts.subarray(0, partCount * 9), gl.DYNAMIC_DRAW);
      gl.drawArrays(gl.POINTS, 0, partCount);
    }
    gl.depthMask(true); gl.disable(gl.BLEND); gl.bindVertexArray(null);
  }
}
