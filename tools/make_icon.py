#!/usr/bin/env python3
"""Gera o ícone do app (vetores adaptativos + mipmaps legados + preview).
Geometria em coordenadas do viewport adaptativo (108x108; área visível 72x72 no centro)."""
import os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops

RES = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res"
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else "/workspace/icon_preview.png"

X0, X1, Y0, Y1, APEX, R = 35.5, 72.5, 32.5, 75.0, 65.0, 6.8
CX = 54.0

def cmds(d=0.0, dy=0.0):
    """Lista de comandos de path do marcador, encolhido em d (inset) e deslocado em dy."""
    x0, x1, y0, y1 = X0 + d, X1 - d, Y0 + d + dy, Y1 - d + dy
    ap = APEX - d * 0.35 + dy
    r = max(R - d, 2.0)
    return [
        ("M", x0 + r, y0), ("L", x1 - r, y0), ("Q", x1, y0, x1, y0 + r),
        ("L", x1, y1 - 2.2), ("Q", x1, y1 + 0.6, x1 - 2.6, y1 - 1.2),
        ("L", CX + 1.7, ap + 0.9), ("Q", CX, ap - 0.1, CX - 1.7, ap + 0.9),
        ("L", x0 + 2.6, y1 - 1.2), ("Q", x0, y1 + 0.6, x0, y1 - 2.2),
        ("L", x0, y0 + r), ("Q", x0, y0, x0 + r, y0), ("Z",),
    ]

def svg_path(c):
    f = lambda v: ("%.2f" % v).rstrip("0").rstrip(".")
    out = []
    for cmd in c:
        if len(cmd) == 1:
            out.append("Z")
        else:
            v = cmd[1:]
            out.append(cmd[0] + " ".join(f"{f(v[i])},{f(v[i+1])}" for i in range(0, len(v), 2)))
    return "".join(out)

def polygon(c, steps=24):
    pts, cur = [], None
    for cmd in c:
        if cmd[0] in "ML":
            cur = (cmd[1], cmd[2]); pts.append(cur)
        elif cmd[0] == "Q":
            x1, y1, x2, y2 = cmd[1:]
            for i in range(1, steps + 1):
                t = i / steps
                pts.append(((1-t)**2*cur[0] + 2*(1-t)*t*x1 + t*t*x2, (1-t)**2*cur[1] + 2*(1-t)*t*y1 + t*t*y2))
            cur = (x2, y2)
    return pts

# ---------- cores ----------
BG_TOP, BG_BOT = (0x86, 0xC3, 0xFD), (0x44, 0x90, 0xFD)
BODY_TOP, BODY_BOT = (0xFF, 0xFF, 0xFF), (0xCB, 0xE0, 0xFC)

# ---------- adaptive icon (camadas PNG: sem diferenças de renderização de vetor) ----------
def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").write(text)

# Sem camada <monochrome>: o ícone é sempre colorido (mesmo com "Ícones temáticos" ligado).
adaptive = '''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_link_bg"/>
    <foreground android:drawable="@drawable/ic_link_fg"/>
</adaptive-icon>
'''
write(f"{RES}/mipmap-anydpi-v26/ic_link.xml", adaptive)
write(f"{RES}/mipmap-anydpi-v26/ic_link_round.xml", adaptive)

# ---------- raster (mipmaps legados + preview) ----------
SS = 4

def lerp(a, b, t): return tuple(int(round(a[i] + (b[i]-a[i])*t)) for i in range(3))

def vgrad(w, h, y_top, y_bot, c0, c1, scale, offy=0.0):
    """Gradiente vertical definido em coordenadas do viewport (y_top..y_bot)."""
    col = Image.new("RGB", (1, h))
    for y in range(h):
        vy = y / scale + offy
        t = min(max((vy - y_top) / (y_bot - y_top), 0), 1)
        col.putpixel((0, y), lerp(c0, c1, t))
    return col.resize((w, h))

