"""Generates Prism nickname badges in a realistic, rendered style.

Every badge is a set of shapes lit like 3D objects: a height map from the shape's distance
field gives normals, which get diffuse light, an environment reflection (metals), refraction-like
inner glow (gems) and specular highlights. Output: 128x128 transparent PNGs plus a preview sheet.

Usage: python3 generate_badges.py <out_dir>
Needs numpy, scipy and Pillow.
"""
import colorsys
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import ndimage

S = 512
OUT = 128
LIGHT = np.array([-0.45, -0.65, 0.62])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
VIEW = np.array([0.0, 0.0, 1.0])
HALF = (LIGHT + VIEW) / np.linalg.norm(LIGHT + VIEW)

YY, XX = np.mgrid[0:S, 0:S].astype(np.float32)


def new_mask():
    img = Image.new("L", (S, S), 0)
    return img, ImageDraw.Draw(img)


def arr(mask_img):
    return np.asarray(mask_img, dtype=np.float32) / 255.0


# region lighting

def height_map(mask, bevel, profile="round", extra=None):
    inside = mask > 0.5
    dist = ndimage.distance_transform_edt(inside).astype(np.float32)
    d = np.clip(dist / bevel, 0, 1)
    if profile == "round":
        h = np.sqrt(1 - (1 - d) ** 2) * bevel
    elif profile == "sphere":
        h = np.sqrt(np.clip(1 - (1 - d) ** 2, 0, 1)) * bevel * 1.4
    else:  # linear ridges, crystal look
        h = dist * 0.9
    if extra is not None:
        h = h + extra
    return ndimage.gaussian_filter(h, 1.6)


def normals(h, strength=1.0):
    gy, gx = np.gradient(h)
    n = np.dstack([-gx * strength, -gy * strength, np.ones_like(h)])
    return n / np.linalg.norm(n, axis=2, keepdims=True)


def shade(mask, n, material, height=None):
    """Returns float RGB (S, S, 3) in 0..1 for the material."""
    ndl = np.clip(np.tensordot(n, LIGHT, axes=1), 0, 1)
    ndh = np.clip(np.tensordot(n, HALF, axes=1), 0, 1)
    # Reflection of the view ray, used to look up a studio environment.
    r = 2 * n[..., 2:3] * n - VIEW
    ry = r[..., 1]
    env = np.clip(0.5 - ry * 0.75, 0, 1)  # bright above, dark below
    kind = material["kind"]
    base = np.array(material["color"], dtype=np.float32) / 255.0
    if kind == "metal":
        dark = np.array(material.get("dark", tuple(c * 0.28 for c in material["color"])), dtype=np.float32) / 255.0
        light = np.array(material.get("light", tuple(min(255, c * 1.35 + 30) for c in material["color"])),
                         dtype=np.float32) / 255.0
        env_col = dark + (light - dark) * env[..., None] ** 1.3
        col = env_col * (0.55 + 0.55 * ndl[..., None])
        spec = ndh ** material.get("shine", 60)
        col = col + spec[..., None] * np.minimum(base * 0.6 + 0.55, 1.0)
    elif kind == "holo":
        hue = (np.arctan2(n[..., 1], n[..., 0]) / (2 * math.pi) + 0.15 + env * 0.35) % 1.0
        h6 = hue * 6.0
        c = np.dstack([np.clip(np.abs(h6 - 3) - 1, 0, 1), np.clip(2 - np.abs(h6 - 2), 0, 1),
                       np.clip(2 - np.abs(h6 - 4), 0, 1)])
        pastel = 0.45 + 0.55 * c
        col = pastel * (0.45 + 0.55 * env[..., None]) * (0.6 + 0.5 * ndl[..., None])
        col = col + (ndh ** 70)[..., None] * 0.9
    elif kind == "gem":
        thick = np.clip(height / (height.max() + 1e-6), 0, 1) if height is not None else 0.5
        deep = base * 0.18
        glow = np.minimum(base * 1.5 + 0.08, 1.0)
        # Light passing through: brighter on the far side from the light, like real stones.
        through = np.clip(1 - ndl, 0, 1) ** 1.5
        col = deep + (glow - deep) * (0.35 * thick + 0.65 * through)[..., None] * 0.9
        col = col + (0.25 * env ** 3)[..., None]
        col = col + (ndh ** material.get("shine", 140))[..., None] * 1.3
    elif kind == "crystal":
        hue = (np.arctan2(n[..., 1], n[..., 0]) / (2 * math.pi) + 0.6) % 1.0
        h6 = hue * 6.0
        rainbow = np.dstack([np.clip(np.abs(h6 - 3) - 1, 0, 1), np.clip(2 - np.abs(h6 - 2), 0, 1),
                             np.clip(2 - np.abs(h6 - 4), 0, 1)])
        tilt = np.clip(1 - n[..., 2], 0, 1)
        col = base * (0.55 + 0.5 * env[..., None]) + rainbow * (tilt * 0.55)[..., None]
        col = col * (0.65 + 0.45 * ndl[..., None])
        col = col + (ndh ** 90)[..., None] * 1.4
    elif kind == "pearl":
        col = base * (0.55 + 0.5 * ndl[..., None]) + np.array([1.0, 0.75, 0.85]) * (1 - n[..., 2:3]) * 0.25
        col = col + (ndh ** 40)[..., None] * 0.8
    else:  # plastic / organic
        col = base * (0.25 + 0.85 * ndl[..., None])
        col = col + (ndh ** material.get("shine", 30))[..., None] * material.get("spec", 0.45)
    return np.clip(col, 0, 1)


