# Ícone do motor dos consoles do RetroArch (CobaltMotor.exe): um alienzinho em pixel art com o anel e as
# cores do Cobalt GameHub, na grade 32x32 do icon.png. Uso: python3 tools/motor-icon.py motor  → motor.png e motor.ico
import zlib, struct, math, sys
out = sys.argv[1] if len(sys.argv) > 1 else "motor"
N = 32
C = {
 'b0': '2c1866', 'b1': '1e1150', 'b2': '120a2e', 'b3': '07040f', 'o': '0a051a',
 'L': 'd6c8ff', 'M': '8b6cf0', 'S': '5438c4', 'D': '2d1a78', 'K': '160c38',
 'c': '3de1ff', 'C': '2a80a1', 'w': 'ffffff', 'p': 'ff3d9a', 'P': '942669',
 'r1': 'ff3d9a', 'r2': 'd85eae', 'r3': 'b17fc2', 'r4': '8b9fd7', 'r5': '64c0eb', 'r6': '3de1ff',
}
rgb = lambda h: tuple(int(h[i:i+2], 16) for i in (0, 2, 4))
img = [[None] * N for _ in range(N)]
# Fundo: o octógono do ícone do GameHub, com as faixas de cor do canto de cima para baixo.
for y in range(N):
    for x in range(N):
        cut = 4
        if x + y < cut or (N - 1 - x) + y < cut or x + (N - 1 - y) < cut or (N - 1 - x) + (N - 1 - y) < cut: continue
        d = math.hypot((x - 8) / 1.35, y - 3)
        img[y][x] = C['b0'] if d < 7.5 else C['b1'] if d < 13.5 else C['b2'] if d < 19.5 else C['b3']
def star(x, y, hi, lo, big=True):
    img[y][x] = hi
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        img[y + dy][x + dx] = lo
# Estrelinhas nas mesmas cores e cantos do ícone do GameHub.
star(5, 6, 'ff5a6e', 'ac3e58'); star(26, 5, 'a78bfa', '735eb3'); star(4, 26, '38bdf8', '2b7eb1'); star(25, 28, 'fbbf24', 'a98028')
img[3][17] = 'c8beff'; img[28][10] = 'c8beff'; img[21][28] = 'ffffff'; img[20][27] = '3da0c8'; img[22][28] = 'd2f5ff'
alien = [
 "....pp............pp....",
 "....pP............Pp....",
 ".....oo..........oo.....",
 "......oo........oo......",
 "....oooooooooooooooo....",
 "...oLLLLMMMMMMMMMMMSo...",
 "..oLLLMMMMMMMMMMMMMMSo..",
 ".oLLMMMMMMMMMMMMMMMMMSo.",
 ".oLMMwccMMMMMMMMwccMMSo.",
 ".oLMMcccMMMMMMMMcccMMSo.",
 ".oMMMCCCMMMMMMMMCCCMSSo.",
 "ooMMMMMMMMMKKMMMMMMMSSoo",
 "oSoMMMMMMMMMMMMMMMMSSoSo",
 "oSooSMMMMMMMMMMMMMSSooSo",
 "oDo.ooSSSSSSSSSSSSoo.oDo",
 "oSo..oSo..oSSo..oSo..oSo",
 "oDo..oDo..oDDo..oDo..oDo",
 "oo...oo...oooo...oo...oo",
]
assert all(len(r) == 24 for r in alien), [len(r) for r in alien]
ax, ay = 4, 7
RING = True
ringc = ['ff3d9a', 'd85eae', 'b17fc2', '8b9fd7', '64c0eb', '3de1ff']
def ring(front):
    pts = set()
    for k in range(720):
        t = k / 720 * 2 * math.pi
        x0, y0 = 14.6 * math.cos(t), 3.6 * math.sin(t)
        a = -0.2
        x = 15.5 + x0 * math.cos(a) - y0 * math.sin(a); y = 16.8 + x0 * math.sin(a) + y0 * math.cos(a)
        if (math.sin(t) > 0) == front: pts.add((round(x), round(y))); pts.add((round(x), round(y) + 1))
    for x, y in pts:
        if 0 <= x < N and 0 <= y < N and img[y][x]:
            img[y][x] = ringc[min(5, max(0, (x - 1) * 6 // 30))]
if RING: ring(False)
for j, row in enumerate(alien):
    for i, ch in enumerate(row):
        if ch != '.': img[ay + j][ax + i] = C[ch]
if RING: ring(True)
def png(scale):
    W = N * scale
    raw = bytearray()
    for y in range(W):
        raw.append(0)
        for x in range(W):
            h = img[y // scale][x // scale]
            raw += bytes(rgb(h) + (255,)) if h else b'\0\0\0\0'
    chunk = lambda t, d: struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', W, W, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')
open(out + '.png', 'wb').write(png(8))
# .ico com 256, 128, 64 e 32 (em pixel art, só múltiplos da grade ficam nítidos).
imgs = [(s, png(s // N)) for s in (256, 128, 64, 32)]
head = struct.pack('<HHH', 0, 1, len(imgs)); off = 6 + 16 * len(imgs); dirs = b''; body = b''
for s, d in imgs:
    dirs += struct.pack('<BBBBHHII', s % 256, s % 256, 0, 0, 1, 32, len(d), off + len(body)); body += d
open(out + '.ico', 'wb').write(head + dirs + body)