def render(size, shape, part="all"):
    """size: lado final; shape: 'squircle' | 'circle' | 'layer'.
    part: 'all' | 'bg' | 'fg'. 'layer' = canvas adaptativo completo 108x108 (sem máscara)."""
    S = size * SS
    if shape == "layer":
        scale, off = S / 108.0, 0.0
    else:
        scale, off = S / 72.0, 18.0   # área visível 72 -> S
    T = lambda pts: [((x - off) * scale, (y - off) * scale) for x, y in pts]
    # fundo
    if part == "fg":
        img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    else:
        img = vgrad(S, S, 18, 90, BG_TOP, BG_BOT, scale, off).convert("RGBA")
    if part == "bg":
        return img.resize((size, size), Image.LANCZOS)
    # sombra
    for dy, a in [(1.0, 0x30), (2.2, 0x22), (3.6, 0x14)]:
        m = Image.new("L", (S, S), 0); ImageDraw.Draw(m).polygon(T(polygon(cmds(0, dy))), fill=a)
        img = Image.composite(Image.new("RGBA", (S, S), (0x1B, 0x56, 0xC8, 255)), img, m) if False else \
            Image.alpha_composite(img, Image.merge("RGBA", (*Image.new("RGB", (S, S), (0x1B, 0x56, 0xC8)).split(), m)))
    # corpo
    m = Image.new("L", (S, S), 0); ImageDraw.Draw(m).polygon(T(polygon(cmds())), fill=255)
    body = vgrad(S, S, Y0, Y1, BODY_TOP, BODY_BOT, scale, off).convert("RGBA")
    img = Image.composite(body, img, m)
    # contorno claro
    edge = ImageChops.subtract(m, m.filter(ImageFilter.MinFilter(max(3, int(0.9*scale)//2*2+1))))
    img = Image.alpha_composite(img, Image.merge("RGBA", (*Image.new("RGB", (S, S), (255, 255, 255)).split(), edge.point(lambda v: int(v*0.85)))))
    # brilho interno
    m2 = Image.new("L", (S, S), 0); ImageDraw.Draw(m2).polygon(T(polygon(cmds(2.4))), fill=255)
    ramp = Image.new("L", (1, S))
    for y in range(S):
        vy = y / scale + off
        t = min(max((vy - (Y0 + 2.4)) / 19.6, 0), 1)
        ramp.putpixel((0, y), int(0xB3 * (1 - t)))
    ramp = ramp.resize((S, S))
    glow = ImageChops.multiply(m2, ramp)
    img = Image.alpha_composite(img, Image.merge("RGBA", (*Image.new("RGB", (S, S), (255, 255, 255)).split(), glow)))
    if shape == "layer":
        return img.resize((size, size), Image.LANCZOS)
    # máscara da forma
    mask = Image.new("L", (S, S), 0); d = ImageDraw.Draw(mask)
    if shape == "circle":
        d.ellipse([0, 0, S - 1, S - 1], fill=255)
    else:
        n, c, a = 5.0, (S - 1) / 2, (S - 1) / 2
        pts = []
        import math
        for i in range(720):
            th = 2 * math.pi * i / 720
            ct, st = math.cos(th), math.sin(th)
            pts.append((c + a * math.copysign(abs(ct) ** (2 / n), ct), c + a * math.copysign(abs(st) ** (2 / n), st)))
        d.polygon(pts, fill=255)
    img.putalpha(mask)
    return img.resize((size, size), Image.LANCZOS)

dens = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for k, px in dens.items():
    d = f"{RES}/mipmap-{k}"
    os.makedirs(d, exist_ok=True)
    render(px, "squircle").save(f"{d}/ic_link.png")
    render(px, "circle").save(f"{d}/ic_link_round.png")

# Camadas adaptativas: cada layer tem de medir exatamente 108dp -> px = 108 * dpi/160 em cada bucket.
# (Em drawable-nodpi o PNG de 432px vira ~144dp em aparelhos xxhdpi e alguns launchers, como o One UI,
#  desenham a camada no tamanho intrínseco, deixando o marcador gigante.)
import shutil
shutil.rmtree(f"{RES}/drawable-nodpi", ignore_errors=True)
for k, px in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
    os.makedirs(f"{RES}/drawable-{k}", exist_ok=True)
    render(px, "layer", "bg").convert("RGB").save(f"{RES}/drawable-{k}/ic_link_bg.png")
    render(px, "layer", "fg").save(f"{RES}/drawable-{k}/ic_link_fg.png")

# ---------- preview ----------
big = render(512, "squircle")
rnd = render(192, "circle")
small = render(96, "squircle")
sheet = Image.new("RGBA", (900, 700), (244, 246, 250, 255))
sheet.alpha_composite(big, (40, 50))
sheet.alpha_composite(rnd, (620, 60))
sheet.alpha_composite(small, (640, 300))
sheet.alpha_composite(render(48, "squircle"), (770, 300))
dark = Image.new("RGBA", (900, 90), (8, 10, 16, 255))
sheet.alpha_composite(dark, (0, 610))
sheet.alpha_composite(render(64, "squircle"), (40, 623))
sheet.alpha_composite(render(64, "circle"), (130, 623))
sheet.convert("RGB").save(PREVIEW)
render(512, "squircle").save(PREVIEW.replace(".png", "_512.png"))
print("ok")