def render(mask_img, material, bevel=40, profile="round", extra=None, strength=1.0, alpha=1.0):
    mask = arr(mask_img)
    h = height_map(mask, bevel, profile, extra)
    n = normals(h, strength)
    col = shade(mask, n, material, h)
    a = np.clip(mask * alpha, 0, 1)
    rgba = np.dstack([col, a])
    return Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")


def finish(layers, glow=None):
    """Stacks layers over a soft contact shadow; optional colored bloom behind."""
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    union = Image.new("L", (S, S), 0)
    for layer in layers:
        union = Image.fromarray(np.maximum(np.asarray(union), np.asarray(layer.getchannel("A"))))
    if glow is not None:
        bloom = Image.new("RGBA", (S, S), glow + (0,))
        bloom.putalpha(union.filter(ImageFilter.GaussianBlur(26)).point(lambda v: int(v * 0.75)))
        canvas.alpha_composite(bloom)
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    shadow.putalpha(union.filter(ImageFilter.GaussianBlur(12)).point(lambda v: int(v * 0.5)))
    canvas.alpha_composite(shadow, (6, 12))
    for layer in layers:
        canvas.alpha_composite(layer)
    return canvas


def sparkle(img, cx, cy, r, alpha=255):
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    pts = []
    for i in range(8):
        a = math.pi / 4 * i - math.pi / 2
        rr = r if i % 2 == 0 else r * 0.16
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.polygon(pts, fill=(255, 255, 255, alpha))
    halo = layer.filter(ImageFilter.GaussianBlur(r * 0.25))
    img.alpha_composite(halo)
    img.alpha_composite(layer)


GOLD = {"kind": "metal", "color": (255, 190, 60), "dark": (110, 52, 8), "light": (255, 238, 170), "shine": 70}
ROSE_GOLD = {"kind": "metal", "color": (240, 160, 140), "shine": 70}
SILVER = {"kind": "metal", "color": (226, 230, 240), "shine": 90}
RUBY = {"kind": "gem", "color": (230, 20, 70)}
SAPPHIRE = {"kind": "gem", "color": (40, 100, 255)}
EMERALD = {"kind": "gem", "color": (20, 200, 120)}
AMETHYST = {"kind": "gem", "color": (150, 80, 255)}
PEARL = {"kind": "pearl", "color": (245, 238, 240)}

# endregion

# region shapes


def star_points(cx, cy, r_out, r_in, n=5, rot=-90):
    pts = []
    for i in range(n * 2):
        a = math.radians(rot + 180 / n * i)
        r = r_out if i % 2 == 0 else r_in
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


def heart_points(cx, cy, scale):
    pts = []
    for i in range(360):
        t = 2 * math.pi * i / 360
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * scale, cy - y * scale))
    return pts


