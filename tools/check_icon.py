#!/usr/bin/env python3
"""Uso: check_icon.py <res_dir_ou_extraido> <alvo.png> <saida.png> — compõe o adaptativo (bg) e mostra máscaras squircle/círculo ao lado do alvo."""
import sys, math
from PIL import Image, ImageChops, ImageDraw, ImageFont
R, TGT, OUT = sys.argv[1:4]
tgt = Image.open(TGT).convert('RGBA')
full = Image.open(R).convert('RGBA')  # camada de fundo xxxhdpi 432px
def mask(S, shape):
    Sx = S * 4; m = Image.new('L', (Sx, Sx), 0); d = ImageDraw.Draw(m)
    if shape == 'circle': d.ellipse([0, 0, Sx - 1, Sx - 1], fill=255)
    else:
        c = (Sx - 1) / 2; pts = []
        for i in range(720):
            t = 2 * math.pi * i / 720; ct, st = math.cos(t), math.sin(t)
            pts.append((c + c * math.copysign(abs(ct) ** (2 / 5), ct), c + c * math.copysign(abs(st) ** (2 / 5), st)))
        d.polygon(pts, fill=255)
    return m.resize((S, S), Image.LANCZOS)
def launcher(shape, S=512):
    o = int(432 * 18 / 108); vis = full.crop((o, o, 432 - o, 432 - o)).resize((S, S), Image.LANCZOS)
    vis.putalpha(ImageChops.multiply(vis.getchannel('A'), mask(S, shape))); return vis
font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 18)
sheet = Image.new('RGB', (512 * 3 + 80, 512 + 80), (244, 246, 250)); dr = ImageDraw.Draw(sheet)
for i, (l, im) in enumerate([('alvo (imagem do Igor)', tgt.resize((512, 512), Image.LANCZOS)), ('adaptativo: squircle', launcher('squircle')), ('adaptativo: circulo', launcher('circle'))]):
    x = 20 + i * 532; b = Image.new('RGBA', (512, 512), (244, 246, 250, 255)); b.alpha_composite(im); sheet.paste(b.convert('RGB'), (x, 20)); dr.text((x, 540), l, fill=(20, 20, 30), font=font)
sheet.save(OUT)
