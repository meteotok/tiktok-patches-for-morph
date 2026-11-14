"""Renders Prism's realistic theme decorations as PNGs (3x density).

Each theme has a top-right piece (anchored to the screen's top-right corner) and a bottom-left
piece (anchored to the bottom-left corner), plus small sprites the app animates (spider, bubble,
petal, sparkle). Run: python3 generate_decor.py <out_dir> [preview.png]
"""
import math
import random
import sys

import cairo
import numpy as np
from scipy.ndimage import gaussian_filter

S = 3.0  # pixels per dp


def surface(w, h):
    s = cairo.ImageSurface(cairo.FORMAT_ARGB32, int(w), int(h))
    return s, cairo.Context(s)


def to_array(s):
    s.flush()
    a = np.ndarray((s.get_height(), s.get_width(), 4), np.uint8, s.get_data(), strides=(s.get_stride(), 4, 1))
    return a.astype(np.float32) / 255.0  # premultiplied BGRA


def from_array(a):
    h, w = a.shape[:2]
    s = cairo.ImageSurface(cairo.FORMAT_ARGB32, w, h)
    buf = np.ndarray((h, w, 4), np.uint8, s.get_data(), strides=(s.get_stride(), 4, 1))
    buf[:] = np.clip(a * 255 + 0.5, 0, 255).astype(np.uint8)
    s.mark_dirty()
    return s


def blur(s, sigma):
    a = to_array(s)
    for c in range(4):
        a[..., c] = gaussian_filter(a[..., c], sigma)
    return from_array(a)


def over(dst_ctx, src, alpha=1.0, dx=0, dy=0):
    dst_ctx.save()
    dst_ctx.set_source_surface(src, dx, dy)
    dst_ctx.paint_with_alpha(alpha)
    dst_ctx.restore()


def save(s, path, fade=None):
    """fade: the sides away from the screen edge, e.g. "lb" for a top-right piece."""
    if fade:
        a = to_array(s)
        h, w = a.shape[:2]
        mask = np.ones((h, w), np.float32)
        ramp_x = np.clip(np.arange(w) / (0.18 * w), 0, 1)
        ramp_y = np.clip(np.arange(h) / (0.18 * h), 0, 1)
        if "l" in fade:
            mask *= ramp_x[None, :]
        if "r" in fade:
            mask *= ramp_x[::-1][None, :]
        if "t" in fade:
            mask *= ramp_y[:, None]
        if "b" in fade:
            mask *= ramp_y[::-1][:, None]
        mask = mask * mask * (3 - 2 * mask)
        a *= mask[..., None]
        s = from_array(a)
    s.write_to_png(path)


# ---------------------------------------------------------------- spider web

class Strands:
    """Collects web threads, then draws them with a soft shadow, body and highlight for depth."""

    def __init__(self, w, h, seed):
        self.w, self.h = w, h
        self.items = []  # (path points, width, depth)
        self.rng = random.Random(seed)
        self.drops = []

    def line(self, pts, width, depth=None):
        self.items.append((pts, width, self.rng.uniform(0.55, 1.0) if depth is None else depth))

    def sag(self, a, b, amount, toward=None, steps=12):
        """Points along a thread from a to b that sags by `amount` (fraction of length)."""
        ax, ay = a
        bx, by = b
        length = math.hypot(bx - ax, by - ay)
        mx, my = (ax + bx) / 2, (ay + by) / 2
        if toward is None:
            nx, ny = 0, 1  # gravity
        else:
            nx, ny = toward[0] - mx, toward[1] - my
            n = math.hypot(nx, ny) or 1
            nx, ny = nx / n, ny / n
        cx, cy = mx + nx * length * amount, my + ny * length * amount
        pts = []
        for i in range(steps + 1):
            t = i / steps
            x = (1 - t) ** 2 * ax + 2 * (1 - t) * t * cx + t * t * bx
            y = (1 - t) ** 2 * ay + 2 * (1 - t) * t * cy + t * t * by
            pts.append((x, y))
        return pts

    def _stroke(self, ctx, pts, dx=0, dy=0):
        ctx.move_to(pts[0][0] + dx, pts[0][1] + dy)
        for x, y in pts[1:]:
            ctx.line_to(x + dx, y + dy)
        ctx.stroke()

    def render(self, base=(0.80, 0.80, 0.84)):
        w, h = self.w, self.h
        shadow, sc = surface(w, h)
        body, bc = surface(w, h)
        light, lc = surface(w, h)
        for c in (sc, bc, lc):
            c.set_line_cap(cairo.LINE_CAP_ROUND)
            c.set_line_join(cairo.LINE_JOIN_ROUND)
        for pts, width, depth in self.items:
            # Shadow cast on the screen behind the web: further strands cast it further away.
            sc.set_source_rgba(0, 0, 0, 0.55 * depth)
            sc.set_line_width(width * 1.8)
            self._stroke(sc, pts, 2.5 * S * depth, 3.5 * S * depth)
            r, g, b = base
            k = 0.55 + 0.35 * depth
            bc.set_source_rgba(r * k, g * k, b * k, 0.35 + 0.4 * depth)
            bc.set_line_width(width * (0.7 + 0.3 * depth))
            self._stroke(bc, pts)
            lc.set_source_rgba(1, 1, 1, 0.35 * depth)
            lc.set_line_width(max(0.4, width * 0.3))
            self._stroke(lc, pts, -0.35 * S * 0.4, -0.35 * S * 0.4)
        out, oc = surface(w, h)
        over(oc, blur(shadow, 1.6 * S), 0.8)
        over(oc, blur(body, 0.25 * S))
        over(oc, light, 0.9)
        for x, y, r in self.drops:
            dew(oc, x, y, r)
        return out


