// Prepara o motor dos consoles do RetroArch (N64, DS, PSP, GameCube, Wii e PS2) com a cara do GameHub:
// o retroarch.exe vira CobaltMotor.exe, com o ícone do motor (motor.ico, de tools/motor-icon.py) e as
// informações do GameHub. O nome da janela e o menu de dentro do jogo continuam sendo os do RetroArch: trocar
// o texto da janela dentro do .exe quebra a criação dela (o jogo fecha logo ao abrir).
// Uso: node gamehub/pc/build-motor.js <retroarch-win64.zip oficial> <pasta de saída>
// Sai em <pasta de saída>/retroarch-win64.zip, que vai na release motor-retroarch-N.
'use strict';
const path = require('path');
const fs = require('fs');
const { execFileSync } = require('child_process');
const ResEdit = require('resedit');
const PELibrary = require('pe-library');
const zip = require('./src/zip');

const [src, outDir] = process.argv.slice(2);
if (!src || !outDir) throw new Error('Uso: node build-motor.js <retroarch-win64.zip> <pasta de saída>');
const OLD_EXE = 'retroarch.exe', EXE = 'CobaltMotor.exe';

const buf = fs.readFileSync(src);
const stage = path.join(outDir, '.motor');
fs.rmSync(stage, { recursive: true, force: true });
for (const e of zip.entries(buf)) {
  if (e.name.endsWith('/')) continue;
  const data = zip.extract(buf, e);
  if (!data) throw new Error('não deu para abrir ' + e.name);
  const out = path.join(stage, e.name === OLD_EXE ? EXE : e.name);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, data);
}

const exePath = path.join(stage, EXE);
const bin = fs.readFileSync(exePath);
// O RetroArch vem com a tabela de símbolos de depuração, que o pe-library não aceita e o Windows não
// usa: zera a contagem dela antes de mexer nos recursos.
bin.writeUInt32LE(0, bin.readUInt32LE(0x3c) + 4 + 12);

// Ícone e informações do .exe (o que aparece em Propriedades → Detalhes e no Gerenciador de Tarefas).
const exe = PELibrary.NtExecutable.from(bin, { ignoreCert: true });
const res = PELibrary.NtExecutableResource.from(exe);
const iconFile = ResEdit.Data.IconFile.from(fs.readFileSync(path.join(__dirname, 'motor.ico')));
const groups = ResEdit.Resource.IconGroupEntry.fromEntries(res.entries);
const groupId = groups.length ? groups[0].id : 1;
const lang = groups.length ? groups[0].lang : 1033;
ResEdit.Resource.IconGroupEntry.replaceIconsForResource(res.entries, groupId, lang, iconFile.icons.map(i => i.data));
// O RetroArch não traz essas informações; sem elas o Windows mostraria só o nome do arquivo.
const vi = ResEdit.Resource.VersionInfo.fromEntries(res.entries)[0] || ResEdit.Resource.VersionInfo.createEmpty();
vi.setFileVersion(1, 22, 2, 0, 1033);
vi.setProductVersion(1, 22, 2, 0, 1033);
vi.setStringValues({ lang: 1033, codepage: 1200 }, {
  FileDescription: 'Cobalt GameHub', ProductName: 'Cobalt GameHub', CompanyName: 'Project Gems',
  OriginalFilename: EXE, InternalName: 'CobaltMotor', LegalCopyright: 'RetroArch: GPL-3.0',
  FileVersion: '1.22.2', ProductVersion: '1.22.2',
});
vi.outputToResourceEntries(res.entries);
res.outputResource(exe);
fs.writeFileSync(exePath, Buffer.from(exe.generate()));

// Um .zip só, do mesmo jeito que o download espera.
const dst = path.join(outDir, 'retroarch-win64.zip');
fs.rmSync(dst, { force: true });
execFileSync('python3', ['-c', `
import os, sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for base, _, files in os.walk(src):
        for f in sorted(files):
            p = os.path.join(base, f)
            z.write(p, os.path.relpath(p, src))
`, stage, dst]);
fs.rmSync(stage, { recursive: true, force: true });
console.log(`Pronto: ${dst} (${(fs.statSync(dst).size / 1048576).toFixed(1)} MB)`);
