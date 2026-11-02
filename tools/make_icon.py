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
RIM_TOP, RIM_BOT = (0xEA, 0xF5, 0xFF), (0xB4, 0xD6, 0xFB)
PANEL_TOP, PANEL_BOT = (0xFF, 0xFF, 0xFF), (0xDD, 0xEB, 0xFD)

# ---------- vetores ----------
def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").write(text)

hx = lambda c: "#%02X%02X%02X" % c
write(f"{RES}/drawable/ic_link_background.xml", f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="18" android:endX="54" android:endY="90"
                android:startColor="{hx(BG_TOP)}" android:endColor="{hx(BG_BOT)}"/>
        </aapt:attr>
    </path>
</vector>
''')

shadow = ""
for dy, a_ in [(1.0, "30"), (2.2, "22"), (3.6, "14")]:
    shadow += f'    <path android:fillColor="#{a_}1B56C8" android:pathData="{svg_path(cmds(0, dy))}"/>\n'

def grad(y0, y1, c0, c1):
    return (f'        <aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="linear" android:startX="54" android:startY="{y0}" android:endX="54" android:endY="{y1}"\n'
            f'                android:startColor="{c0}" android:endColor="{c1}"/>\n        </aapt:attr>\n')

write(f"{RES}/drawable/ic_link_foreground.xml", f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <!-- sombra suave -->
{shadow}    <!-- halo externo do vidro -->
    <path android:pathData="{svg_path(cmds(0))}" android:strokeColor="#FFFFFF" android:strokeAlpha="0.35" android:strokeWidth="2.8"/>
    <!-- aro de vidro (translúcido) -->
    <path android:pathData="{svg_path(cmds())}" android:strokeColor="#FFFFFF" android:strokeAlpha="0.9" android:strokeWidth="1">
{grad(Y0, Y1, hx(RIM_TOP), hx(RIM_BOT))}    </path>
    <!-- painel interno -->
    <path android:pathData="{svg_path(cmds(2.6))}" android:strokeColor="#7FB3F5" android:strokeAlpha="0.5" android:strokeWidth="0.6">
{grad(Y0 + 2.6, Y1 - 2.6, hx(PANEL_TOP), hx(PANEL_BOT))}    </path>
    <!-- brilho -->
    <path android:pathData="{svg_path(cmds(3.4))}">
{grad(Y0 + 3.4, Y0 + 22, "#99FFFFFF", "#00FFFFFF")}    </path>
</vector>
''')
adaptive = '''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_link_background"/>
    <foreground android:drawable="@drawable/ic_link_foreground"/>
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

def render(size, shape):
    """size: lado final; shape: 'squircle' | 'circle'."""
    S = size * SS
    scale = S / 72.0          # viewport -> px (área visível 72 -> S)
    off = 18.0
    T = lambda pts: [((x - off) * scale, (y - off) * scale) for x, y in pts]
    # fundo
    img = vgrad(S, S, 18, 90, BG_TOP, BG_BOT, scale, off).convert("RGBA")
    def polymask(c):
        m = Image.new("L", (S, S), 0); ImageDraw.Draw(m).polygon(T(polygon(c)), fill=255); return m
    def solid(rgb, m, alpha=1.0):
        return Image.merge("RGBA", (*Image.new("RGB", (S, S), rgb).split(), m.point(lambda v: int(v * alpha))))
    def ring(m, w):
        k = max(3, int(w * scale) // 2 * 2 + 1)
        return ImageChops.subtract(m.filter(ImageFilter.MaxFilter(k)), m.filter(ImageFilter.MinFilter(k)))
    def gradfill(c, y0, y1, c0, c1, a0=1.0, a1=1.0):
        m = polymask(c)
        g = vgrad(S, S, y0, y1, c0, c1, scale, off).convert("RGBA")
        ramp = Image.new("L", (1, S))
        for y in range(S):
            t = min(max((y / scale + off - y0) / (y1 - y0), 0), 1)
            ramp.putpixel((0, y), int(255 * (a0 + (a1 - a0) * t)))
        g.putalpha(ImageChops.multiply(m, ramp.resize((S, S))))
        return g
    # sombra
    for dy, a_ in [(1.0, 0x30), (2.2, 0x22), (3.6, 0x14)]:
        img = Image.alpha_composite(img, solid((0x1B, 0x56, 0xC8), polymask(cmds(0, dy)), a_ / 255))
    # halo externo
    img = Image.alpha_composite(img, solid((255, 255, 255), ring(polymask(cmds()), 2.8 + 0.0), 0.35))
    # aro de vidro
    img = Image.alpha_composite(img, gradfill(cmds(), Y0, Y1, RIM_TOP, RIM_BOT))
    img = Image.alpha_composite(img, solid((255, 255, 255), ring(polymask(cmds()), 1.0), 0.9))
    # painel interno
    img = Image.alpha_composite(img, gradfill(cmds(2.6), Y0 + 2.6, Y1 - 2.6, PANEL_TOP, PANEL_BOT))
    img = Image.alpha_composite(img, solid((0x7F, 0xB3, 0xF5), ring(polymask(cmds(2.6)), 0.6), 0.5))
    # brilho
    img = Image.alpha_composite(img, gradfill(cmds(3.4), Y0 + 3.4, Y0 + 22, (255, 255, 255), (255, 255, 255), 0.6, 0.0))
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
