"""Gera icon.png (192x192) e banner.png (320x180) do Onix GameHub: o Saturno do Onix em pixel art,
numa grade de 32x32 ampliada sem suavizar. Planeta índigo com faixas e sombreado em degraus
(pontilhado à moda dos 16 bits), anel em degradê rosa → ciano, lua branca, estrelinhas nas cores
dos quatro consoles (NES, SNES, GBA e Mega Drive) e o fundo índigo das telas do GameHub.
Também escreve o Saturno em SVG (um retângulo por trecho de pixels da mesma cor) nos lugares que
mostram a marca: MARK em gamehub/assets/common.js, a página de envio (upload.html) e HUB_MARK no
index.html do Onix.
Uso: python3 gamehub/tools/art.py gamehub/res   (só precisa do Pillow)
"""
import math
import os
import re
import sys

from PIL import Image

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
BG = [(7, 4, 15), (18, 10, 46), (30, 17, 80), (44, 24, 102)]
PLANET = [(22, 12, 56), (45, 26, 120), (84, 56, 196), (139, 108, 240), (214, 200, 255)]
PINK, CYAN = (255, 61, 154), (61, 225, 255)
CONSOLES = [(255, 90, 110), (167, 139, 250), (56, 189, 248), (251, 191, 36)]


def hex2(c):
    return '#%02x%02x%02x' % c


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def dither(levels, v, x, y):
    """v em 0..1 vira um dos tons da lista, com pontilhado ordenado entre dois vizinhos."""
    v = max(0.0, min(1.0, v)) * (len(levels) - 1)
    i = int(v)
    if i >= len(levels) - 1:
        return levels[-1]
    return levels[i + 1] if (v - i) * 16 > BAYER[y % 4][x % 4] + .5 else levels[i]


def saturn(px, ox, oy):
    """Desenha o Saturno (32x32) em px com o canto em (ox, oy)."""
    cx, cy, r = 15.5, 16.5, 8.6
    tilt = math.radians(-16)
    a, b = 15.4, 5.6
    drawn = set()
    for y in range(32):
        for x in range(32):
            dx, dy = x + .5 - cx, y + .5 - cy
            u = dx * math.cos(tilt) + dy * math.sin(tilt)
            v = -dx * math.sin(tilt) + dy * math.cos(tilt)
            e = math.hypot(u / a, v / b)
            ring = .7 <= e <= 1.0
            color = None
            if math.hypot(dx, dy) <= r and not (ring and v > 0):
                nx, ny = dx / r, dy / r
                nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
                light = max(0.0, -.55 * nx - .6 * ny + .58 * nz)
                light += .1 if int((v + r) / 3) % 2 else 0  # as faixas do planeta
                # Sombra do anel sobre o planeta, logo acima da parte da frente.
                if 0 < -v < 1.6 and abs(u) < r:
                    light -= .25
                color = dither(PLANET, light, x, y)
            elif ring:
                t = (u + a) / (2 * a)
                t = round(t * 5) / 5
                color = mix(PINK, CYAN, t)
                if v < 0:
                    color = mix(color, (18, 10, 46), .45)  # a parte de trás, mais apagada
            if color:
                px[ox + x, oy + y] = color + (255,)
                drawn.add((x, y))
    # Contorno escuro de um pixel, como nos sprites dos 16 bits.
    for x, y in {(x + dx, y + dy) for x, y in drawn for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))} - drawn:
        if 0 <= x < 32 and 0 <= y < 32:
            px[ox + x, oy + y] = (10, 5, 26, 255)
    # Lua com um brilho ciano em volta.
    for (x, y), c in {(27, 20): (255, 255, 255), (28, 20): (255, 255, 255), (27, 21): (255, 255, 255), (28, 21): (210, 245, 255),
                      (26, 20): (61, 160, 200), (29, 21): (61, 160, 200), (27, 19): (61, 160, 200), (28, 22): (61, 160, 200)}.items():
        px[ox + x, oy + y] = c + (255,)


def star(px, x, y, c, big):
    px[x, y] = c + (255,)
    if big:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            px[x + dx, y + dy] = mix(c, BG[1], .35) + (255,)


def background(img, w, h, corner):
    px = img.load()
    for y in range(h):
        for x in range(w):
            if corner:
                qx, qy = min(x, w - 1 - x), min(y, h - 1 - y)
                if qx < corner and qy < corner and math.hypot(corner - qx - .5, corner - qy - .5) > corner:
                    continue
            d = math.hypot((x - w * .3) / w, (y - h * .2) / h)
            px[x, y] = BG[max(0, min(3, int((1.05 - d * 1.25) * 4)))] + (255,)
    return px


