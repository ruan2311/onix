"""Ícone do GameHub para PC (icon.png e icon.ico em gamehub/pc), a partir do ícone em pixel art
da TV (gamehub/res/drawable-xxxhdpi/icon.png, gerado pelo tools/art.py). Amplia sem suavizar,
para os pixels continuarem nítidos em todos os tamanhos do Windows.
Uso: python3 gamehub/pc/tools/icon.py   (só precisa do Pillow)
"""
import os

from PIL import Image

here = os.path.dirname(os.path.abspath(__file__))
pc = os.path.dirname(here)
src = Image.open(os.path.join(pc, '..', 'res', 'drawable-xxxhdpi', 'icon.png')).convert('RGBA')
big = src.resize((256, 256), Image.NEAREST)
big.save(os.path.join(pc, 'icon.png'))
big.save(os.path.join(pc, 'icon.ico'), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print('icon.png e icon.ico em', pc)
