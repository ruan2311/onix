// Monta o Onix GameHub para Windows (x64) sem precisar de Windows nem Wine, como o Amethyst:
// baixa o Electron oficial, troca o nome e o ícone do .exe e põe o app dentro.
// Uso: node gamehub/pc/build-win.js     (antes: bash gamehub/pc/cores.sh, para os núcleos)
// Sai em gamehub/pc/dist/Onix-GameHub/ e gamehub/pc/dist/Onix-GameHub-win64.zip.
'use strict';
const path = require('path');
const fs = require('fs');
const { execFileSync } = require('child_process');
const { downloadArtifact } = require('@electron/get');
const ResEdit = require('resedit');
const PELibrary = require('pe-library');

const root = __dirname;
const pkg = require(path.join(root, 'package.json'));
const electronVersion = require(path.join(root, 'node_modules', 'electron', 'package.json')).version;
const dist = path.join(root, 'dist');
const out = path.join(dist, 'Onix-GameHub');
const EXE = 'Onix GameHub.exe';

function copyDir(src, dst, skip) {
  fs.mkdirSync(dst, { recursive: true });
  for (const e of fs.readdirSync(src, { withFileTypes: true })) {
    if (skip && skip(e.name)) continue;
    const s = path.join(src, e.name), d = path.join(dst, e.name);
    if (e.isDirectory()) copyDir(s, d, skip); else fs.copyFileSync(s, d);
  }
}

(async () => {
  const cores = path.join(root, 'build', 'cores');
  for (const c of ['fceumm', 'snes9x', 'mgba', 'gpgx']) {
    if (!fs.existsSync(path.join(cores, c + '.wasm'))) throw new Error('Falta o núcleo ' + c + ': rode bash gamehub/pc/cores.sh');
  }
  console.log(`Onix GameHub ${pkg.version} · Electron ${electronVersion} → ${out}`);
  const zip = await downloadArtifact({ version: electronVersion, platform: 'win32', arch: 'x64', artifactName: 'electron' });

  const tmp = path.join(dist, '.stage');
  fs.rmSync(tmp, { recursive: true, force: true });
  fs.mkdirSync(tmp, { recursive: true });
  execFileSync('python3', ['-m', 'zipfile', '-e', zip, tmp]);
  fs.renameSync(path.join(tmp, 'electron.exe'), path.join(tmp, EXE));
  fs.rmSync(path.join(tmp, 'resources', 'default_app.asar'), { force: true });
  // Só os idiomas do GameHub (o Electron usa o inglês quando falta o do Windows).
  const keep = new Set(['pt-BR.pak', 'pt-PT.pak', 'es.pak', 'es-419.pak', 'en-US.pak']);
  for (const f of fs.readdirSync(path.join(tmp, 'locales'))) if (!keep.has(f)) fs.rmSync(path.join(tmp, 'locales', f));

  // O app: package.json enxuto, src/ e web/ do PC, as telas da TV (assets) e os núcleos.
  const app = path.join(tmp, 'resources', 'app');
  copyDir(path.join(root, 'src'), path.join(app, 'src'));
  copyDir(path.join(root, 'web'), path.join(app, 'web'));
  copyDir(path.join(root, '..', 'assets'), path.join(app, 'assets'), n => n === 'upload.html');
  copyDir(cores, path.join(app, 'cores'));
  fs.copyFileSync(path.join(root, 'icon.png'), path.join(app, 'icon.png'));
  const { devDependencies, scripts, ...runtimePkg } = pkg;
  fs.writeFileSync(path.join(app, 'package.json'), JSON.stringify(runtimePkg, null, 2));
  fs.writeFileSync(path.join(tmp, 'LEIA-ME.txt'), fs.readFileSync(path.join(root, 'LEIA-ME.txt'), 'utf8').replace('@VERSION@', pkg.version).replace(/\n/g, '\r\n'));

  // Ícone e informações do .exe (o que aparece em Propriedades → Detalhes).
  const exePath = path.join(tmp, EXE);
  const exe = PELibrary.NtExecutable.from(fs.readFileSync(exePath), { ignoreCert: true });
  const res = PELibrary.NtExecutableResource.from(exe);
  const iconFile = ResEdit.Data.IconFile.from(fs.readFileSync(path.join(root, 'icon.ico')));
  const groups = ResEdit.Resource.IconGroupEntry.fromEntries(res.entries);
  const groupId = groups.length ? groups[0].id : 1;
  const lang = groups.length ? groups[0].lang : 1033;
  ResEdit.Resource.IconGroupEntry.replaceIconsForResource(res.entries, groupId, lang, iconFile.icons.map(i => i.data));
  const [vi] = ResEdit.Resource.VersionInfo.fromEntries(res.entries);
  const [a, b, c] = pkg.version.split('.').map(Number);
  vi.setFileVersion(a, b, c, 0, 1033);
  vi.setProductVersion(a, b, c, 0, 1033);
  vi.setStringValues({ lang: 1033, codepage: 1200 }, {
    FileDescription: 'Onix GameHub', ProductName: 'Onix GameHub', CompanyName: 'Onix',
    OriginalFilename: EXE, InternalName: 'Onix GameHub', LegalCopyright: 'GPL-3.0',
    FileVersion: pkg.version, ProductVersion: pkg.version,
  });
  vi.outputToResourceEntries(res.entries);
  res.outputResource(exe);
  fs.writeFileSync(exePath, Buffer.from(exe.generate()));

  fs.rmSync(out, { recursive: true, force: true });
  copyDir(tmp, out);
  fs.rmSync(tmp, { recursive: true, force: true });

  // .zip para a release: a pasta Onix-GameHub dentro.
  const zipOut = path.join(dist, 'Onix-GameHub-win64.zip');
  fs.rmSync(zipOut, { force: true });
  execFileSync('python3', ['-c', `
import os, sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for base, _, files in os.walk(src):
        for f in files:
            p = os.path.join(base, f)
            z.write(p, os.path.relpath(p, os.path.dirname(src)))
`, out, zipOut]);
  console.log('Pronto: ' + path.join(out, EXE));
  console.log('Zip: ' + zipOut + ' (' + (fs.statSync(zipOut).size / 1048576).toFixed(1) + ' MB)');
})().catch(e => { console.error(e); process.exit(1); });