def dew(ctx, x, y, r):
    """A tiny water droplet hanging on a thread."""
    g = cairo.RadialGradient(x - r * 0.35, y - r * 0.35, r * 0.1, x, y, r)
    g.add_color_stop_rgba(0, 1, 1, 1, 0.95)
    g.add_color_stop_rgba(0.35, 0.75, 0.82, 0.9, 0.55)
    g.add_color_stop_rgba(1, 0.35, 0.4, 0.5, 0.35)
    ctx.set_source(g)
    ctx.arc(x, y, r, 0, 2 * math.pi)
    ctx.fill()
    ctx.set_source_rgba(1, 1, 1, 0.9)
    ctx.arc(x - r * 0.35, y - r * 0.4, r * 0.22, 0, 2 * math.pi)
    ctx.fill()


def ray_polygon(hx, hy, angle, poly):
    dx, dy = math.cos(angle), math.sin(angle)
    best = None
    for i in range(len(poly)):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % len(poly)]
        ex, ey = x2 - x1, y2 - y1
        den = dx * ey - dy * ex
        if abs(den) < 1e-9:
            continue
        t = ((x1 - hx) * ey - (y1 - hy) * ex) / den
        u = ((x1 - hx) * dy - (y1 - hy) * dx) / den
        if t > 0 and 0 <= u <= 1 and (best is None or t < best):
            best = t
    return (hx + dx * best, hy + dy * best) if best else None


def orb_web(st, hub, frame, spokes, rings, broken=0.07, first=0.08):
    hx, hy = hub
    rng = st.rng
    # Frame threads between anchor points, slightly sagging.
    for i in range(len(frame)):
        a, b = frame[i], frame[(i + 1) % len(frame)]
        st.line(st.sag(a, b, 0.03), 1.6 * S * 0.45, 0.9)
    ends = []
    start = rng.uniform(0, math.pi * 2)
    for i in range(spokes):
        angle = start + i * 2 * math.pi / spokes + rng.uniform(-0.12, 0.12)
        p = ray_polygon(hx, hy, angle, frame)
        if p is None:
            continue
        ends.append((angle, p))
        st.line(st.sag((hx, hy), p, rng.uniform(-0.015, 0.015), steps=6), 1.3 * S * 0.42, rng.uniform(0.6, 1.0))
    ends.sort()
    # Capture spiral: rings get further apart outward and sag between spokes.
    for k in range(rings):
        f = first + (1 - first) * ((k + 1) / rings) ** 1.15 * 0.93
        f += rng.uniform(-0.012, 0.012)
        for i in range(len(ends)):
            a = ends[i][1]
            b = ends[(i + 1) % len(ends)][1]
            if rng.random() < broken * (0.4 + k / rings):
                continue
            pa = (hx + (a[0] - hx) * f, hy + (a[1] - hy) * f)
            pb = (hx + (b[0] - hx) * f, hy + (b[1] - hy) * f)
            st.line(st.sag(pa, pb, rng.uniform(0.06, 0.14), toward=(hx, hy), steps=8), 0.95 * S * 0.42)
            if rng.random() < 0.05:
                t = rng.uniform(0.2, 0.8)
                st.drops.append((pa[0] + (pb[0] - pa[0]) * t, pa[1] + (pb[1] - pa[1]) * t + 1.5,
                                 rng.uniform(1.0, 1.8) * S))
    # Dense hub.
    for k in range(5):
        f = 0.012 + k * 0.012
        pts = []
        for angle, p in ends + ends[:1]:
            pts.append((hx + (p[0] - hx) * f, hy + (p[1] - hy) * f))
        st.line(pts, 1.1 * S * 0.42, 1.0)


def web_top_right(path):
    w, h = int(160 * S), int(178 * S)
    st = Strands(w, h, 7)
    hub = (0.66 * w, 0.30 * h)
    # Anchors: along the top edge, down the right edge, plus long lines pulled to the left
    # and down like a real web strung across a corner.
    frame = [(0.14 * w, 0.03 * h), (0.55 * w, 0.015 * h), (0.93 * w, 0.04 * h), (0.985 * w, 0.14 * h),
             (0.975 * w, 0.50 * h), (0.92 * w, 0.76 * h), (0.80 * w, 0.90 * h), (0.55 * w, 0.60 * h),
             (0.06 * w, 0.22 * h)]
    orb_web(st, hub, frame, spokes=15, rings=9, broken=0.14)
    # Frame threads tied to the top and right screen edges.
    for a, b in (((0.14 * w, 0.03 * h), (0.10 * w, 0.0)), ((0.55 * w, 0.015 * h), (0.52 * w, 0.0)),
                 ((0.93 * w, 0.04 * h), (0.98 * w, 0.0)), ((0.985 * w, 0.14 * h), (w - 1, 0.13 * h)),
                 ((0.975 * w, 0.50 * h), (w - 1, 0.52 * h))):
        st.line([a, b], 1.4 * S * 0.45, 0.9)
    # Guy lines from the frame to the screen edges.
    st.line(st.sag((0.06 * w, 0.22 * h), (0.0, 0.24 * h), 0.0), 1.4 * S * 0.45, 0.9)
    st.line(st.sag((0.80 * w, 0.90 * h), (0.84 * w, h - 1), 0.0), 1.4 * S * 0.45, 0.9)
    st.line(st.sag((0.55 * w, 0.60 * h), (0.0, 0.30 * h), 0.02), 1.1 * S * 0.45, 0.7)
    st.line(st.sag((0.92 * w, 0.76 * h), (0.76 * w, 0.995 * h), 0.01), 1.0 * S * 0.45, 0.6)
    s = st.render()
    save(s, path)
    return hub[0] / w, hub[1] / h


