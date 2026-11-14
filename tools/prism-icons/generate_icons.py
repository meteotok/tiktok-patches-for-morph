from PIL import Image, ImageDraw, ImageFilter, ImageChops
import math, os, sys
S = 4  # supersample
OUT = sys.argv[1] if len(sys.argv) > 1 else '/root/icons/out'
os.makedirs(OUT, exist_ok=True)

def note_mask(size, scale=1.0, cx=0.5, cy=0.5):
    """TikTok-like 'd' note glyph as an L mask, glyph box centered at (cx,cy) of width scale*size."""
    m = Image.new('L', (size, size), 0)
    d = ImageDraw.Draw(m)
    b = size * scale
    ox = cx * size - b / 2
    oy = cy * size - b / 2
    P = lambda x, y: (ox + x * b, oy + y * b)
    # bowl ring
    r_out, r_in = 0.20, 0.095
    bx, by = 0.36, 0.67
    d.ellipse([P(bx - r_out, by - r_out), P(bx + r_out, by + r_out)], fill=255)
    d.ellipse([P(bx - r_in, by - r_in), P(bx + r_in, by + r_in)], fill=0)
    # stem
    sx0, sx1 = bx + r_out - 0.115, bx + r_out
    d.rectangle([P(sx0, 0.10), P(sx1, by)], fill=255)
    # hook: quarter annulus to the right of the stem top
    hx, hy = sx1, 0.10
    R_out, R_in = 0.30, 0.185
    hook = Image.new('L', (size, size), 0)
    hd = ImageDraw.Draw(hook)
    hd.pieslice([P(hx - R_out + 0.0, hy - R_out + 0.30 - 0.30), P(hx + R_out, hy + R_out)], 0, 90, fill=0)
    # build hook as annulus sector from angle 270..360 centered at (sx0, hy+R_out)
    hook = Image.new('L', (size, size), 0)
    hd = ImageDraw.Draw(hook)
    ccx, ccy = sx0, hy + R_out
    hd.pieslice([P(ccx - R_out, ccy - R_out), P(ccx + R_out, ccy + R_out)], 270, 360, fill=255)
    hd.ellipse([P(ccx - R_in, ccy - R_in), P(ccx + R_in, ccy + R_in)], fill=0)
    # keep only right of stem left edge
    m = ImageChops.lighter(m, hook)
    return m

def gradient(size, c1, c2, angle=45):
    w = h = size
    base = Image.new('RGB', (w, h), c1)
    top = Image.new('RGB', (w, h), c2)
    mask = Image.new('L', (w, h))
    md = mask.load()
    a = math.radians(angle)
    dx, dy = math.cos(a), math.sin(a)
    vals = []
    for y in range(h):
        for x in range(w):
            t = ((x / w - 0.5) * dx + (y / h - 0.5) * dy) / (abs(dx) * 0.5 + abs(dy) * 0.5) * 0.5 + 0.5
            md[x, y] = int(max(0, min(1, t)) * 255)
    return Image.composite(top, base, mask)

def radial_glow(size, color, center, radius, alpha=160):
    g = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    cx, cy = center[0] * size, center[1] * size
    r = radius * size
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (alpha,))
    return g.filter(ImageFilter.GaussianBlur(r * 0.55))

