"""Gera icon.png (192x192) e banner.png (320x180) do Onix GameHub no traço do Onix: fundo preto
e o controle em linha branca fina (a mesma grade 48x48 e o mesmo traço do Saturno do Onix).
Uso (só no PC, com o Playwright): python3 gamehub/tools/art.py gamehub/res
"""
import sys
from playwright.sync_api import sync_playwright

MARK = ('<svg viewBox="0 0 48 48" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">'
        '<path d="M16 14.5h16a9.5 9.5 0 0 1 9.2 7.1l2.3 8.9a5 5 0 0 1-8.9 4.2L31 30H17l-3.6 4.7a5 5 0 0 1-8.9-4.2l2.3-8.9A9.5 9.5 0 0 1 16 14.5z"/>'
        '<path d="M15 19.5v6M12 22.5h6"/><circle cx="32.5" cy="20.6" r="1.25" fill="#fff" stroke="none"/>'
        '<circle cx="35.9" cy="24" r="1.25" fill="#fff" stroke="none"/></svg>')
FONT = 'font-family:Roboto,"Google Sans","DejaVu Sans",sans-serif'
ICON = f'<body style="margin:0;background:transparent"><div style="width:192px;height:192px;border-radius:44px;background:#0a0a0b;display:grid;place-items:center">{MARK.replace("<svg", "<svg width=138 height=138")}</div></body>'
BANNER = (f'<body style="margin:0;background:#0a0a0b;width:320px;height:180px;display:flex;align-items:center;justify-content:center;gap:16px;{FONT}">'
          f'{MARK.replace("<svg", "<svg width=64 height=64")}'
          '<div style="display:flex;flex-direction:column;gap:5px;color:#fff">'
          '<span style="font-size:23px;font-weight:300;letter-spacing:.32em">ONIX</span>'
          '<span style="font-size:15px;font-weight:400;letter-spacing:.3em">GAMEHUB</span>'
          '<span style="font-size:10px;letter-spacing:.06em;color:#8a8b93">By Ruan</span></div></body>')

res = sys.argv[1]
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={'width': 192, 'height': 192})
    pg.set_content(ICON)
    pg.screenshot(path=f'{res}/drawable-xxxhdpi/icon.png', omit_background=True)
    pg = b.new_page(viewport={'width': 320, 'height': 180})
    pg.set_content(BANNER)
    pg.screenshot(path=f'{res}/drawable-xhdpi/banner.png')
    b.close()
