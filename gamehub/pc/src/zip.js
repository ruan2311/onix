// Leitor de .zip só para tirar uma ROM de dentro (o Node não traz um). Lê o diretório central
// do fim do arquivo, aceita "stored" e "deflate" e recusa nomes com "..". Sem Zip64: ROMs dos
// 4 consoles têm no máximo 33 MB.
'use strict';
const zlib = require('zlib');

function entries(buf) {
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 22 - 65535); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) return [];
  const count = buf.readUInt16LE(eocd + 10);
  let pos = buf.readUInt32LE(eocd + 16);
  const out = [];
  for (let n = 0; n < count && pos + 46 <= buf.length; n++) {
    if (buf.readUInt32LE(pos) !== 0x02014b50) break;
    const method = buf.readUInt16LE(pos + 10);
    const csize = buf.readUInt32LE(pos + 20), usize = buf.readUInt32LE(pos + 24);
    const nlen = buf.readUInt16LE(pos + 28), xlen = buf.readUInt16LE(pos + 30), clen = buf.readUInt16LE(pos + 32);
    const local = buf.readUInt32LE(pos + 42);
    const name = buf.toString('utf8', pos + 46, pos + 46 + nlen);
    out.push({ name, method, csize, usize, local });
    pos += 46 + nlen + xlen + clen;
  }
  return out;
}

function extract(buf, e) {
  if (e.local + 30 > buf.length || buf.readUInt32LE(e.local) !== 0x04034b50) return null;
  const start = e.local + 30 + buf.readUInt16LE(e.local + 26) + buf.readUInt16LE(e.local + 28);
  const raw = buf.subarray(start, start + e.csize);
  if (e.method === 0) return Buffer.from(raw);
  if (e.method === 8) return zlib.inflateRawSync(raw, { maxOutputLength: e.usize + 1 });
  return null;
}

/** A maior entrada aceita por limit(nome) (que devolve o tamanho máximo, ou 0 para recusar). */
function largest(buf, limit) {
  let best = null;
  for (const e of entries(buf)) {
    if (e.name.endsWith('/') || e.name.includes('..')) continue;
    const max = limit(e.name);
    if (!max || e.usize > max) continue;
    if (!best || e.usize > best.usize) best = e;
  }
  if (!best) return null;
  const data = extract(buf, best);
  return data && data.length === best.usize ? { name: best.name, data } : null;
}

module.exports = { entries, largest };