def web_bottom_left(path):
    w, h = int(200 * S), int(130 * S)
    st = Strands(w, h, 11)
    rng = st.rng
    # A small corner web low on the left edge.
    corner = (0.0, 0.22 * h)
    spokes = []
    for i in range(6):
        a = math.radians(8 + i * 13 + rng.uniform(-3, 3))
        r = rng.uniform(0.55, 0.68) * w * (0.8 if i in (0, 5) else 1)
        p = (corner[0] + math.cos(a) * r * 0.62, corner[1] + math.sin(a) * r * 0.85)
        spokes.append(p)
        st.line([corner, p], 1.6 * S * 0.42, 1.0)
    for k in range(1, 9):
        f = k / 9.2
        pts = [(corner[0] + (p[0] - corner[0]) * f, corner[1] + (p[1] - corner[1]) * f) for p in spokes]
        for a, b in zip(pts, pts[1:]):
            if rng.random() < 0.08:
                continue
            st.line(st.sag(a, b, 0.08, toward=corner, steps=6), 1.2 * S * 0.42, rng.uniform(0.8, 1.0))
    # Long torn strands drifting toward the bottom edge.
    for i in range(5):
        a = (rng.uniform(0.05, 0.4) * w, rng.uniform(0.45, 0.75) * h)
        b = (rng.uniform(0.55, 1.0) * w, rng.uniform(0.86, 1.0) * h)
        st.line(st.sag(a, b, rng.uniform(0.01, 0.04)), 1.4 * S * 0.42, rng.uniform(0.8, 1.0))
    st.line(st.sag((0.0, 0.6 * h), (0.42 * w, 0.96 * h), 0.03), 1.4 * S * 0.42, 0.9)
    s = st.render()
    save(s, path)


def spider(path):
    w, h = int(30 * S), int(34 * S)
    s, c = surface(w, h)
    cx = w / 2
    ab_y, ce_y = h * 0.34, h * 0.62  # abdomen on top: it hangs from its spinnerets, head down
    legs, lc = surface(w, h)
    lc.set_line_cap(cairo.LINE_CAP_ROUND)
    lc.set_line_join(cairo.LINE_JOIN_ROUND)
    leg_angles = [-60, -25, 15, 50]
    for side in (-1, 1):
        for i, a in enumerate(leg_angles):
            ang = math.radians(a)
            x0, y0 = cx + side * 1.2 * S, ce_y + (i - 1.5) * 0.9 * S
            knee = (x0 + side * math.cos(ang) * 5.0 * S, y0 + math.sin(ang) * 4.0 * S - 3.6 * S)
            ankle = (knee[0] + side * math.cos(ang) * 3.8 * S, knee[1] + math.sin(ang) * 4.5 * S + 2.6 * S)
            foot = (ankle[0] + side * math.cos(ang) * 1.6 * S, ankle[1] + math.sin(ang) * 2.0 * S + 2.2 * S)
            for width, col, dx in ((1.1 * S, (0.05, 0.04, 0.04, 1), 0), (0.3 * S, (0.6, 0.55, 0.5, 0.6), -0.25 * S)):
                for (a, b), k in (((((x0, y0), knee)), 1.0), ((knee, ankle), 0.75), ((ankle, foot), 0.5)):
                    lc.set_line_width(width * k)
                    lc.set_source_rgba(*col)
                    lc.move_to(a[0] + dx, a[1] + dx)
                    lc.line_to(b[0] + dx, b[1] + dx)
                    lc.stroke()
    over(c, blur(legs, 0.6 * S), 0.5, 1.2 * S, 1.6 * S)
    over(c, legs)
    # Pedipalps.
    c.set_source_rgba(0.06, 0.05, 0.05, 1)
    c.set_line_width(0.8 * S)
    for side in (-1, 1):
        c.move_to(cx + side * 0.6 * S, ce_y + 2.2 * S)
        c.line_to(cx + side * 1.5 * S, ce_y + 4.0 * S)
        c.stroke()
    for (y, rx, ry) in ((ce_y, 2.6 * S, 3.0 * S), (ab_y, 4.0 * S, 5.4 * S)):
        g = cairo.RadialGradient(cx - rx * 0.35, y - ry * 0.4, rx * 0.1, cx, y, max(rx, ry) * 1.1)
        g.add_color_stop_rgb(0, 0.42, 0.34, 0.30)
        g.add_color_stop_rgb(0.45, 0.13, 0.10, 0.09)
        g.add_color_stop_rgb(1, 0.02, 0.02, 0.02)
        c.save()
        c.translate(cx, y)
        c.scale(rx, ry)
        c.arc(0, 0, 1, 0, 2 * math.pi)
        c.restore()
        c.set_source(g)
        c.fill()
    # Faint markings and a glossy highlight on the abdomen.
    c.set_source_rgba(0.75, 0.62, 0.5, 0.35)
    for k in range(3):
        c.arc(cx, ab_y - 2.5 * S + k * 1.9 * S, 0.55 * S, 0, 2 * math.pi)
        c.fill()
    c.set_source_rgba(1, 1, 1, 0.55)
    c.save()
    c.translate(cx - 1.4 * S, ab_y - 2.6 * S)
    c.scale(1.3 * S, 0.7 * S)
    c.arc(0, 0, 1, 0, 2 * math.pi)
    c.restore()
    c.fill()
    save(s, path)
    return 0.5, (ab_y - 5.4 * S) / h  # where the thread attaches