def icon():
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = background(img, 32, 32, 7)
    for (x, y), c in zip([(5, 6), (26, 5), (4, 26), (25, 28)], CONSOLES):
        star(px, x, y, c, True)
    for x, y in ((17, 3), (10, 28), (29, 13)):
        px[x, y] = (200, 190, 255, 255)
    saturn(px, 0, 0)
    return img.resize((192, 192), Image.NEAREST)


# Fonte de pixel 5x7, só com as letras do banner.
FONT = {
    'O': ['01110', '10001', '10001', '10001', '10001', '10001', '01110'],
    'N': ['10001', '11001', '10101', '10011', '10001', '10001', '10001'],
    'I': ['11111', '00100', '00100', '00100', '00100', '00100', '11111'],
    'X': ['10001', '01010', '00100', '00100', '00100', '01010', '10001'],
    'G': ['01111', '10000', '10000', '10111', '10001', '10001', '01111'],
    'A': ['01110', '10001', '10001', '11111', '10001', '10001', '10001'],
    'M': ['10001', '11011', '10101', '10101', '10001', '10001', '10001'],
    'E': ['11111', '10000', '10000', '11110', '10000', '10000', '11111'],
    'H': ['10001', '10001', '10001', '11111', '10001', '10001', '10001'],
    'U': ['10001', '10001', '10001', '10001', '10001', '10001', '01110'],
    'B': ['11110', '10001', '10001', '11110', '10001', '10001', '11110'],
    'Y': ['10001', '10001', '01010', '00100', '00100', '00100', '00100'],
    'R': ['11110', '10001', '10001', '11110', '10100', '10010', '10001'],
    ' ': ['000'] * 7,
}


def text(px, s, x, y, gap, color):
    """Escreve s; color(i, n) dá a cor da coluna i de n (para o degradê)."""
    n = sum(len(FONT[ch][0]) + gap for ch in s) - gap
    i = 0
    for ch in s:
        g = FONT[ch]
        for gy, row in enumerate(g):
            for gx, bit in enumerate(row):
                if bit == '1':
                    px[x + i + gx, y + gy] = color(i + gx, n) + (255,)
        i += len(g[0]) + gap


def banner():
    img = Image.new('RGBA', (160, 90))
    px = background(img, 160, 90, 0)
    for (x, y), c in zip([(10, 8), (70, 10), (150, 14), (140, 80)], CONSOLES):
        star(px, x, y, c, True)
    for x, y in ((44, 4), (6, 80), (120, 6), (100, 84)):
        px[x, y] = (200, 190, 255, 255)
    # O Saturno em dobro (64x64) à esquerda.
    s = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    saturn(s.load(), 0, 0)
    img.alpha_composite(s.resize((64, 64), Image.NEAREST), (4, 13))
    text(px, 'ONIX', 74, 28, 2, lambda i, n: (241, 237, 255))
    text(px, 'GAMEHUB', 74, 41, 1, lambda i, n: mix(PINK, CYAN, round(i / (n - 1) * 6) / 6))
    text(px, 'BY RUAN', 74, 55, 1, lambda i, n: (169, 159, 208))
    return img.resize((320, 180), Image.NEAREST)


def mark_svg():
    """O Saturno sem fundo em SVG, com o mesmo pixel da grade 32x32."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    saturn(img.load(), 0, 0)
    px = img.load()
    runs = {}
    for y in range(32):
        x = 0
        while x < 32:
            c = px[x, y]
            n = 1
            while x + n < 32 and px[x + n, y] == c:
                n += 1
            if c[3]:
                runs.setdefault(hex2(c[:3]), []).append(f'M{x} {y}h{n}v1h-{n}z')
            x += n
    paths = ''.join(f'<path fill="{c}" d="{"".join(d)}"/>' for c, d in runs.items())
    return f'<svg viewBox="0 0 32 32" shape-rendering="crispEdges">{paths}</svg>'


def put_mark(path, pattern, mark):
    s = open(path, encoding='utf-8').read()
    s, n = re.subn(pattern, lambda m: m.group(1) + mark + m.group(2), s, count=1)
    assert n == 1, path
    open(path, 'w', encoding='utf-8').write(s)


res = sys.argv[1]
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
mark = mark_svg()
put_mark(f'{root}/gamehub/assets/common.js', r"(const MARK = ')<svg.*?</svg>(';)", mark)
put_mark(f'{root}/gamehub/assets/upload.html', r'(<div class="brand">)<svg.*?</svg>(<span>)', mark.replace('<svg ', '<svg width="40" height="40" '))
put_mark(f'{root}/index.html', r"(const HUB_MARK = ')<svg.*?</svg>(';)", mark)
icon().save(f'{res}/drawable-xxxhdpi/icon.png')
banner().convert('RGB').save(f'{res}/drawable-xhdpi/banner.png')
