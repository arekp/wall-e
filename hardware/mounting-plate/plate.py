#!/usr/bin/env python3
"""Parametryczna płytka montażowa elektroniki Wall-E (druk 3D).

Generuje:
  walle_plate.stl      – pełna płytka (kieszenie na moduły, otwory na odstępniki)
  walle_fit_coupon.stl – cienki szablon samych obrysów do przymiarki modułów (kilkanaście minut druku)
  walle_spacer.stl     – tulejka dystansowa (drukuj 4 szt. albo użyj kupnych odstępników M3)
  preview.png          – widok z góry z opisem

Wszystkie wymiary modułów są w słowniku MODULES poniżej – zmierz własne moduły suwmiarką i popraw
TYLKO tam. Wymiary oznaczone ⚠️ w docs/mounting-plate.md nie są potwierdzone z dokumentacji producenta.

Użycie:  pip install trimesh manifold3d numpy matplotlib shapely && python3 plate.py
"""
from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import trimesh

OUT = Path(__file__).parent

# ───────────────────────── parametry druku ─────────────────────────
CLEAR = 0.3          # luz na stronę między modułem a kieszenią (FDM: 0.2–0.4)
PLATE_T = 3.5        # grubość płyty
MARGIN = 11.0        # odstęp modułów od krawędzi płyty
GAP = 10.0           # odstęp między rzędami modułów (miejsce na opaskę i przewody)
BR_H = 4.5           # wysokość narożnych kątowników ponad półką
BR_ARM = 7.0         # długość ramienia kątownika
BR_T = 1.6           # grubość ścianki kątownika (4 ścieżki dyszy 0.4)
SHELF_H = 2.5        # wysokość półki pod narożnikami (luz na lutowania od spodu modułu)
SHELF = 3.5          # wielkość półki
TIE_W, TIE_H = 4.4, 2.0   # otwór na opaskę zaciskową 2,5–3,6 mm
HOLE_D = 3.4         # otwory M3 w narożnikach płyty
HOLE_INSET = 6.0
SPACER_H, SPACER_OD = 12.0, 8.0   # musi być > wystające piny ESP32 (ok. 8,5 mm) + grubość płyty


@dataclass
class Module:
    name: str
    w: float            # wymiar X modułu [mm]
    d: float            # wymiar Y modułu [mm]
    x: float = 0.0
    y: float = 0.0
    shelf: bool = True  # półki w narożnikach
    pin_slots: bool = False  # przelotowe szczeliny pod piny (ESP32 z kołkami w dół)
    note: str = ""


# Wymiary modułu (PCB, bez luzu). ŹRÓDŁA i pewność: docs/mounting-plate.md
MODULES = [
    # wiersz A
    Module("ESP32 DevKit 38p", 55.0, 28.0, pin_slots=False, shelf=False,
           note="klony 51–55 x 25–28; rozstaw rzędów 22,86 lub 25,4"),
    Module("PCA9685", 62.5, 25.4, note="jedno źródło; inne oferty 60 x 25"),
    # wiersz B
    Module("MAX98357A", 19.4, 17.8, note="Adafruit 19,4 x 17,8"),
    Module("Buck 5 V (LM2596)", 43.0, 21.0, note="⚠️ założenie – nie sprawdzano w sieci"),
    # wiersz C
    Module("Buck 6 V 5 A", 51.0, 23.0, note="⚠️ założenie (typ XL4015) – nie sprawdzano w sieci"),
]
BY = {m.name: m for m in MODULES}
# ESP32 stoi na kołkach w dół → szczeliny pod piny; rozstaw szczelin obejmuje 22,86 i 25,4 mm
ESP_SLOT_C, ESP_SLOT_W, ESP_SLOT_L = 11.5, 5.0, 50.0
BY["ESP32 DevKit 38p"].pin_slots = True