def circle_mask(cx, cy, r):
    m, d = new_mask()
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    return m


# endregion

# region badges


def badge_crown():
    m, d = new_mask()
    d.polygon([(96, 380), (70, 160), (182, 262), (256, 108), (330, 262), (442, 160), (416, 380)], fill=255)
    d.rounded_rectangle((84, 330, 428, 420), 24, fill=255)
    for x, y in [(70, 150), (256, 98), (442, 150)]:
        d.ellipse((x - 30, y - 30, x + 30, y + 30), fill=255)
    layers = [render(m, GOLD, bevel=44)]
    for x, mat in [(170, RUBY), (256, SAPPHIRE), (342, EMERALD)]:
        layers.append(render(circle_mask(x, 376, 30), mat, bevel=30, profile="sphere"))
    layers.append(render(circle_mask(256, 98, 22), RUBY, bevel=22, profile="sphere"))
    img = finish(layers)
    sparkle(img, 300, 150, 30)
    return img


def badge_verified():
    m, d = new_mask()
    pts = []
    for i in range(720):
        a = 2 * math.pi * i / 720
        r = 200 + 18 * math.cos(12 * a)
        pts.append((256 + math.cos(a) * r, 256 + math.sin(a) * r))
    d.polygon(pts, fill=255)
    check, cd = new_mask()
    cd.line([(168, 262), (230, 324), (350, 194)], fill=255, width=52, joint="curve")
    for x, y in [(168, 262), (350, 194)]:
        cd.ellipse((x - 26, y - 26, x + 26, y + 26), fill=255)
    blue = {"kind": "metal", "color": (40, 120, 255), "dark": (8, 30, 110), "light": (140, 210, 255),
            "shine": 80}
    layers = [render(m, blue, bevel=46), render(check, SILVER, bevel=22)]
    return finish(layers)


def badge_diamond():
    m, d = new_mask()
    d.polygon([(150, 120), (362, 120), (446, 214), (256, 440), (66, 214)], fill=255)
    crystal = {"kind": "crystal", "color": (215, 235, 255)}
    img = finish([render(m, crystal, profile="linear", strength=0.9)])
    lines = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ld = ImageDraw.Draw(lines)
    for seg in [((66, 214), (446, 214)), ((150, 120), (204, 214)), ((362, 120), (308, 214)),
                ((256, 120), (204, 214)), ((256, 120), (308, 214)), ((204, 214), (256, 440)),
                ((308, 214), (256, 440)), ((130, 214), (256, 440)), ((382, 214), (256, 440))]:
        ld.line(seg, fill=(255, 255, 255, 110), width=4)
    img.alpha_composite(lines)
    sparkle(img, 372, 132, 44)
    sparkle(img, 170, 250, 20, 200)
    return img


def badge_star():
    m, d = new_mask()
    d.polygon(star_points(256, 272, 226, 104), fill=255)
    m = m.filter(ImageFilter.GaussianBlur(5)).point(lambda v: 255 if v > 128 else 0)
    img = finish([render(m, GOLD, profile="linear", strength=0.55)])
    sparkle(img, 330, 150, 34)
    return img


def badge_bolt():
    m, d = new_mask()
    d.polygon([(300, 46), (116, 292), (236, 292), (192, 466), (396, 206), (272, 206), (334, 46)], fill=255)
    m = m.filter(ImageFilter.GaussianBlur(3)).point(lambda v: 255 if v > 128 else 0)
    img = finish([render(m, {"kind": "metal", "color": (255, 214, 60), "shine": 80}, profile="linear",
                         strength=0.7)], glow=(255, 190, 40))
    sparkle(img, 150, 140, 30)
    return img


def badge_heart():
    m, d = new_mask()
    d.polygon(heart_points(256, 246, 12.8), fill=255)
    img = finish([render(m, RUBY, bevel=120, profile="round")])
    sparkle(img, 330, 150, 36)
    return img