# ---------------------------------------------------------------- other themes

def satin_band(ctx, x0, y0, x1, y1, base, light, dark):
    g = cairo.LinearGradient(x0, y0, x1, y1)
    for t, col in ((0, dark), (0.18, base), (0.34, light), (0.42, base), (0.62, dark), (0.8, base), (1, light)):
        g.add_color_stop_rgb(t, *col)
    return g


def bow(path):
    w, h = int(150 * S), int(150 * S)
    s, c = surface(w, h)
    base, light, dark = (0.96, 0.52, 0.72), (1.0, 0.86, 0.93), (0.70, 0.22, 0.45)
    shadow, sh = surface(w, h)
    cx, cy = w * 0.55, h * 0.36

    def loops(ctx, fill_fn):
        for side in (-1, 1):
            ctx.move_to(cx, cy)
            ctx.curve_to(cx + side * w * 0.18, cy - h * 0.30, cx + side * w * 0.46, cy - h * 0.20, cx + side * w * 0.40, cy)
            ctx.curve_to(cx + side * w * 0.44, cy + h * 0.16, cx + side * w * 0.20, cy + h * 0.14, cx, cy)
            ctx.close_path()
            fill_fn(ctx, side)
        for side in (-1, 1):
            ctx.move_to(cx - side * w * 0.02, cy + h * 0.02)
            ctx.curve_to(cx + side * w * 0.08, cy + h * 0.25, cx + side * w * 0.02, cy + h * 0.40, cx + side * w * 0.16, cy + h * 0.60)
            ctx.line_to(cx + side * w * 0.26, cy + h * 0.52)
            ctx.curve_to(cx + side * w * 0.16, cy + h * 0.40, cx + side * w * 0.18, cy + h * 0.22, cx + side * w * 0.06, cy)
            ctx.close_path()
            fill_fn(ctx, side)

    def shade(ctx, side):
        ctx.set_source(satin_band(ctx, cx, cy - h * 0.25, cx + side * w * 0.42, cy + h * 0.2, base, light, dark))
        ctx.fill_preserve()
        ctx.set_source_rgba(*dark, 0.6)
        ctx.set_line_width(0.6 * S)
        ctx.stroke()

    def flat(ctx, side):
        ctx.set_source_rgba(0, 0, 0, 0.6)
        ctx.fill()

    # Ribbon it hangs from.
    c.set_source(satin_band(c, cx - 3 * S, 0, cx + 3 * S, 0, base, light, dark))
    c.rectangle(cx - 2.2 * S, 0, 4.4 * S, cy)
    c.fill()
    loops(sh, flat)
    over(c, blur(shadow, 3 * S), 0.55, 2 * S, 4 * S)
    loops(c, shade)
    g = cairo.RadialGradient(cx - 2 * S, cy - 2 * S, 1, cx, cy, 9 * S)
    g.add_color_stop_rgb(0, *light)
    g.add_color_stop_rgb(0.5, *base)
    g.add_color_stop_rgb(1, *dark)
    c.set_source(g)
    c.save()
    c.translate(cx, cy)
    c.scale(8 * S, 9 * S)
    c.arc(0, 0, 1, 0, 2 * math.pi)
    c.restore()
    c.fill()
    # A few pearls on the knot.
    for i, (dx, dy) in enumerate(((-4, 13), (0, 15), (4, 13))):
        pearl(c, cx + dx * S, cy + dy * S, 2.2 * S)
    save(s, path)


def pearl(c, x, y, r):
    g = cairo.RadialGradient(x - r * 0.4, y - r * 0.4, r * 0.05, x, y, r)
    g.add_color_stop_rgb(0, 1, 1, 1)
    g.add_color_stop_rgb(0.5, 0.95, 0.9, 0.92)
    g.add_color_stop_rgb(1, 0.7, 0.62, 0.68)
    c.set_source(g)
    c.arc(x, y, r, 0, 2 * math.pi)
    c.fill()


def glossy_heart(c, x, y, r, base, dark):
    def shape():
        c.move_to(x, y + r)
        c.curve_to(x - r * 2.1, y - r * 0.2, x - r * 0.9, y - r * 1.7, x, y - r * 0.55)
        c.curve_to(x + r * 0.9, y - r * 1.7, x + r * 2.1, y - r * 0.2, x, y + r)
        c.close_path()
    g = cairo.RadialGradient(x - r * 0.5, y - r * 0.7, r * 0.1, x, y, r * 1.7)
    g.add_color_stop_rgb(0, 1, 0.85, 0.92)
    g.add_color_stop_rgb(0.35, *base)
    g.add_color_stop_rgb(1, *dark)
    shape()
    c.set_source(g)
    c.fill()
    c.set_source_rgba(1, 1, 1, 0.75)
    c.save()
    c.translate(x - r * 0.7, y - r * 0.65)
    c.rotate(-0.6)
    c.scale(r * 0.38, r * 0.18)
    c.arc(0, 0, 1, 0, 2 * math.pi)
    c.restore()
    c.fill()


def hearts(path):
    w, h = int(130 * S), int(110 * S)
    s, c = surface(w, h)
    shadow, sh = surface(w, h)
    spec = [(0.30, 0.62, 15, (0.95, 0.30, 0.55), (0.55, 0.05, 0.25)),
            (0.66, 0.70, 11, (0.98, 0.55, 0.75), (0.65, 0.18, 0.40)),
            (0.50, 0.33, 9, (0.80, 0.62, 0.98), (0.40, 0.25, 0.70))]
    for fx, fy, r, base, dark in spec:
        sh.set_source_rgba(0, 0, 0, 0.6)
        sh.arc(fx * w + 2 * S, fy * h + 4 * S, r * S * 1.05, 0, 2 * math.pi)
        sh.fill()
    over(c, blur(shadow, 4 * S), 0.5)
    for fx, fy, r, base, dark in spec:
        glossy_heart(c, fx * w, fy * h, r * S, base, dark)
    save(s, path)


