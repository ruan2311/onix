"""Monta o index.html das TVs sem Android (Samsung e LG) a partir do index.html do Android.

    python3 tv/page.py RAIZ SAIDA VERSÃO ADAPTADOR.js

Põe a ponte (o adaptador da TV + tv/bridge.js, numa função só) antes do script da página, embute as
indicações iniciais e libera na CSP só os hosts que a ponte usa e os ícones (file:) dos apps da TV.
"""
import json, re, sys

root, out, version, platform_js = sys.argv[1:]
page = open(f'{root}/index.html', encoding='utf-8').read()
core = open(f'{root}/tv/bridge.js', encoding='utf-8').read()
platform = open(platform_js, encoding='utf-8').read().replace('@VERSION@', version)
seed = json.load(open(f'{root}/android/assets/picks-seed.json', encoding='utf-8'))

def sub(pattern, repl, text, count=1):
    new, n = re.subn(pattern, repl, text, count=count)
    if n == 0: sys.exit(f'page.py: não achei {pattern!r} no index.html')
    return new

page = re.sub(r"^const APP_VERSION = '[^']*';", lambda m: f"const APP_VERSION = '{version}';", page, count=1, flags=re.M)
# CSP: a página continua sem rede própria; liberam-se só os hosts que a ponte usa e os ícones (file:) dos apps da TV.
hosts = ' '.join(['https://apis.justwatch.com', 'https://images.justwatch.com', 'https://itunes.apple.com',
                  'https://*.mzstatic.com', 'https://www.adrenaline.com.br', 'https://www.eurogamer.pt',
                  'https://assetsio.gnwcdn.com', 'https://api.github.com', 'https://connectivitycheck.gstatic.com',
                  'https://api.open-meteo.com', 'https://geocoding-api.open-meteo.com'])
page = sub(r"img-src data:;", "img-src data: file:;", page)
page = sub(r"connect-src 'none';", f"connect-src {hosts};", page)
# O CSS para navegadores antigos (sem inset, aspect-ratio, clamp e gap no flexbox) já vem no index.html.
core = core.replace('/*ONIX_SEED*/null', json.dumps(seed, ensure_ascii=False, separators=(',', ':')), 1)
bridge = "(function () {\n'use strict';\n" + platform + '\n' + core + '})();'
# A ponte entra antes do script da página (que lê window.AndroidBridge ao abrir).
page = sub(r'<script>', lambda m: '<script>\n' + bridge.replace('</script', '<\\/script') + '\n</script>\n<script>', page)
open(f'{out}/index.html', 'w', encoding='utf-8').write(page)
