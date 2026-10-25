#!/usr/bin/env python3
"""Gera assets/picks-seed.json: indicações embutidas no APK para a primeira abertura sem internet.

Mesmas fontes e formato do WeeklyPicks.java (JustWatch/Netflix BR, completando com a iTunes BR),
mas pega simplesmente os primeiros do ranking. Rodar manualmente (precisa de internet):
    python3 android/tools/gen_seed.py
O build.sh só copia o arquivo; não acessa a rede.
"""
import base64, json, os, re, subprocess, sys, tempfile, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'assets', 'picks-seed.json')
COUNT = 8
MAX_POSTER = 300 * 1024
UA = {'User-Agent': 'Mozilla/5.0 (Linux; Android 11) Onix'}
JW_QUERY = ('query($c:Country!,$l:Language!,$f:TitleFilter){popularTitles(country:$c,first:40,filter:$f,sortBy:POPULAR)'
            '{edges{node{objectType content(country:$c,language:$l){title originalReleaseYear shortDescription posterUrl genres{shortName}}'
            ' offers(country:$c,platform:WEB,filter:{packages:["nfx"]}){standardWebURL}}}}}')
JW_POSTER = re.compile(r'^/poster/\d{1,12}/\{profile\}/[a-z0-9-]{1,120}\.\{format\}$')
NETFLIX_URL = re.compile(r'^https://www\.netflix\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?title/(\d{1,12})/?$')
POSTER = re.compile(r'^https://[a-z0-9-]+\.mzstatic\.com/image/thumb/[^?#\s"]+/\d+x\d+bb\.(png|jpg)$')
GENRES = dict(act='Ação', ani='Animação', cmy='Comédia', crm='Crime', doc='Documentário', drm='Drama',
              fnt='Fantasia', hst='História', hrr='Terror', fml='Família', msc='Música', trl='Suspense',
              rma='Romance', scf='Ficção científica', spt='Esporte', war='Guerra', wsn='Faroeste', rly='Reality')


def fetch(url, data=None, limit=2 * 1024 * 1024):
    headers = dict(UA)
    if data is not None:
        headers['Content-Type'] = 'application/json'
    with urllib.request.urlopen(urllib.request.Request(url, data, headers), timeout=20) as r:
        body = r.read(limit + 1)
    if len(body) > limit:
        raise ValueError('resposta grande demais: ' + url)
    return body


def clean_title(t):
    t = re.sub(r'\s*\[[^\]]*\]', '', t)
    t = re.sub(r'(?i)\s*"vers[aã]o[^"]*"', '', t)
    t = re.sub(r'(?i)\s*\(vers[aã]o[^)]*\)', '', t)
    return t.strip()


def franchise(title):
    head = re.split(r':| - | – ', title)[0].lower()
    words = [w for w in head.split() if w not in ('o', 'a', 'os', 'as', 'um', 'uma', 'the')]
    return ' '.join(words[:2])


def trim(s, n):
    if len(s) <= n:
        return s
    cut = s.rfind(' ', 0, n - 1)
    return s[:cut if cut > n // 2 else n - 1] + '…'


def poster(it, jpg, tmpdir):
    it['poster'] = 'data:image/jpeg;base64,' + base64.b64encode(jpg).decode()
    path = os.path.join(tmpdir, '%d.jpg' % len(os.listdir(tmpdir)))
    with open(path, 'wb') as f:
        f.write(jpg)
    it['_file'] = path


def netflix(out, seen, tmpdir):
    body = json.dumps({'query': JW_QUERY, 'variables': {'c': 'BR', 'l': 'pt',
                       'f': {'packages': ['nfx'], 'objectTypes': ['MOVIE', 'SHOW']}}}).encode()
    edges = json.loads(fetch('https://apis.justwatch.com/graphql', body))['data']['popularTitles']['edges']
    for e in edges:
        if len(out) >= COUNT:
            return
        try:
            node, c = e['node'], e['node']['content']
            title = clean_title(c['title'])
            ids = [m.group(1) for o in (node.get('offers') or [])
                   for m in [NETFLIX_URL.match(o.get('standardWebURL') or '')] if m]
            path = c.get('posterUrl') or ''
            if not title or len(title) > 120 or not ids or not JW_POSTER.match(path) or franchise(title) in seen:
                continue
            jpg = fetch('https://images.justwatch.com' + path.replace('{profile}', 's332').replace('{format}', 'jpg'), limit=MAX_POSTER)
            if jpg[:2] != b'\xff\xd8':
                continue
            seen.add(franchise(title))
            it = {'title': title, 'provider': 'netflix', 'netflixId': ids[0],
                  'kind': 'Série' if node.get('objectType') == 'SHOW' else 'Filme'}
            g = GENRES.get(((c.get('genres') or [{}])[0]).get('shortName'))
            if g:
                it['genre'] = g
            if 1900 < (c.get('originalReleaseYear') or 0) < 3000:
                it['year'] = str(c['originalReleaseYear'])
            it['summary'] = trim((c.get('shortDescription') or '').strip(), 260)
            poster(it, jpg, tmpdir)
            out.append(it)
        except Exception as ex:
            print('  pulando item JustWatch:', ex, file=sys.stderr)


def itunes(out, seen, tmpdir):
    entries = json.loads(fetch('https://itunes.apple.com/br/rss/topmovies/limit=50/json'))['feed']['entry']
    for e in entries:
        if len(out) >= COUNT:
            return
        try:
            title = clean_title(e['im:name']['label'])
            src = e['im:image'][-1]['label']
            if not title or franchise(title) in seen or not POSTER.match(src):
                continue
            jpg = fetch(re.sub(r'/\d+x\d+bb\.(png|jpg)$', '/300x450bb.jpg', src), limit=MAX_POSTER)
            if jpg[:2] != b'\xff\xd8':
                continue
            seen.add(franchise(title))
            it = {'title': title, 'genre': e['category']['attributes'].get('label', '')}
            date = e['im:releaseDate'].get('label', '')
            if len(date) >= 4:
                it['year'] = date[:4]
            it['summary'] = trim(e['summary'].get('label', ''), 260)
            poster(it, jpg, tmpdir)
            out.append(it)
        except Exception as ex:
            print('  pulando item iTunes:', ex, file=sys.stderr)


def main():
    out, seen = [], set()
    with tempfile.TemporaryDirectory() as tmpdir:
        try:
            netflix(out, seen, tmpdir)
        except Exception as ex:
            print('JustWatch indisponível:', ex, file=sys.stderr)
        if len(out) < COUNT:
            itunes(out, seen, tmpdir)
        if not out:
            sys.exit('nenhuma indicação baixada; o seed anterior foi mantido')
        # Cor média de cada capa (tom do destaque), calculada pelo AWT como o app faz com o BitmapFactory.
        colors = subprocess.run(['java', '-Djava.awt.headless=true', os.path.join(HERE, 'AvgColor.java')]
                                + [it['_file'] for it in out], capture_output=True, text=True, check=True).stdout.split()
        for it, col in zip(out, colors):
            del it['_file']
            if re.fullmatch(r'#[0-9a-f]{6}', col):
                it['color'] = col
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump({'v': 2, 'items': out}, f, ensure_ascii=False, separators=(',', ':'))
    print('%d indicações, %d KB -> %s' % (len(out), os.path.getsize(OUT) // 1024, os.path.normpath(OUT)))
    for it in out:
        print('  -', it['title'])


if __name__ == '__main__':
    main()
