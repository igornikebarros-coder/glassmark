#!/usr/bin/env python3
"""Compõe as camadas PNG do ícone adaptativo e compara com o preview. Uso: check_icon.py <res> <preview_512.png> <saida.png>"""
import sys, math
from PIL import Image, ImageChops, ImageDraw, ImageFont
R, PREV, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
new = Image.open(PREV).convert('RGBA')
bg = Image.open(R + '/drawable-nodpi/ic_link_bg.png').convert('RGBA'); fg = Image.open(R + '/drawable-nodpi/ic_link_fg.png').convert('RGBA')
full = Image.alpha_composite(bg, fg)
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
sheet = Image.new('RGB', (512 * 3 + 80, 512 + 80 + 140), (244, 246, 250)); dr = ImageDraw.Draw(sheet)
for i, (l, im) in enumerate([('preview 53811ab (alvo)', new), ('adaptativo PNG: squircle', launcher('squircle')), ('adaptativo PNG: circulo', launcher('circle'))]):
    x = 20 + i * 532; b = Image.new('RGBA', (512, 512), (244, 246, 250, 255)); b.alpha_composite(im); sheet.paste(b.convert('RGB'), (x, 20)); dr.text((x, 540), l, fill=(20, 20, 30), font=font)
for bgc, x0 in [((244, 246, 250), 20), ((12, 14, 22), 540)]:
    dr.rectangle([x0 - 10, 580, x0 + 500, 720], fill=bgc); x = x0
    for sz in (96, 72, 48):
        for shp in ('squircle', 'circle'):
            b = Image.new('RGBA', (sz, sz), bgc + (255,)); b.alpha_composite(launcher(shp, sz)); sheet.paste(b.convert('RGB'), (x, 600)); x += sz + 12
sheet.save(OUT)