def badge_flame():
    # Emissive: no lighting, layered glowing gradients.
    def flame_mask(scale, dy):
        m, d = new_mask()
        pts = []
        for i in range(200):
            t = i / 199 * 2 * math.pi
            # Teardrop with a flickering tip.
            r = (1 - math.sin(t)) ** 0.9
            x = 256 + math.cos(t) * 150 * r * scale + math.sin(t * 3) * 10 * scale
            y = 270 - dy * 0.2 - math.sin(t) * 200 * scale + (1 - scale) * 120
            pts.append((x, y))
        d.polygon(pts, fill=255)
        return m.filter(ImageFilter.GaussianBlur(4))

    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for scale, dy, color, blur in [(1.0, 0, (255, 60, 20), 0), (0.78, 30, (255, 140, 20), 0),
                                   (0.52, 60, (255, 220, 80), 0), (0.3, 80, (255, 255, 230), 0)]:
        m = flame_mask(scale, dy)
        layer = Image.new("RGBA", (S, S), color + (0,))
        layer.putalpha(m)
        if scale == 1.0:
            bloom = Image.new("RGBA", (S, S), (255, 90, 20, 0))
            bloom.putalpha(m.filter(ImageFilter.GaussianBlur(28)).point(lambda v: int(v * 0.8)))
            img.alpha_composite(bloom)
        img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(6)))
    sparkle(img, 330, 210, 20, 220)
    return img


def badge_comet():
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    tail, td = new_mask()
    td.polygon([(40, 100), (330, 240), (300, 340)], fill=255)
    tail = tail.filter(ImageFilter.GaussianBlur(26))
    grad = Image.linear_gradient("L").rotate(-60, expand=False).resize((S, S))
    tail_alpha = Image.fromarray((arr(tail) * arr(grad) * 255 * 1.4).clip(0, 255).astype(np.uint8))
    for color, blur in [((120, 160, 255), 0), ((200, 230, 255), 10)]:
        layer = Image.new("RGBA", (S, S), color + (0,))
        layer.putalpha(tail_alpha.filter(ImageFilter.GaussianBlur(blur)))
        img.alpha_composite(layer)
    orb = render(circle_mask(336, 296, 104), SAPPHIRE, bevel=104, profile="sphere")
    img.alpha_composite(finish([orb], glow=(90, 140, 255)))
    sparkle(img, 300, 250, 40)
    sparkle(img, 130, 330, 22, 220)
    return img


def badge_pumpkin():
    lobes = [(150, 108), (362, 108), (204, 118), (308, 118), (256, 122)]
    layers = []
    for cx, rw in lobes:
        m, d = new_mask()
        d.ellipse((cx - rw, 150, cx + rw, 446), fill=255)
        layers.append(render(m, {"kind": "plastic", "color": (240, 120, 20), "shine": 25, "spec": 0.35},
                             bevel=80))
    stem, sd = new_mask()
    sd.polygon([(236, 168), (244, 92), (288, 70), (296, 86), (270, 104), (276, 168)], fill=255)
    layers.append(render(stem, {"kind": "plastic", "color": (110, 130, 50), "shine": 20, "spec": 0.25},
                         bevel=14))
    img = finish(layers)
    # Carved face lit from inside.
    face, fd = new_mask()
    fd.polygon([(168, 284), (232, 284), (200, 226)], fill=255)
    fd.polygon([(280, 284), (344, 284), (312, 226)], fill=255)
    fd.polygon([(156, 330), (196, 356), (226, 334), (256, 366), (286, 334), (316, 356), (356, 330),
                (326, 404), (256, 420), (186, 404)], fill=255)
    glow = Image.new("RGBA", (S, S), (255, 170, 30, 0))
    glow.putalpha(face.filter(ImageFilter.GaussianBlur(16)).point(lambda v: int(v * 0.9)))
    img.alpha_composite(glow)
    inner = Image.new("RGBA", (S, S), (255, 236, 140, 0))
    inner.putalpha(face)
    img.alpha_composite(inner)
    return img