def neon(path, shape, colors, fade):
    w, h = int(170 * S), int(150 * S)
    pts = [(fx * w, fy * h) for fx, fy in shape]
    s, c = surface(w, h)
    for color, offset in zip(colors, (0, 7 * S)):
        glow, gc = surface(w, h)
        for width, alpha in ((14 * S, 0.35), (6 * S, 0.6)):
            gc.set_line_width(width)
            gc.set_line_cap(cairo.LINE_CAP_ROUND)
            gc.set_line_join(cairo.LINE_JOIN_ROUND)
            gc.set_source_rgba(*color, alpha)
            gc.move_to(pts[0][0], pts[0][1] + offset)
            for x, y in pts[1:]:
                gc.line_to(x, y + offset)
            gc.stroke()
        over(c, blur(glow, 6 * S))
        # Glass tube: colored edge, white-hot core.
        for width, col in ((3.4 * S, color + (1,)), (1.3 * S, (1, 1, 1, 0.95))):
            c.set_line_width(width)
            c.set_line_cap(cairo.LINE_CAP_ROUND)
            c.set_line_join(cairo.LINE_JOIN_ROUND)
            c.set_source_rgba(*col)
            c.move_to(pts[0][0], pts[0][1] + offset)
            for x, y in pts[1:]:
                c.line_to(x, y + offset)
            c.stroke()
    save(s, path, fade)


def god_rays(path):
    w, h = int(220 * S), int(260 * S)
    s, c = surface(w, h)
    rays, rc = surface(w, h)
    rng = random.Random(3)
    for i in range(7):
        a = math.radians(105 + i * 9 + rng.uniform(-3, 3))
        spread = math.radians(rng.uniform(2, 4.5))
        rc.move_to(w, 0)
        rc.line_to(w + math.cos(a - spread) * w * 2, math.sin(a - spread) * w * 2)
        rc.line_to(w + math.cos(a + spread) * w * 2, math.sin(a + spread) * w * 2)
        rc.close_path()
        g = cairo.RadialGradient(w, 0, 0, w, 0, h * 1.05)
        g.add_color_stop_rgba(0, 0.75, 0.95, 1, 0.55)
        g.add_color_stop_rgba(1, 0.4, 0.8, 1, 0)
        rc.set_source(g)
        rc.fill()
    over(c, blur(rays, 3 * S))
    save(s, path, 'lb')


def bubble(path):
    r = 20 * S
    w = h = int(2 * r + 4)
    s, c = surface(w, h)
    x = y = w / 2
    g = cairo.RadialGradient(x, y, r * 0.55, x, y, r)
    g.add_color_stop_rgba(0, 0.7, 0.9, 1, 0.04)
    g.add_color_stop_rgba(0.8, 0.75, 0.92, 1, 0.22)
    g.add_color_stop_rgba(0.95, 0.9, 0.98, 1, 0.75)
    g.add_color_stop_rgba(1, 0.9, 0.98, 1, 0.0)
    c.set_source(g)
    c.arc(x, y, r, 0, 2 * math.pi)
    c.fill()
    # Thin-film colors on the rim and window reflections.
    for col, a0, a1 in (((1, 0.6, 0.9), 0.3, 1.4), ((0.6, 1, 0.8), 3.4, 4.4)):
        c.set_source_rgba(*col, 0.35)
        c.set_line_width(1.2 * S)
        c.arc(x, y, r * 0.9, a0, a1)
        c.stroke()
    c.set_source_rgba(1, 1, 1, 0.9)
    c.set_line_width(1.6 * S)
    c.arc(x, y, r * 0.72, math.radians(200), math.radians(250))
    c.stroke()
    c.arc(x - r * 0.25, y - r * 0.42, r * 0.09, 0, 2 * math.pi)
    c.fill()
    save(s, path)


def bauble(path):
    w, h = int(90 * S), int(170 * S)
    s, c = surface(w, h)
    x, y, r = w / 2, h * 0.70, 26 * S
    # Chain.
    for i in range(int((y - r - 6 * S) / (3.2 * S))):
        cy = i * 3.2 * S
        g = cairo.LinearGradient(x - 2 * S, 0, x + 2 * S, 0)
        g.add_color_stop_rgb(0, 0.55, 0.38, 0.08)
        g.add_color_stop_rgb(0.5, 1.0, 0.88, 0.5)
        g.add_color_stop_rgb(1, 0.5, 0.33, 0.05)
        c.set_source(g)
        c.set_line_width(0.9 * S)
        c.save()
        c.translate(x, cy + 1.6 * S)
        c.scale(1.2 * S if i % 2 else 0.5 * S, 1.6 * S)
        c.arc(0, 0, 1, 0, 2 * math.pi)
        c.restore()
        c.stroke()
    shadow, sh = surface(w, h)
    sh.set_source_rgba(0, 0, 0, 0.6)
    sh.arc(x + 3 * S, y + 5 * S, r, 0, 2 * math.pi)
    sh.fill()
    over(c, blur(shadow, 5 * S), 0.6)
    # Cap.
    g = cairo.LinearGradient(x - 7 * S, 0, x + 7 * S, 0)
    for t, col in ((0, (0.45, 0.3, 0.05)), (0.35, (1, 0.9, 0.55)), (0.6, (0.8, 0.6, 0.15)), (1, (0.4, 0.26, 0.04))):
        g.add_color_stop_rgb(t, *col)
    c.set_source(g)
    c.rectangle(x - 7 * S, y - r - 6 * S, 14 * S, 8 * S)
    c.fill()
    # Mirror-gold sphere: dark band, bright window reflection, warm rim light.
    g = cairo.RadialGradient(x - r * 0.35, y - r * 0.4, r * 0.05, x, y, r)
    for t, col in ((0, (1, 0.97, 0.8)), (0.18, (1, 0.83, 0.35)), (0.55, (0.72, 0.48, 0.08)),
                   (0.85, (0.35, 0.2, 0.02)), (1, (0.85, 0.6, 0.2))):
        g.add_color_stop_rgb(t, *col)
    c.set_source(g)
    c.arc(x, y, r, 0, 2 * math.pi)
    c.fill()
    c.set_source_rgba(1, 1, 1, 0.85)
    c.save()
    c.translate(x - r * 0.38, y - r * 0.45)
    c.rotate(-0.5)
    c.scale(r * 0.22, r * 0.12)
    c.arc(0, 0, 1, 0, 2 * math.pi)
    c.restore()
    c.fill()
    save(s, path)


