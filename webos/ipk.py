"""Empacota o app da LG num .ipk sem o ares-package (o formato é um "ar" com dois tar.gz).

    python3 webos/ipk.py PASTA_DO_APP SAÍDA.ipk

A pasta precisa ter o appinfo.json. O pacote sai igual ao do ares-package: debian-binary,
control.tar.gz (o arquivo control) e data.tar.gz (usr/palm/applications/<id> e
usr/palm/packages/<id>/packageinfo.json). Instala pelo ares-install, pelo webOS Dev Manager ou pelo
Homebrew Channel.
"""
import gzip, io, json, os, sys, tarfile, time

app_dir, out = sys.argv[1:]
info = json.load(open(os.path.join(app_dir, 'appinfo.json'), encoding='utf-8'))
app_id, version = info['id'], info['version']
now = int(time.time())

def tar_gz(add):
    raw = io.BytesIO()
    with tarfile.open(fileobj=raw, mode='w', format=tarfile.GNU_FORMAT) as t:
        add(t)
    gz = io.BytesIO()
    with gzip.GzipFile(fileobj=gz, mode='wb', mtime=now) as g:
        g.write(raw.getvalue())
    return gz.getvalue()

def add_dir(t, name):
    ti = tarfile.TarInfo(name); ti.type = tarfile.DIRTYPE; ti.mode = 0o755; ti.mtime = now
    t.addfile(ti)

def add_bytes(t, name, data, mode=0o644):
    ti = tarfile.TarInfo(name); ti.size = len(data); ti.mode = mode; ti.mtime = now
    t.addfile(ti, io.BytesIO(data))

size = 0
def data(t):
    global size
    base = f'./usr/palm/applications/{app_id}'
    for d in ['./usr', './usr/palm', './usr/palm/applications', base]:
        add_dir(t, d)
    for dirpath, dirnames, filenames in os.walk(app_dir):
        dirnames.sort()
        rel = os.path.relpath(dirpath, app_dir)
        if rel != '.': add_dir(t, f'{base}/{rel}')
        for f in sorted(filenames):
            b = open(os.path.join(dirpath, f), 'rb').read()
            size += len(b)
            add_bytes(t, f'{base}/{f}' if rel == '.' else f'{base}/{rel}/{f}', b)
    pkg = json.dumps({'app': app_id, 'id': app_id, 'loc_name': info.get('title', app_id),
                      'package_format_version': 2, 'vendor': info.get('vendor', ''), 'version': version}, indent=2).encode()
    for d in ['./usr/palm/packages', f'./usr/palm/packages/{app_id}']:
        add_dir(t, d)
    add_bytes(t, f'./usr/palm/packages/{app_id}/packageinfo.json', pkg)

data_tgz = tar_gz(data)
control = (f'Package: {app_id}\nVersion: {version}\nSection: misc\nPriority: optional\nArchitecture: all\n'
           f'Installed-Size: {max(1, size // 1024)}\nMaintainer: {info.get("vendor", "")}\n'
           f'Description: {info.get("title", app_id)}\nwebOS-Package-Format-Version: 2\nwebOS-Packager-Version: 1.0.0\n').encode()
control_tgz = tar_gz(lambda t: (add_dir(t, '.'), add_bytes(t, './control', control)))

def ar_entry(name, b):
    head = f'{name:<16}{now:<12}{0:<6}{0:<6}{"100644":<8}{len(b):<10}`\n'.encode()
    return head + b + (b'\n' if len(b) % 2 else b'')

with open(out, 'wb') as f:
    f.write(b'!<arch>\n')
    f.write(ar_entry('debian-binary', b'2.0\n'))
    f.write(ar_entry('control.tar.gz', control_tgz))
    f.write(ar_entry('data.tar.gz', data_tgz))
