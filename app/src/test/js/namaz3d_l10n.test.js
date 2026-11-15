'use strict';
// WebGL gerektirmeden gerçek HTML dil seçimi ve içerik uyarlamasını denetler.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const html = fs.readFileSync(path.resolve(__dirname, '../../main/assets/namaz3d/index.html'), 'utf8');
for (const script of html.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)) {
  if (!script[1].includes('x-shader')) new vm.Script(script[2]);
}
const localization = html.match(/<script id="lesson-l10n">([\s\S]*?)<\/script>/);
assert.ok(localization, '3B dersin dil uyarlaması bulunmalı');
const canonical = html.match(/const STEPS=localizeSteps\((\[[\s\S]*?\n\])\);/)[1];
const expected = {tr: 'Namaz Öğreniyorum', fr: "J’apprends la prière", en: 'Learning to Pray', de: 'Beten lernen'};
for (const language of ['tr', 'fr', 'en', 'de', 'es', '__proto__', '']) {
  const nodes = new Map();
  const document = {
    documentElement: {},
    getElementById(id) {
      if (!nodes.has(id)) nodes.set(id, {textContent: '', attrs: {}, options: [{}, {}, {}], getContext() {return null;}, setAttribute(k, v) {this.attrs[k] = v;}});
      return nodes.get(id);
    },
  };
  const ctx = vm.createContext({document, location: {search: `?app=1&lang=${language}`}, URLSearchParams});
  vm.runInContext(localization[1], ctx);
  const lang = Object.hasOwn(expected, language) ? language : 'tr';
  assert.equal(document.documentElement.lang, lang);
  assert.equal(document.getElementById('brandTitle').textContent, expected[lang]);
  assert.equal(document.title, expected[lang] + ' 3D');
  const baseline = JSON.parse(vm.runInContext(`JSON.stringify(${canonical})`, ctx));
  const steps = JSON.parse(vm.runInContext(`JSON.stringify(localizeSteps(${canonical}))`, ctx));
  assert.equal(steps.length, 10);
  for (const [i, step] of steps.entries()) {
    for (const field of ['t', 'info', 'src']) assert.ok(step[field].trim(), `${language} ${i} ${field}`);
    assert.deepEqual(step.rec.map(r => r[0]), baseline[i].rec.map(r => r[0]), 'Okuma sırası ve zamanı sabit kalmalı');
    if (i > 0 || lang === 'tr') assert.deepEqual(step.rec, baseline[i].rec, 'Arapça okunuşlar değişmemeli');
    if (lang !== 'tr') {
      assert.notEqual(step.t, baseline[i].t, `${lang} ${i} başlık çevrilmeli`);
      assert.notEqual(step.info, baseline[i].info, `${lang} ${i} açıklama çevrilmeli`);
    }
  }
  if (lang !== 'tr') assert.notEqual(steps[0].rec[0][1], baseline[0].rec[0][1], 'Niyet seçili dilde olmalı');
  const labels = ['seek', 'bPrev', 'bPlay', 'bNext', 'bRestart', 'bInfo', 'bCam', 'sSpeed', 'sQ', 'bRec'];
  for (const id of labels) assert.ok(document.getElementById(id).attrs['aria-label'], `${language}: ${id}`);
  const data = JSON.parse(vm.runInContext('JSON.stringify(LESSON)', ctx));
  const turkish = JSON.parse(vm.runInContext('JSON.stringify(LESSON_LANGUAGES.tr)', ctx));
  for (const key of ['loading', 'webgl', 'engine', 'freeCamera', 'cinematicCamera', 'noRecording', 'videoReady', 'download', 'recording', 'source', 'rakat']) {
    assert.ok(data[key].trim(), `${language}: ${key}`);
    if (lang !== 'tr') assert.notEqual(data[key], turkish[key], `${language}: ${key} çevrilmeli`);
  }
  // Gerçek oynatıcı WebGL olmayan cihazda da seçili dilde açıklama göstermeli.
  const player = [...html.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)].find(script => script[2].includes('const STEPS='));
  assert.throws(() => vm.runInContext(player[2], ctx), error => error.message === 'webgl2');
  assert.ok(document.getElementById('load').innerHTML.includes(data.webgl));
  if (lang !== 'tr') {
    data.labels.forEach((label, i) => assert.notEqual(label, turkish.labels[i]));
    data.quality.forEach((label, i) => assert.notEqual(label, turkish.quality[i]));
  }
}
console.log('3D localization: 4 languages, unsupported/malformed fallbacks, 10 steps, recitations and accessible controls passed.');
