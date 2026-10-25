"""Generates Prism sticker badges (shown to the right of a nickname).

Each badge is drawn at 4x, gets a white sticker outline, a soft shadow and a gloss,
and is saved as a 128x128 transparent PNG. A preview sheet shows them next to nicknames.

Usage: python3 generate_badges.py <out_dir>
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

S = 512  # working size
OUT = 128


def gradient(size, top, bottom, angle=90):
    """Linear gradient image, angle 90 = top to bottom, 0 = left to right."""
    w, h = size
    base = Image.new("RGBA", size)
    px = base.load()
    rad = math.radians(angle)
    dx, dy = math.cos(rad), math.sin(rad)
    lo = min(0, dx * w) + min(0, dy * h)
    hi = max(0, dx * w) + max(0, dy * h)
    for y in range(h):
        for x in range(w):
            t = ((x * dx + y * dy) - lo) / (hi - lo)
            px[x, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
    return base


def multi_gradient(size, stops, angle=90):
    w, h = size
    img = Image.new("RGBA", size)
    px = img.load()
    rad = math.radians(angle)
    dx, dy = math.cos(rad), math.sin(rad)
    lo = min(0, dx * w) + min(0, dy * h)
    hi = max(0, dx * w) + max(0, dy * h)
    for y in range(h):
        for x in range(w):
            t = ((x * dx + y * dy) - lo) / (hi - lo)
            for i in range(len(stops) - 1):
                p0, c0 = stops[i]
                p1, c1 = stops[i + 1]
                if p0 <= t <= p1:
                    k = (t - p0) / (p1 - p0) if p1 > p0 else 0
                    px[x, y] = tuple(int(c0[j] + (c1[j] - c0[j]) * k) for j in range(3)) + (255,)
                    break
    return img


def fill(mask, paint):
    out = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    out.paste(paint, (0, 0), mask)
    return out


def sticker(art, mask, outline=22, shadow=True):
    """White sticker border, drop shadow and a gloss on top of the artwork."""
    border = mask.filter(ImageFilter.MaxFilter(outline * 2 + 1)).filter(ImageFilter.GaussianBlur(1.5))
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    if shadow:
        sh = Image.new("RGBA", (S, S), (20, 10, 40, 0))
        sh.putalpha(border.point(lambda v: int(v * 0.45)))
        sh = sh.filter(ImageFilter.GaussianBlur(14))
        canvas.alpha_composite(sh, (0, 10))
    white = Image.new("RGBA", (S, S), (255, 255, 255, 0))
    white.putalpha(border)
    canvas.alpha_composite(white)
    canvas.alpha_composite(art)
    # Gloss: a soft white ellipse over the upper part, clipped to the art.
    gloss = Image.new("L", (S, S), 0)
    ImageDraw.Draw(gloss).ellipse((S * 0.18, S * 0.06, S * 0.82, S * 0.5), fill=95)
    gloss = gloss.filter(ImageFilter.GaussianBlur(18))
    gloss = ImageChops.multiply(gloss, mask)
    shine = Image.new("RGBA", (S, S), (255, 255, 255, 0))
    shine.putalpha(gloss)
    canvas.alpha_composite(shine)
    return canvas


def sparkle(draw, cx, cy, r, color=(255, 255, 255, 255)):
    pts = []
    for i in range(8):
        a = math.pi / 4 * i - math.pi / 2
        rr = r if i % 2 == 0 else r * 0.22
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    draw.polygon(pts, fill=color)


def star_points(cx, cy, r_out, r_in, n=5, rot=-90):
    pts = []
    for i in range(n * 2):
        a = math.radians(rot + 180 / n * i)
        r = r_out if i % 2 == 0 else r_in
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


def heart_points(cx, cy, scale):
    pts = []
    for i in range(240):
        t = 2 * math.pi * i / 240
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * scale, cy - y * scale))
    return pts


# region badges

def badge_crown():
    mask = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    d.polygon([(96, 380), (80, 170), (178, 270), (256, 120), (334, 270), (432, 170), (416, 380)], fill=255)
    d.rounded_rectangle((92, 350, 420, 410), 18, fill=255)
    for x, y in [(80, 160), (256, 108), (432, 160)]:
        d.ellipse((x - 26, y - 26, x + 26, y + 26), fill=255)
    art = fill(mask, multi_gradient((S, S), [(0, (255, 236, 140)), (0.55, (255, 190, 40)), (1, (214, 120, 0))]))
    a = ImageDraw.Draw(art)
    for x, c in [(176, (139, 92, 246)), (256, (34, 211, 238)), (336, (236, 72, 153))]:
        a.ellipse((x - 24, 352, x + 24, 400), fill=c + (255,))
        a.ellipse((x - 12, 360, x + 2, 374), fill=(255, 255, 255, 200))
    sparkle(a, 256, 108, 20)
    return sticker(art, mask)


def badge_diamond():
    mask = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    top = [(150, 130), (362, 130), (440, 220), (256, 430), (72, 220)]
    d.polygon(top, fill=255)
    art = fill(mask, multi_gradient((S, S), [(0, (165, 243, 252)), (0.5, (139, 92, 246)), (1, (88, 28, 135))], 60))
    a = ImageDraw.Draw(art)
    line = (255, 255, 255, 150)
    for seg in [((72, 220), (440, 220)), ((150, 130), (200, 220)), ((362, 130), (312, 220)),
                ((256, 130), (200, 220)), ((256, 130), (312, 220)), ((200, 220), (256, 430)),
                ((312, 220), (256, 430))]:
        a.line(seg, fill=line, width=6)
    a.polygon([(150, 130), (256, 130), (200, 220), (72, 220)], fill=(255, 255, 255, 70))
    sparkle(a, 380, 120, 34)
    return sticker(art, mask)


def badge_verified():
    mask = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    pts = []
    for i in range(720):
        a = 2 * math.pi * i / 720
        r = 190 + 16 * math.cos(12 * a)
        pts.append((256 + math.cos(a) * r, 256 + math.sin(a) * r))
    d.polygon(pts, fill=255)
    art = fill(mask, multi_gradient((S, S), [(0, (255, 64, 129)), (0.35, (139, 92, 246)),
                                             (0.7, (34, 211, 238)), (1, (52, 211, 153))], 45))
    a = ImageDraw.Draw(art)
    a.line([(170, 262), (232, 324), (348, 196)], fill=(255, 255, 255, 255), width=46, joint="curve")
    for x, y in [(170, 262), (348, 196)]:
        a.ellipse((x - 23, y - 23, x + 23, y + 23), fill=(255, 255, 255, 255))
    return sticker(art, mask)


def badge_star():
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).polygon(star_points(256, 270, 215, 100), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(6)).point(lambda v: 255 if v > 128 else 0)
    art = fill(mask, multi_gradient((S, S), [(0, (255, 240, 120)), (0.5, (255, 170, 0)), (1, (255, 72, 0))]))
    a = ImageDraw.Draw(art)
    a.ellipse((196, 230, 226, 270), fill=(60, 30, 0, 255))
    a.ellipse((286, 230, 316, 270), fill=(60, 30, 0, 255))
    a.arc((210, 250, 302, 320), 20, 160, fill=(60, 30, 0, 255), width=10)
    a.ellipse((176, 280, 206, 300), fill=(255, 120, 120, 160))
    a.ellipse((306, 280, 336, 300), fill=(255, 120, 120, 160))
    return sticker(art, mask)


def badge_bolt():
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).polygon([(300, 50), (120, 290), (238, 290), (196, 462), (392, 210), (272, 210), (330, 50)],
                                 fill=255)
    art = fill(mask, multi_gradient((S, S), [(0, (255, 251, 160)), (0.5, (255, 214, 0)), (1, (255, 128, 0))]))
    a = ImageDraw.Draw(art)
    sparkle(a, 140, 140, 30, (255, 255, 255, 255))
    return sticker(art, mask)


def badge_heart():
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).polygon(heart_points(256, 250, 12.5), fill=255)
    art = fill(mask, multi_gradient((S, S), [(0, (255, 160, 200)), (0.5, (255, 72, 140)), (1, (200, 20, 90))]))
    a = ImageDraw.Draw(art)
    sparkle(a, 340, 160, 34)
    sparkle(a, 170, 300, 18)
    return sticker(art, mask)


def badge_pumpkin():
    mask = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    for cx, rw in [(160, 110), (352, 110), (210, 120), (302, 120), (256, 120)]:
        d.ellipse((cx - rw, 150, cx + rw, 440), fill=255)
    d.rounded_rectangle((236, 80, 280, 170), 14, fill=255)
    art = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    body = multi_gradient((S, S), [(0, (255, 170, 60)), (1, (210, 80, 0))])
    art.alpha_composite(fill(mask, body))
    a = ImageDraw.Draw(art)
    for cx, rw in [(160, 110), (352, 110), (210, 120), (302, 120)]:
        a.ellipse((cx - rw, 150, cx + rw, 440), outline=(170, 60, 0, 255), width=6)
    a.rounded_rectangle((236, 80, 280, 170), 14, fill=(74, 122, 38, 255))
    face = (255, 230, 90, 255)
    a.polygon([(160, 280), (230, 280), (195, 215)], fill=face)
    a.polygon([(282, 280), (352, 280), (317, 215)], fill=face)
    a.polygon([(150, 320), (190, 350), (225, 325), (256, 360), (288, 325), (322, 350), (362, 320),
               (330, 400), (256, 415), (182, 400)], fill=face)
    return sticker(art, mask)


def badge_ghost():
    mask = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((116, 70, 396, 350), fill=255)
    d.rectangle((116, 210, 396, 400), fill=255)
    for i in range(4):
        x = 116 + i * 70
        d.ellipse((x, 365, x + 70, 445), fill=255)
    art = fill(mask, multi_gradient((S, S), [(0, (255, 255, 255)), (1, (214, 206, 255))]))
    a = ImageDraw.Draw(art)
    a.ellipse((190, 190, 236, 250), fill=(40, 24, 70, 255))
    a.ellipse((276, 190, 322, 250), fill=(40, 24, 70, 255))
    a.ellipse((202, 198, 216, 214), fill=(255, 255, 255, 255))
    a.ellipse((288, 198, 302, 214), fill=(255, 255, 255, 255))
    a.ellipse((236, 270, 276, 310), fill=(40, 24, 70, 255))
    a.ellipse((160, 255, 196, 275), fill=(255, 150, 190, 200))
    a.ellipse((316, 255, 352, 275), fill=(255, 150, 190, 200))
    return sticker(art, mask)


def badge_sakura():
    mask = Image.new("L", (S, S), 0)
    petal = Image.new("L", (S, S), 0)
    pd = ImageDraw.Draw(petal)
    pd.ellipse((186, 70, 326, 270), fill=255)
    pd.polygon([(236, 70), (256, 110), (276, 70)], fill=0)
    for i in range(5):
        mask = ImageChops.lighter(mask, petal.rotate(-72 * i, center=(256, 256)))
    art = fill(mask, Image.radial_gradient("L").resize((S, S)).convert("RGBA"))
    pink = multi_gradient((S, S), [(0, (255, 214, 232)), (1, (255, 120, 180))])
    art = fill(mask, pink)
    a = ImageDraw.Draw(art)
    a.ellipse((216, 216, 296, 296), fill=(255, 210, 90, 255))
    for i in range(5):
        ang = math.radians(-90 + 72 * i)
        a.line([(256, 256), (256 + math.cos(ang) * 90, 256 + math.sin(ang) * 90)], fill=(236, 72, 153, 255), width=6)
        a.ellipse((256 + math.cos(ang) * 90 - 10, 256 + math.sin(ang) * 90 - 10,
                   256 + math.cos(ang) * 90 + 10, 256 + math.sin(ang) * 90 + 10), fill=(236, 72, 153, 255))
    return sticker(art, mask)


def badge_comet():
    mask = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    # Three tapered streaks behind a round head.
    d.polygon([(70, 90), (300, 210), (250, 300)], fill=255)
    d.polygon([(120, 60), (330, 200), (300, 230)], fill=255)
    d.polygon([(50, 170), (260, 270), (240, 320)], fill=255)
    d.ellipse((230, 190, 430, 390), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(8)).point(lambda v: 255 if v > 128 else 0)
    art = fill(mask, multi_gradient((S, S), [(0, (34, 211, 238)), (0.55, (139, 92, 246)), (1, (255, 64, 129))], 35))
    head = Image.new("L", (S, S), 0)
    ImageDraw.Draw(head).ellipse((250, 210, 410, 370), fill=255)
    art.alpha_composite(fill(head, multi_gradient((S, S), [(0, (255, 255, 230)), (0.6, (255, 220, 90)),
                                                           (1, (255, 150, 40))], 45)))
    a = ImageDraw.Draw(art)
    sparkle(a, 330, 290, 46, (255, 255, 255, 230))
    sparkle(a, 150, 330, 24)
    return sticker(art, mask)


def badge_flame():
    mask = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    pts = []
    for i in range(361):
        t = math.radians(i)
        r = 170 * (1 - math.sin(t)) ** 0.5 if True else 0
        x = 256 + r * math.cos(t) * 0.9
        y = 300 + r * math.sin(t)
        pts.append((x, y))
    d.polygon([(256, 50), (340, 180), (380, 300), (330, 430), (182, 430), (132, 300), (180, 200), (230, 230)],
              fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(22)).point(lambda v: 255 if v > 128 else 0)
    art = fill(mask, multi_gradient((S, S), [(0, (255, 214, 0)), (0.5, (255, 112, 0)), (1, (230, 20, 60))]))
    inner = Image.new("L", (S, S), 0)
    ImageDraw.Draw(inner).polygon([(256, 200), (310, 300), (296, 400), (216, 400), (202, 310)], fill=255)
    inner = inner.filter(ImageFilter.GaussianBlur(16)).point(lambda v: 255 if v > 128 else 0)
    art.alpha_composite(fill(inner, multi_gradient((S, S), [(0, (255, 255, 200)), (1, (255, 200, 40))])))
    return sticker(art, mask)


BADGES = [
    ("crown", "Корона", badge_crown),
    ("verified", "Галочка Prism", badge_verified),
    ("diamond", "Алмаз", badge_diamond),
    ("star", "Звезда", badge_star),
    ("bolt", "Молния", badge_bolt),
    ("heart", "Сердечко", badge_heart),
    ("flame", "Огонь", badge_flame),
    ("comet", "Комета", badge_comet),
    ("pumpkin", "Тыква", badge_pumpkin),
    ("ghost", "Призрак", badge_ghost),
    ("sakura", "Сакура", badge_sakura),
]

# endregion


def preview(images, out):
    font_path = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
    title = ImageFont.truetype(font_path, 44)
    nick = ImageFont.truetype(font_path, 34)
    small = ImageFont.truetype("/usr/share/fonts/opentype/inter/Inter-Medium.otf", 24)
    cols, cell_w, cell_h = 3, 420, 300
    rows = math.ceil(len(images) / cols)
    sheet = Image.new("RGBA", (cols * cell_w + 40, rows * cell_h + 120), (14, 12, 24, 255))
    d = ImageDraw.Draw(sheet)
    d.text((30, 30), "Prism: стикеры у ника", font=title, fill=(255, 255, 255))
    for i, (key, label, img) in enumerate(images):
        x = 20 + (i % cols) * cell_w
        y = 110 + (i // cols) * cell_h
        d.rounded_rectangle((x + 10, y + 10, x + cell_w - 10, y + cell_h - 10), 28, fill=(30, 27, 48, 255))
        big = img.resize((150, 150), Image.LANCZOS)
        sheet.alpha_composite(big, (x + (cell_w - 150) // 2, y + 22))
        # How it looks next to a nickname at real size.
        name = "meteotok"
        tw = d.textlength(name, font=nick)
        nx = x + (cell_w - tw - 46) // 2
        d.text((nx, y + 190), name, font=nick, fill=(255, 255, 255))
        sheet.alpha_composite(img.resize((40, 40), Image.LANCZOS), (int(nx + tw + 6), y + 192))
        lw = d.textlength(label, font=small)
        d.text((x + (cell_w - lw) // 2, y + 245), label, font=small, fill=(170, 165, 200))
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
