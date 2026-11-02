#!/usr/bin/env python3
"""Gera o ícone do Link a partir de tools/src/link_icon_target.png (arte exata aprovada).
Adaptativo: camada de fundo = a arte em 72dp centralizada num canvas de 108dp, com as bordas
estendidas (full-bleed); primeiro plano transparente (ver mipmap-anydpi-v26/ic_link.xml).
Legados: ic_link (quadrado arredondado) e ic_link_round (círculo)."""
import numpy as np
from PIL import Image, ImageDraw
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
SRC = Image.open(ROOT / "tools/src/link_icon_target.png").convert("RGBA")

def filled_square():
    """Recorta o conteúdo do quadrado arredondado e preenche os cantos transparentes com a cor vizinha mais próxima."""
    a = np.array(SRC).astype(np.float32)
    ins = 14  # remove franja clara da borda
    a = a[ins:-ins, ins:-ins]
    h, w, _ = a.shape
    solid = a[..., 3] > 250
    rgb = a[..., :3].copy()
    # preenchimento por dilatação iterativa (cor do vizinho sólido mais próximo)
    known = solid.copy()
    for _ in range(400):
        if known.all():
            break
        acc = np.zeros_like(rgb); cnt = np.zeros((h, w), np.float32)
        for dy, dx in [(-1,0),(1,0),(0,-1),(0,1),(-1,-1),(-1,1),(1,-1),(1,1)]:
            k = np.roll(known, (dy, dx), (0, 1)); c = np.roll(rgb, (dy, dx), (0, 1))
            acc += c * k[..., None]; cnt += k
        new = (~known) & (cnt > 0)
        rgb[new] = acc[new] / cnt[new][:, None]
        known |= new
    return Image.fromarray(rgb.clip(0, 255).astype(np.uint8), "RGB")

SQ = filled_square()

def adaptive_bg(px):
    """canvas px (=108dp); arte em 72dp central; bordas estendidas com suavização."""
    inner = round(px * 72 / 108)
    art = np.array(SQ.resize((inner, inner), Image.LANCZOS))
    pad = (px - inner) // 2
    full = np.pad(art, ((pad, px - inner - pad), (pad, px - inner - pad), (0, 0)), mode="edge")
    img = Image.fromarray(full, "RGB")
    # suaviza só a região estendida (fora dos 72dp) para não ter listras
    from PIL import ImageFilter
    blurred = img.filter(ImageFilter.GaussianBlur(px * 0.04))
    mask = Image.new("L", (px, px), 255)
    ImageDraw.Draw(mask).rectangle((pad, pad, pad + inner - 1, pad + inner - 1), fill=0)
    return Image.composite(blurred, img, mask)

def legacy(px, round_):
    # arte original (com seus cantos arredondados) ou círculo
    src = SQ.resize((px, px), Image.LANCZOS).convert("RGBA")
    ss = 4
    m = Image.new("L", (px * ss, px * ss), 0)
    d = ImageDraw.Draw(m)
    if round_:
        d.ellipse((0, 0, px * ss - 1, px * ss - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, px * ss - 1, px * ss - 1), radius=int(px * ss * 0.2), fill=255)
    src.putalpha(m.resize((px, px), Image.LANCZOS))
    return src

dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for name, s in dens.items():
    d = RES / f"drawable-{name}"; d.mkdir(parents=True, exist_ok=True)
    adaptive_bg(round(108 * s)).save(d / "ic_link_bg.png", optimize=True)
    m = RES / f"mipmap-{name}"; m.mkdir(parents=True, exist_ok=True)
    legacy(round(48 * s), False).save(m / "ic_link.png", optimize=True)
    legacy(round(48 * s), True).save(m / "ic_link_round.png", optimize=True)
any_ = RES / "mipmap-anydpi-v26"; any_.mkdir(parents=True, exist_ok=True)
xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_link_bg"/>
    <foreground android:drawable="@android:color/transparent"/>
</adaptive-icon>
'''
(any_ / "ic_link.xml").write_text(xml); (any_ / "ic_link_round.xml").write_text(xml)
print("ok")