def badge_ghost():
    m, d = new_mask()
    d.ellipse((120, 64, 392, 336), fill=255)
    d.rectangle((120, 200, 392, 400), fill=255)
    for i in range(4):
        x = 120 + i * 68
        d.ellipse((x, 364, x + 68, 440), fill=255)
    eyes, ed = new_mask()
    ed.ellipse((192, 186, 236, 250), fill=255)
    ed.ellipse((276, 186, 320, 250), fill=255)
    ed.ellipse((236, 272, 276, 316), fill=255)
    extra = -arr(eyes.filter(ImageFilter.GaussianBlur(6))) * 34
    body = render(m, {"kind": "pearl", "color": (232, 236, 255)}, bevel=90, extra=extra, alpha=0.93)
    img = finish([body], glow=(170, 190, 255))
    shade_layer = Image.new("RGBA", (S, S), (30, 26, 60, 0))
    shade_layer.putalpha(eyes.point(lambda v: int(v * 0.85)))
    img.alpha_composite(shade_layer)
    return img


def badge_sakura():
    petal, pd = new_mask()
    pd.ellipse((190, 64, 322, 262), fill=255)
    pd.polygon([(238, 64), (256, 104), (274, 64)], fill=0)
    layers = []
    for i in range(5):
        layers.append(render(petal.rotate(-72 * i, center=(256, 256), resample=Image.BICUBIC),
                             {"kind": "gem", "color": (255, 120, 175)}, bevel=60))
    layers.append(render(circle_mask(256, 256, 54), PEARL, bevel=54, profile="sphere"))
    return finish(layers)


BADGES = [
    ("crown", "Корона", badge_crown),
    ("verified", "Галочка Prism", badge_verified),
    ("diamond", "Бриллиант", badge_diamond),
    ("star", "Золотая звезда", badge_star),
    ("bolt", "Молния", badge_bolt),
    ("heart", "Рубиновое сердце", badge_heart),
    ("flame", "Пламя", badge_flame),
    ("comet", "Комета", badge_comet),
    ("pumpkin", "Тыква", badge_pumpkin),
    ("ghost", "Призрак", badge_ghost),
    ("sakura", "Сакура", badge_sakura),
]

# endregion


def preview(images, out):
    bold = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
    title = ImageFont.truetype(bold, 44)
    nick = ImageFont.truetype(bold, 34)
    small = ImageFont.truetype("/usr/share/fonts/opentype/inter/Inter-Medium.otf", 24)
    cols, cell_w, cell_h = 3, 420, 330
    rows = math.ceil(len(images) / cols)
    sheet = Image.new("RGBA", (cols * cell_w + 40, rows * cell_h + 120), (12, 12, 16, 255))
    d = ImageDraw.Draw(sheet)
    d.text((30, 30), "Prism: значки у ника", font=title, fill=(255, 255, 255))
    for i, (key, label, img) in enumerate(images):
        x = 20 + (i % cols) * cell_w
        y = 110 + (i // cols) * cell_h
        d.rounded_rectangle((x + 10, y + 10, x + cell_w - 10, y + cell_h - 10), 28, fill=(28, 28, 34, 255))
        big = img.resize((150, 150), Image.LANCZOS)
        sheet.alpha_composite(big, (x + (cell_w - 150) // 2, y + 20))
        name = "meteotok"
        # Real size on a dark and on a light background.
        tw = d.textlength(name, font=nick)
        nx = x + (cell_w - tw - 46) // 2
        d.text((nx, y + 186), name, font=nick, fill=(255, 255, 255))
        sheet.alpha_composite(img.resize((42, 42), Image.LANCZOS), (int(nx + tw + 6), y + 186))
        d.rounded_rectangle((x + 40, y + 236, x + cell_w - 40, y + 282), 14, fill=(255, 255, 255, 255))
        d.text((nx, y + 240), name, font=nick, fill=(22, 24, 35))
        sheet.alpha_composite(img.resize((42, 42), Image.LANCZOS), (int(nx + tw + 6), y + 238))
        lw = d.textlength(label, font=small)
        d.text((x + (cell_w - lw) // 2, y + 290), label, font=small, fill=(170, 170, 185))
    sheet.convert("RGB").save(out, quality=95)


def main():
    out_dir = Path(sys.argv[1])
    out_dir.mkdir(parents=True, exist_ok=True)
    images = []
    for key, label, fn in BADGES:
        img = fn().resize((OUT, OUT), Image.LANCZOS)
        img.save(out_dir / f"prism_badge_{key}.png")
        images.append((key, label, img))
    preview(images, out_dir / "badges-preview.png")


if __name__ == "__main__":
    main()
