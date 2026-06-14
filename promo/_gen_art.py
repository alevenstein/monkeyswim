"""
Generates MonkeySwim store art (app-icon options + feature graphic) procedurally
with PIL, matching the in-game palette and sprite style. Supersampled 3x then
downscaled for clean anti-aliased edges.

Palette pulled from app/src/main/res/values/colors.xml.
"""
import os, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SS = 3  # supersample factor

# ---- palette ----
WATER_DEEP    = (6, 50, 76)
WATER_MID     = (9, 70, 105)
WATER_SHALLOW = (15, 90, 134)
FUR        = (139, 90, 43)
FUR_LIGHT  = (165, 106, 51)
FUR_LIGHTR = (197, 138, 78)
FUR_DARK   = (110, 68, 31)
FUR_OUT    = (42, 22, 5)
FACE       = (242, 210, 168)
FACE_SHADE = (224, 188, 143)
EYE_W      = (255, 255, 255)
EYE_B      = (28, 24, 22)
PELLET     = (255, 233, 176)
GOLD       = (255, 210, 48)
GOLD_DK    = (196, 150, 10)
PIR = {
    "red":    (192, 57, 43),
    "blue":   (46, 134, 193),
    "green":  (39, 174, 96),
    "orange": (230, 126, 34),
}
PIR_BELLY = (217, 84, 63)
TOOTH = (250, 250, 245)

ASSET_DIR = os.path.join(os.path.dirname(__file__), "store-listing-assets")
OPT_DIR = os.path.join(ASSET_DIR, "icon_options")
os.makedirs(OPT_DIR, exist_ok=True)


