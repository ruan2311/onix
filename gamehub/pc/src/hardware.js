// O PC da pessoa: placa de vídeo, processador e memória, para o GameHub escolher sozinho a resolução e
// o renderizador dos consoles do RetroArch. Três faixas: 0 = básico (vídeo integrado simples ou pouca
// memória), 1 = intermediário, 2 = forte (placa de vídeo dedicada atual).
'use strict';
const { app } = require('electron');
const os = require('os');

let info = null;

// Do mais forte para o mais fraco; o primeiro que bater decide a faixa da placa de vídeo.
const STRONG = /\bRTX\b|GTX\s*(16|20)\d\d|\bRX\s*([5-9]\d{3})\b|Arc\s*\(?TM\)?\s*A\d{3}|Arc\s*A\d{3}|Radeon\s*(Pro\s*W|VII)/i;
const MEDIUM = /\bGTX\b|\bMX\s*\d{3}|\bRX\s*\d{3}\b|Radeon\s*RX|Iris\s*\(?R?\)?\s*Xe|Iris\s*Xe|\bArc\b|Radeon\s*\(?TM\)?\s*(\d{3}M|Graphics|Vega)|Radeon\s*\d{3}M|Apple\s*M\d/i;
const gpuTier = name => STRONG.test(name) ? 2 : MEDIUM.test(name) ? 1 : 0;

/** O nome da placa de vídeo no texto do ANGLE: "ANGLE (NVIDIA, NVIDIA GeForce RTX 3050 ... Direct3D11 ...)". */
function angleName(s) {
  const m = /ANGLE \(([^,]+),\s*([^(]+?)(?:\s*\(0x[0-9a-f]+\))?\s*(?:Direct3D|D3D|OpenGL|Vulkan)/i.exec(s || '');
  return m ? m[2].trim() : '';
}

async function detect() {
  const gpus = [];
  try {
    const g = await app.getGPUInfo('complete');
    for (const d of (g && g.gpuDevice) || []) if (d.deviceString) gpus.push(d.deviceString);
    const angle = angleName(g && g.auxAttributes && g.auxAttributes.glRenderer);
    if (angle && !gpus.includes(angle)) gpus.push(angle);
  } catch (_) { }
  // Notebook com duas placas: vale a mais forte (é a que o Windows usa para jogos).
  const gpu = gpus.slice().sort((a, b) => gpuTier(b) - gpuTier(a))[0] || '';
  const threads = os.cpus().length, ramGB = Math.round(os.totalmem() / 1073741824);
  let tier = gpuTier(gpu);
  if (threads < 8 || ramGB < 8) tier = Math.min(tier, 1);
  if (threads < 4 || ramGB < 6) tier = 0;
  info = { gpu, cpu: ((os.cpus()[0] || {}).model || '').replace(/\s+/g, ' ').trim(), threads, ramGB, tier };
  return info;
}

/** O que já foi detectado (a detecção roda ao abrir o GameHub; até lá, faixa intermediária). */
function get() { return info || { gpu: '', cpu: '', threads: os.cpus().length, ramGB: Math.round(os.totalmem() / 1073741824), tier: 1 }; }

module.exports = { detect, get, gpuTier };