def glitter(path):
    w, h = int(150 * S), int(100 * S)
    s, c = surface(w, h)
    rng = random.Random(5)
    for i in range(140):
        x = rng.random() ** 1.6 * w
        y = h - rng.random() ** 1.4 * h
        size = rng.uniform(0.7, 2.4) * S
        shade = rng.uniform(0.75, 1.0)
        c.save()
        c.translate(x, y)
        c.rotate(rng.uniform(0, math.pi))
        g = cairo.LinearGradient(-size, -size, size, size)
        g.add_color_stop_rgb(0, 1.0 * shade, 0.92 * shade, 0.6 * shade)
        g.add_color_stop_rgb(1, 0.6 * shade, 0.42 * shade, 0.08 * shade)
        c.set_source(g)
        c.rectangle(-size, -size * 0.6, size * 2, size * 1.2)
        c.fill()
        c.restore()
    save(s, path, 'lrtb')


def sparkle(path):
    r = 14 * S
    w = h = int(2 * r)
    s, c = surface(w, h)
    x = y = r
    g = cairo.RadialGradient(x, y, 0, x, y, r * 0.5)
    g.add_color_stop_rgba(0, 1, 1, 1, 1)
    g.add_color_stop_rgba(0.3, 1, 0.95, 0.75, 0.6)
    g.add_color_stop_rgba(1, 1, 0.9, 0.6, 0)
    c.set_source(g)
    c.arc(x, y, r * 0.5, 0, 2 * math.pi)
    c.fill()
    for angle, length in ((0, 1.0), (math.pi / 2, 1.0), (math.pi / 4, 0.55), (3 * math.pi / 4, 0.55)):
        c.save()
        c.translate(x, y)
        c.rotate(angle)
        g = cairo.LinearGradient(-r * length, 0, r * length, 0)
        g.add_color_stop_rgba(0, 1, 1, 1, 0)
        g.add_color_stop_rgba(0.5, 1, 1, 1, 0.95)
        g.add_color_stop_rgba(1, 1, 1, 1, 0)
        c.set_source(g)
        c.rectangle(-r * length, -0.5 * S, 2 * r * length, 1.0 * S)
        c.fill()
        c.restore()
    save(s, path)


def blossom(c, x, y, r, rot, rng):
    for i in range(5):
        a = rot + i * 2 * math.pi / 5
        c.save()
        c.translate(x, y)
        c.rotate(a)
        g = cairo.LinearGradient(0, 0, 0, -r)
        g.add_color_stop_rgb(0, 0.93, 0.45, 0.62)
        g.add_color_stop_rgb(0.45, 1.0, 0.80, 0.87)
        g.add_color_stop_rgb(1, 1.0, 0.95, 0.97)
        c.move_to(0, 0)
        c.curve_to(-r * 0.55, -r * 0.3, -r * 0.5, -r * 0.95, -r * 0.12, -r)
        c.line_to(0, -r * 0.86)  # notch at the petal tip
        c.line_to(r * 0.12, -r)
        c.curve_to(r * 0.5, -r * 0.95, r * 0.55, -r * 0.3, 0, 0)
        c.set_source(g)
        c.fill_preserve()
        c.set_source_rgba(0.85, 0.45, 0.6, 0.35)
        c.set_line_width(0.4 * S)
        c.stroke()
        c.restore()
    for i in range(10):
        a = rng.uniform(0, 2 * math.pi)
        l = r * rng.uniform(0.25, 0.42)
        c.set_source_rgba(0.85, 0.25, 0.45, 0.9)
        c.set_line_width(0.4 * S)
        c.move_to(x, y)
        c.line_to(x + math.cos(a) * l, y + math.sin(a) * l)
        c.stroke()
        c.set_source_rgb(1.0, 0.85, 0.3)
        c.arc(x + math.cos(a) * l, y + math.sin(a) * l, 0.6 * S, 0, 2 * math.pi)
        c.fill()