def layout() -> tuple[float, float]:
    """Ręczne rozmieszczenie (współrzędne lewego dolnego rogu kieszeni). Zwraca (W, D) płyty."""
    def pw(m): return m.w + 2 * CLEAR
    def pd(m): return m.d + 2 * CLEAR
    esp, pca = BY["ESP32 DevKit 38p"], BY["PCA9685"]
    amp, b5, b6 = (BY[n] for n in ("MAX98357A", "Buck 5 V (LM2596)", "Buck 6 V 5 A"))

    y = MARGIN
    esp.x, esp.y = MARGIN, y
    pca.x, pca.y = esp.x + pw(esp) + GAP, y
    rowA = max(pd(esp), pd(pca))

    y = MARGIN + rowA + GAP
    b5.x, b5.y = MARGIN, y
    b6.x, b6.y = b5.x + pw(b5) + GAP, y
    amp.x, amp.y = b6.x + pw(b6) + GAP, y

    W = max(m.x + pw(m) for m in MODULES) + MARGIN
    D = max(m.y + pd(m) for m in MODULES) + MARGIN
    return W, D


def box(x0, y0, z0, x1, y1, z1):
    b = trimesh.creation.box(extents=[x1 - x0, y1 - y0, z1 - z0])
    b.apply_translation([(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2])
    return b


def cyl(cx, cy, z0, z1, d, sections=48):
    c = trimesh.creation.cylinder(radius=d / 2, height=z1 - z0, sections=sections)
    c.apply_translation([cx, cy, (z0 + z1) / 2])
    return c


def union(parts):
    return trimesh.boolean.union(parts, engine="manifold")


def diff(a, parts):
    return trimesh.boolean.difference([a] + parts, engine="manifold")


def pocket_rect(m: Module):
    return (m.x, m.y, m.x + m.w + 2 * CLEAR, m.y + m.d + 2 * CLEAR)


def brackets(m: Module):
    """Cztery kątowniki L w narożnikach kieszeni (+ półka pod narożnikami płytki)."""
    x0, y0, x1, y1 = pocket_rect(m)
    parts = []
    z0 = PLATE_T
    top = z0 + (SHELF_H if m.shelf else 0) + BR_H
    for sx, cx in ((1, x0), (-1, x1)):
        for sy, cy in ((1, y0), (-1, y1)):
            # ramię wzdłuż X i wzdłuż Y, leżą na zewnątrz kieszeni
            # ramię w osi X (przy krawędzi poziomej)
            hx0, hx1 = sorted((cx - sx * BR_T, cx + sx * BR_ARM))
            hy0, hy1 = sorted((cy - sy * BR_T, cy))
            parts.append(box(hx0, hy0, z0, hx1, hy1, top))
            # ramię w osi Y (przy krawędzi pionowej)
            vx0, vx1 = sorted((cx - sx * BR_T, cx))
            vy0, vy1 = sorted((cy - sy * BR_T, cy + sy * BR_ARM))
            parts.append(box(vx0, vy0, z0, vx1, vy1, top))
            if m.shelf:
                sx0, sx1 = sorted((cx, cx + sx * SHELF))
                sy0, sy1 = sorted((cy, cy + sy * SHELF))
                parts.append(box(sx0, sy0, z0, sx1, sy1, z0 + SHELF_H))
    return parts


def cutouts(m: Module):
    """Szczeliny przelotowe: pod piny ESP32 i na opaski zaciskowe."""
    x0, y0, x1, y1 = pocket_rect(m)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    cuts = []
    if m.pin_slots:
        for s in (-1, 1):
            cuts.append(box(cx - ESP_SLOT_L / 2, cy + s * ESP_SLOT_C - ESP_SLOT_W / 2, -1,
                            cx + ESP_SLOT_L / 2, cy + s * ESP_SLOT_C + ESP_SLOT_W / 2, PLATE_T + 1))
    # opaski prowadzimy w poprzek krótszego wymiaru, otwory leżą poza krawędziami modułu
    short_is_y = (y1 - y0) <= (x1 - x0)
    off = 3.5
    for s in (-1, 1):
        if short_is_y:
            ty = (cy + s * ((y1 - y0) / 2 + off))
            cuts.append(box(cx - TIE_W / 2, ty - TIE_H / 2, -1, cx + TIE_W / 2, ty + TIE_H / 2, PLATE_T + 1))
        else:
            tx = (cx + s * ((x1 - x0) / 2 + off))
            cuts.append(box(tx - TIE_H / 2, cy - TIE_W / 2, -1, tx + TIE_H / 2, cy + TIE_W / 2, PLATE_T + 1))
    return cuts


def hole_positions(W, D):
    i = HOLE_INSET
    return [(i, i), (W - i, i), (i, D - i), (W - i, D - i)]


def build_plate():
    W, D = layout()
    plate = box(0, 0, 0, W, D, PLATE_T)
    cuts = [cyl(x, y, -1, PLATE_T + 1, HOLE_D) for x, y in hole_positions(W, D)]
    adds = []
    for m in MODULES:
        cuts += cutouts(m)
        adds += brackets(m)
    solid = union([plate] + adds)
    return diff(solid, cuts), W, D


def build_coupon():
    """Szablon: 0,8 mm podłoże wokół obrysów + same kątowniki (bez półek) – do przymiarki modułów."""
    W, D = layout()
    parts = []
    for m in MODULES:
        x0, y0, x1, y1 = pocket_rect(m)
        # pierścień o szerokości 3 mm wokół kieszeni, 1 mm grubości
        parts.append(box(x0 - 3, y0 - 3, 0, x1 + 3, y1 + 3, 1.0))
    base = union(parts)
    holes = []
    for m in MODULES:
        x0, y0, x1, y1 = pocket_rect(m)
        holes.append(box(x0, y0, -1, x1, y1, 2.0))
    base = diff(base, holes)
    br = []
    for m in MODULES:
        m2 = Module(m.name, m.w, m.d, m.x, m.y, shelf=False)
        for b in brackets(m2):
            b.apply_translation([0, 0, -PLATE_T + 1.0])
            br.append(b)
    return union([base] + br)


def build_spacer():
    outer = cyl(0, 0, 0, SPACER_H, SPACER_OD)
    return diff(outer, [cyl(0, 0, -1, SPACER_H + 1, HOLE_D)])


def check(name, mesh):
    ext = mesh.bounds[1] - mesh.bounds[0]
    print(f"{name}: watertight={mesh.is_watertight} volume={mesh.volume/1000:.1f} cm3 "
          f"rozmiar={ext[0]:.1f} x {ext[1]:.1f} x {ext[2]:.1f} mm")
    assert mesh.is_watertight, f"{name}: siatka nie jest zamknięta"


def preview(W, D):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from matplotlib.patches import Rectangle, Circle

    fig, ax = plt.subplots(figsize=(11, 11 * D / W + 0.6))
    ax.add_patch(Rectangle((0, 0), W, D, fc="#f1e3cf", ec="#2b2118", lw=2))
    for x, y in hole_positions(W, D):
        ax.add_patch(Circle((x, y), HOLE_D / 2, fc="white", ec="#2b2118"))
        ax.annotate("M3", (x, y), (x + 4, y + 4), fontsize=7)
    for m in MODULES:
        x0, y0, x1, y1 = pocket_rect(m)
        ax.add_patch(Rectangle((x0, y0), x1 - x0, y1 - y0, fc="#ffe29a", ec="#9a4f12", lw=1.5))
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        ax.text(cx, cy, f"{m.name}\n{m.w:g} x {m.d:g}", ha="center", va="center", fontsize=8)
        for c in cutouts(m):
            b = c.bounds
            ax.add_patch(Rectangle((b[0][0], b[0][1]), b[1][0] - b[0][0], b[1][1] - b[0][1], fc="white", ec="#2b2118", lw=0.8))
    ax.set_xlim(-3, W + 3)
    ax.set_ylim(-3, D + 3)
    ax.set_aspect("equal")
    ax.set_title(f"Płytka montażowa Wall-E – {W:.0f} x {D:.0f} mm (widok z góry; białe = otwory przelotowe)")
    fig.savefig(OUT / "preview.png", dpi=110, bbox_inches="tight")


def main():
    plate, W, D = build_plate()
    coupon = build_coupon()
    spacer = build_spacer()
    check("plate", plate)
    check("coupon", coupon)
    check("spacer", spacer)
    plate.export(OUT / "walle_plate.stl")
    coupon.export(OUT / "walle_fit_coupon.stl")
    spacer.export(OUT / "walle_spacer.stl")
    # kontrola kolizji kieszeni
    rects = [pocket_rect(m) for m in MODULES]
    for i, a in enumerate(rects):
        for b in rects[i + 1:]:
            assert a[2] + 6 <= b[0] or b[2] + 6 <= a[0] or a[3] + 6 <= b[1] or b[3] + 6 <= a[1], "kieszenie za blisko"
    preview(W, D)
    print(f"płyta {W:.1f} x {D:.1f} mm – OK")


if __name__ == "__main__":
    sys.exit(main())