def font(size, heavy=True):
    candidates = ([
        r"C:\Windows\Fonts\ariblk.ttf",   # Arial Black
        r"C:\Windows\Fonts\arialbd.ttf",
    ] if heavy else [r"C:\Windows\Fonts\arialbd.ttf"])
    for c in candidates:
        if os.path.exists(c):
            return ImageFont.truetype(c, size)
    return ImageFont.load_default()


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def water_bg(W, H, top=WATER_DEEP, bot=(3, 36, 56)):
    """Vertical depth gradient + wavy bands + caustic highlights."""
    img = Image.new("RGB", (W, H), top)
    px = img.load()
    for y in range(H):
        c = lerp(top, bot, y / H)
        for x in range(W):
            px[x, y] = c
    d = ImageDraw.Draw(img, "RGBA")
    # wavy bands
    for i in range(7):
        yb = int(H * (i + 0.5) / 7)
        amp = H * 0.012
        pts = []
        for x in range(0, W + 1, max(4, W // 120)):
            pts.append((x, yb + amp * math.sin(x / (W / 6.0) + i)))
        d.line(pts, fill=(255, 255, 255, 16), width=max(2, H // 260))
    # caustic dots
    rnd = [(0.13, 0.22), (0.34, 0.55), (0.66, 0.3), (0.8, 0.7), (0.5, 0.85),
           (0.22, 0.74), (0.9, 0.18), (0.45, 0.15)]
    for fx, fy in rnd:
        r = int(W * 0.05)
        d.ellipse([fx * W - r, fy * H - r, fx * W + r, fy * H + r],
                  fill=(80, 150, 190, 22))
    return img


# ---------- monkey (front/top face) ----------
def draw_monkey(d, cx, cy, R):
    # ears
    for sx in (-1, 1):
        ex, ey = cx + sx * 0.82 * R, cy - 0.5 * R
        d.ellipse([ex - 0.34*R, ey - 0.34*R, ex + 0.34*R, ey + 0.34*R], fill=FUR_OUT)
        d.ellipse([ex - 0.30*R, ey - 0.30*R, ex + 0.30*R, ey + 0.30*R], fill=FUR_LIGHTR)
        d.ellipse([ex - 0.15*R, ey - 0.15*R, ex + 0.15*R, ey + 0.15*R], fill=FACE)
    # head outline + fur
    d.ellipse([cx - R - 0.05*R, cy - R - 0.05*R, cx + R + 0.05*R, cy + R + 0.05*R], fill=FUR_OUT)
    d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=FUR)
    # top highlight + bottom shade (clipped within head via smaller ellipses)
    d.ellipse([cx - 0.82*R, cy - 0.95*R, cx + 0.82*R, cy - 0.1*R], fill=FUR_LIGHT)
    d.ellipse([cx - 0.85*R, cy + 0.2*R, cx + 0.85*R, cy + 1.02*R], fill=FUR_DARK)
    # face mask (peanut: muzzle + upper face), with a soft shade behind
    d.ellipse([cx - 0.66*R, cy - 0.52*R, cx + 0.66*R, cy + 0.32*R], fill=FACE_SHADE)
    d.ellipse([cx - 0.54*R, cy - 0.05*R, cx + 0.54*R, cy + 0.8*R], fill=FACE_SHADE)
    d.ellipse([cx - 0.6*R, cy - 0.46*R, cx + 0.6*R, cy + 0.26*R], fill=FACE)
    d.ellipse([cx - 0.48*R, cy + 0.0*R, cx + 0.48*R, cy + 0.72*R], fill=FACE)
    # eyes
    for sx in (-1, 1):
        ex, ey = cx + sx * 0.27 * R, cy - 0.12 * R
        d.ellipse([ex - 0.19*R, ey - 0.21*R, ex + 0.19*R, ey + 0.21*R], fill=EYE_W)
        d.ellipse([ex - 0.10*R, ey - 0.06*R, ex + 0.10*R, ey + 0.16*R], fill=EYE_B)
        d.ellipse([ex - 0.03*R, ey - 0.02*R, ex + 0.05*R, ey + 0.06*R], fill=EYE_W)
        # brow
        d.arc([ex - 0.22*R, ey - 0.34*R, ex + 0.22*R, ey + 0.08*R], 200, 340, fill=FUR_DARK, width=max(2, int(0.05*R)))
    # nostrils + mouth
    for sx in (-1, 1):
        nx, ny = cx + sx * 0.09 * R, cy + 0.40 * R
        d.ellipse([nx - 0.045*R, ny - 0.035*R, nx + 0.045*R, ny + 0.035*R], fill=(120, 86, 56))
    d.arc([cx - 0.22*R, cy + 0.4*R, cx + 0.22*R, cy + 0.66*R], 20, 160, fill=(150, 110, 80), width=max(2, int(0.045*R)))


# ---------- piranha (side view, facing +x by default) ----------
def draw_piranha(base, cx, cy, L, color, facing=1, angle=0):
    """Draw a side-view piranha onto `base` (RGBA paste with rotation)."""
    pad = int(L * 1.4)
    layer = Image.new("RGBA", (pad * 2, pad * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    ox = oy = pad
    s = L
    fin = lerp(color, (0, 0, 0), 0.42)
    back = lerp(color, (0, 0, 0), 0.22)
    # tail (forked) behind (-x)
    d.polygon([(ox - 0.95*s, oy), (ox - 1.6*s, oy - 0.6*s),
               (ox - 1.28*s, oy), (ox - 1.6*s, oy + 0.6*s)], fill=fin)
    # dorsal fin
    d.polygon([(ox - 0.15*s, oy - 0.5*s), (ox + 0.12*s, oy - 0.92*s),
               (ox + 0.32*s, oy - 0.48*s)], fill=fin)
    # body (humped, blunt snout at +x)
    body = [
        (ox - 0.95*s, oy - 0.26*s),
        (ox - 0.35*s, oy - 0.66*s),
        (ox + 0.3*s,  oy - 0.62*s),
        (ox + 0.62*s, oy - 0.42*s),
        (ox + 1.12*s, oy - 0.04*s),   # snout top
        (ox + 1.12*s, oy + 0.12*s),   # snout bottom
        (ox + 0.6*s,  oy + 0.5*s),
        (ox + 0.1*s,  oy + 0.58*s),
        (ox - 0.5*s,  oy + 0.5*s),
        (ox - 0.95*s, oy + 0.26*s),
    ]
    d.polygon(body, fill=color, outline=lerp(color, (0, 0, 0), 0.6))
    # back shade
    d.polygon([(ox - 0.9*s, oy - 0.26*s), (ox - 0.3*s, oy - 0.62*s),
               (ox + 0.3*s, oy - 0.58*s), (ox + 0.6*s, oy - 0.4*s),
               (ox + 0.55*s, oy - 0.18*s), (ox - 0.85*s, oy - 0.06*s)], fill=back)
    # belly (red)
    d.ellipse([ox - 0.7*s, oy + 0.1*s, ox + 0.7*s, oy + 0.55*s], fill=PIR_BELLY)
    # pectoral fin
    d.polygon([(ox + 0.05*s, oy + 0.2*s), (ox - 0.25*s, oy + 0.6*s),
               (ox + 0.3*s, oy + 0.32*s)], fill=fin)
    # teeth (sawtooth) at the mouth
    tx0, tx1 = ox + 0.6*s, ox + 1.08*s
    n = 5
    tw = (tx1 - tx0) / n
    for i in range(n):
        xa = tx0 + i*tw
        d.polygon([(xa, oy - 0.04*s), (xa + tw*0.5, oy + 0.06*s), (xa + tw, oy - 0.04*s)], fill=TOOTH)
    # eye
    ex, ey = ox + 0.42*s, oy - 0.24*s
    d.ellipse([ex - 0.17*s, ey - 0.17*s, ex + 0.17*s, ey + 0.17*s], fill=EYE_W)
    d.ellipse([ex - 0.02*s, ey - 0.09*s, ex + 0.16*s, ey + 0.09*s], fill=EYE_B)
    if facing < 0:
        layer = layer.transpose(Image.FLIP_LEFT_RIGHT)
    if angle:
        layer = layer.rotate(angle, resample=Image.BICUBIC, expand=False)
    base.alpha_composite(layer, (int(cx - pad), int(cy - pad)))


def pellets(d, W, H, coords, r):
    for fx, fy in coords:
        x, y = fx * W, fy * H
        d.ellipse([x - r, y - r, x + r, y + r], fill=PELLET)


def outlined_text(d, xy, text, fnt, fill, outline, ow, shadow=None):
    x, y = xy
    if shadow:
        d.text((x + ow*2, y + ow*2), text, font=fnt, fill=shadow)
    for dx in range(-ow, ow + 1):
        for dy in range(-ow, ow + 1):
            if dx*dx + dy*dy <= ow*ow:
                d.text((x + dx, y + dy), text, font=fnt, fill=outline)
    d.text((x, y), text, font=fnt, fill=fill)


# ===================== ICONS =====================
def icon_face(size=512):
    W = H = size * SS
    img = water_bg(W, H).convert("RGBA")
    d = ImageDraw.Draw(img, "RGBA")
    # wave bands low
    for i, yb in enumerate([0.78, 0.9]):
        y = yb * H
        amp = H * 0.03
        pts = [(x, y + amp * math.sin(x / (W/4.0))) for x in range(0, W+1, W//60)]
        for k in range(len(pts)-1):
            d.line([pts[k], pts[k+1]], fill=WATER_SHALLOW, width=int(H*0.018))
    pellets(d, W, H, [(0.12,0.16),(0.88,0.16),(0.1,0.5),(0.9,0.52),(0.5,0.08)], int(W*0.022))
    draw_monkey(d, W*0.5, H*0.44, R=W*0.30)
    return img.convert("RGB").resize((size, size), Image.LANCZOS)


def icon_chase(size=512):
    W = H = size * SS
    img = water_bg(W, H).convert("RGBA")
    d = ImageDraw.Draw(img, "RGBA")
    pellets(d, W, H, [(0.2,0.2),(0.35,0.2),(0.5,0.2),(0.2,0.35),(0.78,0.7),(0.6,0.8),(0.85,0.5)], int(W*0.02))
    # monkey fleeing upper-left, piranhas chasing from lower-right
    draw_monkey(d, W*0.36, H*0.4, R=W*0.22)
    draw_piranha(img, W*0.7, H*0.62, L=W*0.14, color=PIR["red"], facing=-1, angle=18)
    draw_piranha(img, W*0.84, H*0.8, L=W*0.11, color=PIR["green"], facing=-1, angle=10)
    return img.convert("RGB").resize((size, size), Image.LANCZOS)


def icon_porthole(size=512):
    W = H = size * SS
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img, "RGBA")
    cx, cy, R = W*0.5, H*0.5, W*0.43
    # rounded-square dark frame backdrop
    d.rounded_rectangle([0, 0, W, H], radius=int(W*0.22), fill=(8, 40, 60))
    # brass ring (outer + inner bevel)
    d.ellipse([cx-R, cy-R, cx+R, cy+R], fill=(186, 150, 78))
    d.ellipse([cx-R*0.97, cy-R*0.97, cx+R*0.97, cy+R*0.97], fill=(150, 116, 56))
    # circular water disc inside the ring
    rin = int(R*0.86)
    disc = water_bg(rin*2, rin*2).convert("RGBA")
    dm = Image.new("L", (rin*2, rin*2), 0)
    ImageDraw.Draw(dm).ellipse([0, 0, rin*2-1, rin*2-1], fill=255)
    img.paste(disc, (int(cx-rin), int(cy-rin)), dm)
    d = ImageDraw.Draw(img, "RGBA")
    # a couple of bubbles + the monkey peering through
    for fx, fy, br in [(0.7, 0.32, 0.03), (0.3, 0.7, 0.022), (0.74, 0.7, 0.018)]:
        d.ellipse([cx + (fx-0.5)*2*rin - br*W, cy + (fy-0.5)*2*rin - br*W,
                   cx + (fx-0.5)*2*rin + br*W, cy + (fy-0.5)*2*rin + br*W],
                  fill=(120, 180, 215, 70))
    draw_monkey(d, cx, cy + R*0.04, R=W*0.27)
    # rivets around the ring
    for a in range(0, 360, 45):
        rx = cx + R*0.91*math.cos(math.radians(a))
        ry = cy + R*0.91*math.sin(math.radians(a))
        d.ellipse([rx-W*0.018, ry-W*0.018, rx+W*0.018, ry+W*0.018], fill=(96, 74, 36))
    return img.convert("RGB").resize((size, size), Image.LANCZOS)


# ===================== FEATURE GRAPHIC =====================
def feature_graphic(W=1024, H=500):
    w, h = W*SS, H*SS
    img = water_bg(w, h, top=(8, 56, 84), bot=(4, 30, 48)).convert("RGBA")
    d = ImageDraw.Draw(img, "RGBA")
    # subtle vignette on left for text legibility
    grad = Image.new("L", (w, h), 0)
    gd = ImageDraw.Draw(grad)
    for x in range(w):
        gd.line([(x, 0), (x, h)], fill=max(0, int(150 * (1 - x / (w*0.62)))))
    shade = Image.new("RGBA", (w, h), (2, 22, 36, 255))
    shade.putalpha(grad)
    img.alpha_composite(shade)
    d = ImageDraw.Draw(img, "RGBA")
    # pellets sprinkled on the right
    pellets(d, w, h, [(0.62,0.25),(0.7,0.25),(0.78,0.25),(0.86,0.25),
                      (0.66,0.5),(0.74,0.72),(0.9,0.55),(0.6,0.8),(0.82,0.85)], int(w*0.008))
    # gameplay cluster on the right: monkey chased by colored piranhas
    draw_monkey(d, w*0.66, h*0.5, R=h*0.27)
    draw_piranha(img, w*0.86, h*0.36, L=h*0.16, color=PIR["red"], facing=-1, angle=15)
    draw_piranha(img, w*0.9,  h*0.66, L=h*0.14, color=PIR["blue"], facing=-1, angle=-8)
    draw_piranha(img, w*0.78, h*0.82, L=h*0.13, color=PIR["green"], facing=-1, angle=-18)
    draw_piranha(img, w*0.97, h*0.5,  L=h*0.12, color=PIR["orange"], facing=-1, angle=4)
    # wordmark on the left
    f1 = font(int(h*0.2))
    f2 = font(int(h*0.2))
    ftag = font(int(h*0.062), heavy=False)
    x0 = int(w*0.05)
    outlined_text(d, (x0, int(h*0.24)), "MONKEY", f1, GOLD, (40, 26, 6), int(h*0.012), shadow=(2,18,30))
    outlined_text(d, (x0, int(h*0.46)), "RAPIDS", f2, GOLD, (40, 26, 6), int(h*0.012), shadow=(2,18,30))
    d.text((x0 + int(h*0.01), int(h*0.7)), "Dodge the piranhas. Clear the river.",
           font=ftag, fill=(225, 240, 250))
    return img.convert("RGB").resize((W, H), Image.LANCZOS)


if __name__ == "__main__":
    icon_face().save(os.path.join(OPT_DIR, "icon_A_face.png"))
    icon_chase().save(os.path.join(OPT_DIR, "icon_B_chase.png"))
    icon_porthole().save(os.path.join(OPT_DIR, "icon_C_porthole.png"))
    # primary icon = the brand face
    icon_face().save(os.path.join(ASSET_DIR, "icon.png"))
    feature_graphic().save(os.path.join(ASSET_DIR, "feature_graphic.png"))
    print("done")