def branch(path):
    w, h = int(220 * S), int(190 * S)
    s, c = surface(w, h)
    rng = random.Random(9)
    shadow, sh = surface(w, h)
    limbs = [((w, 0.06 * h), (0.65 * w, 0.18 * h), (0.40 * w, 0.20 * h), (0.10 * w, 0.42 * h), 5.5 * S),
             ((0.62 * w, 0.18 * h), (0.55 * w, 0.35 * h), (0.60 * w, 0.50 * h), (0.48 * w, 0.66 * h), 3.2 * S),
             ((0.38 * w, 0.21 * h), (0.30 * w, 0.10 * h), (0.22 * w, 0.08 * h), (0.14 * w, 0.03 * h), 2.4 * S)]
    for ctx, col in ((sh, (0, 0, 0, 0.6)), (c, None)):
        for p0, p1, p2, p3, width in limbs:
            ctx.move_to(*p0)
            ctx.curve_to(*p1, *p2, *p3)
            ctx.set_line_width(width)
            ctx.set_line_cap(cairo.LINE_CAP_ROUND)
            if col:
                ctx.set_source_rgba(*col)
            else:
                g = cairo.LinearGradient(0, p0[1] - width, 0, p0[1] + width * 3)
                g.add_color_stop_rgb(0, 0.42, 0.28, 0.22)
                g.add_color_stop_rgb(0.5, 0.22, 0.13, 0.10)
                g.add_color_stop_rgb(1, 0.12, 0.07, 0.06)
                ctx.set_source(g)
            ctx.stroke()
        if ctx is sh:
            over(c, blur(shadow, 3 * S), 0.5, 2 * S, 4 * S)
    spots = [(0.12, 0.40, 13), (0.22, 0.30, 11), (0.35, 0.20, 14), (0.50, 0.27, 10), (0.47, 0.64, 13),
             (0.58, 0.47, 11), (0.16, 0.05, 10), (0.70, 0.12, 12), (0.30, 0.47, 9), (0.83, 0.20, 10)]
    for fx, fy, r in spots:
        blossom(c, fx * w, fy * h, r * S, rng.uniform(0, 1.2), rng)
    for fx, fy in ((0.40, 0.33), (0.62, 0.30), (0.25, 0.14)):
        g = cairo.RadialGradient(fx * w, fy * h, 0, fx * w, fy * h, 3.5 * S)
        g.add_color_stop_rgb(0, 1, 0.75, 0.85)
        g.add_color_stop_rgb(1, 0.85, 0.35, 0.55)
        c.set_source(g)
        c.save()
        c.translate(fx * w, fy * h)
        c.scale(2.6 * S, 3.6 * S)
        c.arc(0, 0, 1, 0, 2 * math.pi)
        c.restore()
        c.fill()
    save(s, path)


def petal(path):
    r = 9 * S
    w, h = int(2.2 * r), int(2.2 * r)
    s, c = surface(w, h)
    c.translate(w / 2, h / 2 + r * 0.5)
    g = cairo.LinearGradient(0, 0, 0, -r)
    g.add_color_stop_rgb(0, 0.95, 0.55, 0.70)
    g.add_color_stop_rgb(0.5, 1.0, 0.82, 0.89)
    g.add_color_stop_rgb(1, 1.0, 0.96, 0.98)
    c.move_to(0, 0)
    c.curve_to(-r * 0.6, -r * 0.3, -r * 0.55, -r * 0.95, -r * 0.13, -r)
    c.line_to(0, -r * 0.85)
    c.line_to(r * 0.13, -r)
    c.curve_to(r * 0.55, -r * 0.95, r * 0.6, -r * 0.3, 0, 0)
    c.set_source(g)
    c.fill()
    save(s, path)


def fallen_petals(path):
    w, h = int(140 * S), int(60 * S)
    s, c = surface(w, h)
    rng = random.Random(4)
    petal_path = "/tmp/_petal.png"
    petal(petal_path)
    p = cairo.ImageSurface.create_from_png(petal_path)
    for i in range(16):
        x = rng.random() ** 1.5 * w
        y = h - rng.random() ** 2 * h * 0.8 - 4 * S
        c.save()
        c.translate(x, y)
        c.rotate(rng.uniform(0, 2 * math.pi))
        k = rng.uniform(0.55, 1.0)
        c.scale(k, k * rng.uniform(0.55, 1.0))
        c.set_source_surface(p, -p.get_width() / 2, -p.get_height() / 2)
        c.paint_with_alpha(rng.uniform(0.75, 1.0))
        c.restore()
    save(s, path)


def prism(path):
    w, h = int(210 * S), int(220 * S)
    s, c = surface(w, h)
    px, py, r = 0.74 * w, 0.20 * h, 24 * S
    beam, bc = surface(w, h)
    spectrum = [(1, 0.2, 0.2), (1, 0.6, 0.1), (1, 0.95, 0.2), (0.3, 0.9, 0.35), (0.2, 0.75, 1), (0.55, 0.35, 1)]
    for i, col in enumerate(spectrum):
        a0 = math.radians(118 + i * 4.2)
        a1 = math.radians(118 + (i + 1) * 4.2)
        bc.move_to(px - r * 0.3, py + r * 0.2)
        bc.line_to(px + math.cos(a0) * w * 1.3, py + math.sin(a0) * w * 1.3)
        bc.line_to(px + math.cos(a1) * w * 1.3, py + math.sin(a1) * w * 1.3)
        bc.close_path()
        g = cairo.RadialGradient(px, py, r * 0.2, px, py, w * 1.05)
        g.add_color_stop_rgba(0, *col, 0.75)
        g.add_color_stop_rgba(1, *col, 0)
        bc.set_source(g)
        bc.fill()
    over(c, blur(beam, 2.5 * S))
    # Glass prism with refracted edges.
    tri = [(px, py - r), (px + r * 0.95, py + r * 0.75), (px - r * 0.95, py + r * 0.75)]
    c.move_to(*tri[0])
    for p in tri[1:]:
        c.line_to(*p)
    c.close_path()
    g = cairo.LinearGradient(px - r, py - r, px + r, py + r)
    g.add_color_stop_rgba(0, 1, 1, 1, 0.55)
    g.add_color_stop_rgba(0.5, 0.75, 0.82, 0.95, 0.25)
    g.add_color_stop_rgba(1, 0.95, 0.97, 1, 0.5)
    c.set_source(g)
    c.fill_preserve()
    c.set_source_rgba(1, 1, 1, 0.9)
    c.set_line_width(1.1 * S)
    c.stroke()
    c.set_source_rgba(1, 1, 1, 0.55)
    c.set_line_width(0.6 * S)
    c.move_to(px, py - r)
    c.line_to(px + r * 0.15, py + r * 0.75)
    c.stroke()
    save(s, path, 'lb')