def glyph_layers(size, front, left, right, scale=0.56, offset=0.022, glow=None):
    """Chromatic note: left-shifted, right-shifted color layers and a front layer."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    o = offset
    for col, dx, dy in ((left, -o, -o), (right, o, o)):
        m = note_mask(size, scale, 0.5 + dx, 0.5 + dy)
        layer = Image.new('RGBA', (size, size), col + (255,))
        img = Image.alpha_composite(img, Image.composite(layer, Image.new('RGBA', (size, size), (0, 0, 0, 0)), m))
    m = note_mask(size, scale, 0.5, 0.5)
    if glow:
        gl = Image.new('RGBA', (size, size), glow + (0,))
        gm = m.filter(ImageFilter.GaussianBlur(size * 0.03))
        gl.putalpha(gm.point(lambda v: int(v * 0.9)))
        img = Image.alpha_composite(gl, img)
    layer = Image.new('RGBA', (size, size), front + (255,))
    img = Image.alpha_composite(img, Image.composite(layer, Image.new('RGBA', (size, size), (0, 0, 0, 0)), m))
    return img

def stars(size, n, seed, color=(255, 255, 255)):
    import random
    rnd = random.Random(seed)
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for _ in range(n):
        x, y = rnd.random() * size, rnd.random() * size
        r = rnd.choice([1, 1, 1.5, 2, 3]) * size / 432
        a = rnd.randint(90, 230)
        d.ellipse([x - r, y - r, x + r, y + r], fill=color + (a,))
    return img

def prismr(size, x0, y0, x1, y1, color=(255, 255, 255), width=0.02):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    steps = 60
    d = ImageDraw.Draw(img)
    for i in range(steps):
        t = i / steps
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        r = width * size * (0.2 + 0.8 * t)
        a = int(255 * t ** 1.5)
        d.ellipse([x * size - r, y * size - r, x * size + r, y * size + r], fill=color + (a,))
    return img.filter(ImageFilter.GaussianBlur(size * 0.004))

def heart(size, cx, cy, r, color, alpha=255):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pts = []
    for i in range(200):
        t = math.pi * 2 * i / 200
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append(((cx + x / 17 * r) * size, (cy + y / 17 * r) * size))
    d.polygon(pts, fill=color + (alpha,))
    return img

def bat(size, cx, cy, w, color=(20, 8, 30), alpha=230):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = w * size
    X, Y = cx * size, cy * size
    pts = [(-1, 0), (-0.75, -0.35), (-0.6, -0.05), (-0.4, -0.2), (-0.2, 0.0), (-0.1, -0.22), (0, -0.1), (0.1, -0.22), (0.2, 0), (0.4, -0.2), (0.6, -0.05), (0.75, -0.35), (1, 0), (0.55, 0.15), (0.3, 0.05), (0.1, 0.25), (0, 0.15), (-0.1, 0.25), (-0.3, 0.05), (-0.55, 0.15)]
    d.polygon([(X + px * s / 2, Y + py * s / 2) for px, py in pts], fill=color + (alpha,))
    return img

def petal(size, cx, cy, r, ang, color, alpha=200):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([(cx - r) * size, (cy - r * 0.55) * size, (cx + r) * size, (cy + r * 0.55) * size], fill=color + (alpha,))
    return img.rotate(ang, center=(cx * size, cy * size))

THEMES = {
    'prism': dict(bg=((30, 16, 72), (6, 8, 30)), angle=60, front=(255, 255, 255), left=(34, 211, 238), right=(168, 85, 247), glow=(139, 92, 246)),
    'halloween': dict(bg=((60, 18, 80), (12, 4, 18)), angle=70, front=(255, 150, 40), left=(255, 220, 90), right=(142, 45, 226), glow=(255, 110, 0)),
    'femboy': dict(bg=((255, 182, 222), (170, 216, 255)), angle=45, front=(255, 255, 255), left=(255, 120, 190), right=(110, 180, 255), glow=(255, 255, 255)),
    'neon': dict(bg=((10, 14, 12), (0, 0, 0)), angle=60, front=(240, 255, 240), left=(57, 255, 20), right=(255, 0, 229), glow=(57, 255, 20)),
    'ocean': dict(bg=((0, 120, 200), (2, 22, 52)), angle=70, front=(255, 255, 255), left=(0, 255, 200), right=(0, 150, 255), glow=(0, 200, 255)),
    'gold': dict(bg=((40, 32, 14), (6, 5, 2)), angle=60, front=(255, 222, 120), left=(255, 245, 190), right=(200, 110, 20), glow=(255, 190, 40)),
    'sakura': dict(bg=((255, 236, 244), (250, 200, 225)), angle=50, front=(236, 72, 153), left=(255, 170, 210), right=(168, 85, 247), glow=(255, 255, 255)),
}

def background(key, size):
    t = THEMES[key]
    bg = gradient(size, t['bg'][0], t['bg'][1], t['angle']).convert('RGBA')
    if key == 'prism':
        bg = Image.alpha_composite(bg, radial_glow(size, (139, 92, 246), (0.25, 0.2), 0.35, 150))
        bg = Image.alpha_composite(bg, stars(size, 70, 7))
        bg = Image.alpha_composite(bg, prismr(size, 0.95, 0.05, 0.70, 0.30, (200, 240, 255), 0.012))
        bg = Image.alpha_composite(bg, prismr(size, 0.30, 0.85, 0.12, 0.98, (200, 180, 255), 0.008))
    elif key == 'halloween':
        bg = Image.alpha_composite(bg, radial_glow(size, (255, 230, 170), (0.78, 0.22), 0.13, 255))
        moon = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        ImageDraw.Draw(moon).ellipse([0.70 * size, 0.14 * size, 0.86 * size, 0.30 * size], fill=(255, 236, 190, 255))
        bg = Image.alpha_composite(bg, moon)
        bg = Image.alpha_composite(bg, bat(size, 0.22, 0.24, 0.16))
        bg = Image.alpha_composite(bg, bat(size, 0.80, 0.78, 0.11))
        bg = Image.alpha_composite(bg, radial_glow(size, (255, 100, 0), (0.5, 1.0), 0.35, 120))
    elif key == 'femboy':
        bg = Image.alpha_composite(bg, radial_glow(size, (255, 255, 255), (0.3, 0.25), 0.3, 140))
        for (x, y, r, a) in ((0.20, 0.22, 0.06, 200), (0.82, 0.30, 0.045, 180), (0.78, 0.80, 0.07, 190), (0.18, 0.80, 0.04, 170)):
            bg = Image.alpha_composite(bg, heart(size, x, y, r, (255, 255, 255), a))
        bg = Image.alpha_composite(bg, stars(size, 30, 3))
    elif key == 'neon':
        grid = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        gd = ImageDraw.Draw(grid)
        for i in range(0, size, size // 12):
            gd.line([(i, 0), (i, size)], fill=(57, 255, 20, 28), width=max(1, size // 300))
            gd.line([(0, i), (size, i)], fill=(255, 0, 229, 22), width=max(1, size // 300))
        bg = Image.alpha_composite(bg, grid)
        bg = Image.alpha_composite(bg, radial_glow(size, (255, 0, 229), (0.85, 0.15), 0.3, 110))
        bg = Image.alpha_composite(bg, radial_glow(size, (57, 255, 20), (0.15, 0.9), 0.3, 90))
    elif key == 'ocean':
        bg = Image.alpha_composite(bg, radial_glow(size, (0, 255, 220), (0.2, 0.15), 0.35, 120))
        waves = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        wd = ImageDraw.Draw(waves)
        for k, yy in enumerate((0.80, 0.88, 0.96)):
            pts = [(x, (yy + 0.025 * math.sin(x / size * math.pi * 4 + k)) * size) for x in range(0, size + 1, 4)]
            wd.line(pts, fill=(180, 240, 255, 70 - k * 15), width=max(2, size // 90))
        bg = Image.alpha_composite(bg, waves)
        bg = Image.alpha_composite(bg, stars(size, 25, 11, (200, 255, 255)))
    elif key == 'gold':
        bg = Image.alpha_composite(bg, radial_glow(size, (255, 200, 60), (0.5, 0.45), 0.4, 70))
        bg = Image.alpha_composite(bg, stars(size, 40, 5, (255, 220, 140)))
    elif key == 'sakura':
        bg = Image.alpha_composite(bg, radial_glow(size, (255, 255, 255), (0.3, 0.2), 0.3, 160))
        for (x, y, r, a) in ((0.18, 0.2, 0.05, 30), (0.84, 0.26, 0.04, -40), (0.8, 0.84, 0.055, 70), (0.2, 0.82, 0.04, 10), (0.62, 0.12, 0.03, 120)):
            bg = Image.alpha_composite(bg, petal(size, x, y, r, a, (244, 114, 182), 190))
    return bg

def foreground(key, size):
    t = THEMES[key]
    return glyph_layers(size, t['front'], t['left'], t['right'], scale=0.50, offset=0.016, glow=t['glow'])

def render(key, out):
    big = 432 * S
    bg = background(key, big)
    fg = foreground(key, big)
    bg.resize((432, 432), Image.LANCZOS).convert('RGB').save(f'{out}/prism_icon_{key}_background.png', optimize=True)
    fg.resize((432, 432), Image.LANCZOS).save(f'{out}/prism_icon_{key}_foreground.png', optimize=True)
    # Legacy square icon with rounded corners (pre-Android 8 launchers and previews).
    combo = Image.alpha_composite(bg, fg).resize((192 * S, 192 * S), Image.LANCZOS) if False else None
    leg = 192 * S
    bg2 = background(key, leg)
    fg2 = glyph_layers(leg, t_front(key), THEMES[key]['left'], THEMES[key]['right'], scale=0.70, offset=0.022, glow=THEMES[key]['glow'])
    img = Image.alpha_composite(bg2, fg2)
    mask = Image.new('L', (leg, leg), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, leg - 1, leg - 1], radius=int(leg * 0.23), fill=255)
    img.putalpha(mask)
    img.resize((192, 192), Image.LANCZOS).save(f'{out}/prism_icon_{key}.png', optimize=True)

def t_front(key):
    return THEMES[key]['front']

keys = sys.argv[2:] or list(THEMES)
for k in keys:
    render(k, OUT)
    print('ok', k)