def light_leak(path):
    w, h = int(170 * S), int(130 * S)
    s, c = surface(w, h)
    leak, lc = surface(w, h)
    spectrum = [(1, 0.3, 0.4), (1, 0.75, 0.2), (0.4, 1, 0.6), (0.3, 0.7, 1), (0.7, 0.4, 1)]
    for i, col in enumerate(spectrum):
        g = cairo.RadialGradient(0.12 * w, 0.88 * h, 0, 0.12 * w, 0.88 * h, w * (0.3 + i * 0.11))
        g.add_color_stop_rgba(0, *col, 0)
        g.add_color_stop_rgba(0.8, *col, 0.45)
        g.add_color_stop_rgba(1, *col, 0)
        lc.set_source(g)
        lc.paint()
    over(c, blur(leak, 4 * S), 0.8)
    save(s, path, 'lrtb')


def main(out, preview=None):
    import os
    os.makedirs(out, exist_ok=True)
    p = lambda name: os.path.join(out, f"prism_decor_{name}.png")
    hub = web_top_right(p("web_tr"))
    web_bottom_left(p("web_bl"))
    attach = spider(p("spider"))
    bow(p("bow_tr"))
    hearts(p("hearts_bl"))
    heart = []
    for i in range(121):
        a = i / 120 * 2 * math.pi
        hx = 16 * math.sin(a) ** 3
        hy = -(13 * math.cos(a) - 5 * math.cos(2 * a) - 2 * math.cos(3 * a) - math.cos(4 * a))
        heart.append((0.55 + hx / 50, 0.42 + hy / 50))
    neon(p("neon_tr"), heart, [(1.0, 0.1, 0.8), (0.2, 1.0, 0.85)], "lrtb")
    wave = [(0.08 + i / 60 * 0.84, 0.55 + 0.16 * math.sin(i / 60 * 3 * math.pi)) for i in range(61)]
    neon(p("neon_bl"), wave, [(0.2, 1.0, 0.85), (1.0, 0.1, 0.8)], "lrtb")
    god_rays(p("rays_tr"))
    bubble(p("bubble"))
    bauble(p("bauble_tr"))
    glitter(p("glitter_bl"))
    sparkle(p("sparkle"))
    branch(p("branch_tr"))
    fallen_petals(p("petals_bl"))
    petal(p("petal"))
    prism(p("prism_tr"))
    light_leak(p("leak_bl"))
    print("web hub", hub, "spider attach", attach)
    if preview:
        make_preview(out, preview)


def make_preview(out, path):
    import os
    themes = [("web_tr", "web_bl", "spider"), ("bow_tr", "hearts_bl", None), ("neon_tr", "neon_bl", None),
              ("rays_tr", None, "bubble"), ("bauble_tr", "glitter_bl", "sparkle"),
              ("branch_tr", "petals_bl", "petal"), ("prism_tr", "leak_bl", None)]
    pw, ph = int(411 * S * 0.5), int(880 * S * 0.5)
    sheet = cairo.ImageSurface(cairo.FORMAT_ARGB32, pw * len(themes), ph)
    c = cairo.Context(sheet)
    for i, (tr, bl, sprite) in enumerate(themes):
        c.save()
        c.translate(i * pw, 0)
        c.rectangle(0, 0, pw, ph)
        c.clip()
        g = cairo.LinearGradient(0, 0, 0, ph)
        g.add_color_stop_rgb(0, 0.10, 0.10, 0.13)
        g.add_color_stop_rgb(1, 0.03, 0.03, 0.05)
        c.set_source(g)
        c.paint()
        c.scale(0.5, 0.5)
        top = 40 * S
        bottom = 880 * S - 70 * S
        W = 411 * S
        if tr:
            img = cairo.ImageSurface.create_from_png(os.path.join(out, f"prism_decor_{tr}.png"))
            c.set_source_surface(img, W - img.get_width(), top)
            c.paint()
        if bl:
            img = cairo.ImageSurface.create_from_png(os.path.join(out, f"prism_decor_{bl}.png"))
            c.set_source_surface(img, 0, bottom - img.get_height())
            c.paint()
        if sprite:
            img = cairo.ImageSurface.create_from_png(os.path.join(out, f"prism_decor_{sprite}.png"))
            if sprite == "spider":
                hx, hy = W - 160 * S + 0.66 * 160 * S, top + 0.30 * 178 * S
                c.set_source_rgba(0.85, 0.85, 0.9, 0.8)
                c.set_line_width(0.5 * S)
                c.move_to(hx, hy)
                c.line_to(hx, hy + 110 * S)
                c.stroke()
                c.set_source_surface(img, hx - img.get_width() / 2, hy + 110 * S - 2)
                c.paint()
            else:
                for k, (fx, fy) in enumerate(((0.85, 0.30), (0.12, 0.85), (0.78, 0.22))):
                    c.set_source_surface(img, W * fx, 880 * S * fy)
                    c.paint()
        c.restore()
    sheet.write_to_png(path)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
