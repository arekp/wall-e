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
MARGIN = 5.0         # odstęp modułów od krawędzi płyty (góra/dół – tam są otwory opasek)
MARGIN_X = 2.5       # odstęp od lewej/prawej krawędzi (opaski idą w poprzek Y, więc może być mały)
GAP = 5.0            # odstęp między modułami w tym samym rzędzie
ROW_GAP = 10.0       # odstęp między rzędami (opaski, przewody i otwory M3 płyty)
BR_H = 4.5           # wysokość narożnych kątowników ponad półką
BR_ARM = 7.0         # długość ramienia kątownika
BR_T = 1.6           # grubość ścianki kątownika (4 ścieżki dyszy 0.4)
SHELF_H = 2.5        # wysokość półki pod narożnikami (luz na lutowania od spodu modułu)
SHELF = 3.5          # wielkość półki
TIE_W, TIE_H = 4.4, 2.0   # otwór na opaskę zaciskową 2,5–3,6 mm
HOLE_D = 3.4         # otwory M3 płyty (w pasach między rzędami modułów)
HOLE_X = (12.0, 8.0)  # odległość otworów od lewej / prawej krawędzi płyty
SPACER_H, SPACER_OD = 12.0, 8.0   # min. 12 (piny ESP32 ok. 8,5 mm + płyta). POD płytą leży akumulator: wysokość odstępnika = wys. akumulatora + 3 mm zapasu, gdy > 12

# Maksymalny rozmiar płyty – ZMIERZONY przez użytkownika we wnętrzu zbudowanego korpusu (wcześniejsze
# oszacowanie z rozłożonych części 3MF okazało się za duże). Skrypt przerywa, gdy płyta jest większa.
PLATE_MAX_W = 125.0
PLATE_MAX_D = 110.0


# Opis dla człowieka: co to jest i co do niego podłączyć (piny wg docs/electronics.md)
ROLE = {
    "ESP32 DevKit 38p": "1|ESP32: mózg. USB od krawędzi płyty (programowanie), antena bez metalu obok",
    "PCA9685": "2|PCA9685: serwa (V+ z buck 6 V + 1000 µF), I2C do ESP32",
    "Buck 5 V (LM2596)": "3|Buck 5 V: ESP32, MAX98357A (+ ESP32-CAM). Wejście z baterii",
    "Buck 6 V 5 A": "4|Buck 6 V: tylko serwa (V+ PCA9685). Wejście z baterii",
    "MAX98357A": "5|MAX98357A: głośnik, I2S z ESP32, 5 V z buck 5 V",
    "TB6612FNG": "6|TB6612: silniki gąsienic, VM wprost z baterii (+470 µF)",
}


@dataclass
class Module:
    name: str
    w: float            # wymiar X modułu [mm]
    d: float            # wymiar Y modułu [mm]
    x: float = 0.0
    y: float = 0.0
    shelf: bool = True  # półki w narożnikach
    pin_slots: bool = False  # przelotowe szczeliny pod piny (ESP32 z kołkami w dół)
    bh: float = BR_H    # wysokość kątowników ponad półką
    note: str = ""


# Wymiary modułu (PCB, bez luzu). ŹRÓDŁA i pewność: docs/mounting-plate.md
MODULES = [
    # rząd 1
    Module("ESP32 DevKit 38p", 55.0, 28.0, shelf=False, pin_slots=True,
           note="klony 51–55 x 25–28; rozstaw rzędów 22,86 lub 25,4"),
    Module("PCA9685", 62.5, 25.4, note="jedno źródło; inne oferty 60 x 25"),
    # rząd 2
    Module("Buck 5 V (LM2596)", 43.0, 21.0, note="⚠️ założenie – nie sprawdzano w sieci"),
    Module("Buck 6 V 5 A", 51.0, 23.0, note="⚠️ założenie (typ XL4015) – nie sprawdzano w sieci"),
    Module("MAX98357A", 19.4, 17.8, note="Adafruit 19,4 x 17,8"),
    # rząd 3
    Module("TB6612FNG", 25.4, 22.9, note="klony 24 x 21 – 25,4 x 22,9"),
]
BY = {m.name: m for m in MODULES}
ROWS = [
    ["ESP32 DevKit 38p", "Buck 5 V (LM2596)"],   # logika (5 V)
    ["PCA9685", "Buck 6 V 5 A"],                 # serwa (6 V)
    ["TB6612FNG", "MAX98357A"],                  # silniki i audio; z prawej wolne miejsce na bezpiecznik/wyłącznik/napisy
]
# ESP32 stoi na kołkach w dół → szczeliny pod piny; rozstaw szczelin obejmuje 22,86 i 25,4 mm
ESP_SLOT_C, ESP_SLOT_W, ESP_SLOT_L = 11.5, 5.0, 50.0


def pw(m): return m.w + 2 * CLEAR
def pd(m): return m.d + 2 * CLEAR


def layout() -> tuple[float, float]:
    """Rzędy modułów od dołu do góry. Zwraca (W, D) płyty.
    Szerokość wyznacza najszerszy rząd; pozostałe rzędy są rozsunięte na całą szerokość (moduły przy
    krawędziach, wolne miejsce w środku – na napisy, bezpiecznik, wyłącznik, kondensatory)."""
    row_w = [sum(pw(BY[n]) for n in row) + GAP * (len(row) - 1) for row in ROWS]
    inner_w = max(row_w)
    W = inner_w + 2 * MARGIN_X
    y = MARGIN
    for row, rw in zip(ROWS, row_w):
        gap = GAP + (inner_w - rw) / (len(row) - 1) if len(row) > 1 else GAP
        x = MARGIN_X
        for n in row:
            m = BY[n]
            m.x, m.y = x, y
            x += pw(m) + gap
        y += max(pd(BY[n]) for n in row) + ROW_GAP
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
    top = z0 + (SHELF_H if m.shelf else 0) + m.bh
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


# ───────────────────────── opisy wyryte w płycie ─────────────────────────
LABELS = {   # krótki napis wyryty obok kieszeni (numer jak na preview.png i w docs/mounting-plate.md)
    "ESP32 DevKit 38p": "1 ESP32",
    "PCA9685": "2 SERWA",
    "Buck 5 V (LM2596)": "3 5V",
    "Buck 6 V 5 A": "4 6V",
    "MAX98357A": "5 AUDIO",
    "TB6612FNG": "6 MOTOR",
}
TEXT_H = 3.5         # wysokość liter [mm]; poniżej ok. 3 mm druk 0,4 mm jest nieczytelny
ENGRAVE = 0.8        # głębokość ryty [mm] (4 warstwy po 0,2)


def text_polygon(txt, height):
    """Napis → wielokąt shapely (litery z otworami), lewy dolny róg bboxa w (0,0)."""
    from matplotlib.font_manager import FontProperties
    from matplotlib.textpath import TextPath
    from shapely.geometry import Polygon
    from shapely.ops import unary_union
    tp = TextPath((0, 0), txt, size=height / 0.72, prop=FontProperties(family="DejaVu Sans", weight="bold"))
    polys = [Polygon(p) for p in tp.to_polygons() if len(p) >= 3]
    # otwory liter (O, A, 6…): kontur leżący w innym konturze odejmujemy (parzysty-nieparzysty)
    solid = None
    for p in sorted(polys, key=lambda q: -q.area):
        if not p.is_valid:
            p = p.buffer(0)
        solid = p if solid is None else solid.symmetric_difference(p)
    x0, y0, _, _ = solid.bounds
    from shapely import affinity
    return affinity.translate(solid, -x0, -y0)


def place_labels(W, D):
    """Dla każdego modułu wybiera wolne miejsce na napis (nad lub pod kieszenią); zwraca [(tekst, poly, x, y)]."""
    from shapely.geometry import box as sbox
    keep = []   # strefy zakazane
    for m in MODULES:
        x0, y0, x1, y1 = pocket_rect(m)
        keep.append(sbox(x0 - BR_T - 0.6, y0 - BR_T - 0.6, x1 + BR_T + 0.6, y1 + BR_T + 0.6))
        for c in cutouts(m):
            b = c.bounds
            keep.append(sbox(b[0][0] - 0.8, b[0][1] - 0.8, b[1][0] + 0.8, b[1][1] + 0.8))
    for hx, hy in hole_positions(W, D):
        keep.append(sbox(hx - HOLE_D / 2 - 1.2, hy - HOLE_D / 2 - 1.2, hx + HOLE_D / 2 + 1.2, hy + HOLE_D / 2 + 1.2))
    edge = sbox(1.0, 1.0, W - 1.0, D - 1.0)
    placed, out = [], []
    for m in MODULES:
        txt = LABELS[m.name]
        poly = text_polygon(txt, TEXT_H)
        w, h = poly.bounds[2], poly.bounds[3]
        x0, y0, x1, y1 = pocket_rect(m)
        found = None
        # kandydaci: cała płyta, najbliżej kieszeni modułu (odległość od krawędzi kieszeni), max 22 mm
        pocket = sbox(x0, y0, x1, y1)
        cands = []
        for tx in np.arange(1.0, W - w, 0.5):
            for ty in np.arange(1.0, D - h, 0.5):
                tb = sbox(tx, ty, tx + w, ty + h)
                dist = tb.distance(pocket)
                # napis musi leżeć bliżej własnej kieszeni niż jakiejkolwiek innej (inaczej myli)
                nearer = all(dist < tb.distance(sbox(*pocket_rect(o))) for o in MODULES if o is not m)
                if dist <= 22.0 and nearer:
                    cands.append((dist, tx, ty))
        cands = [(tx, ty) for _, tx, ty in sorted(cands)]
        for tx, ty in cands:
            r = sbox(tx, ty, tx + w, ty + h)
            if edge.contains(r) and not any(r.intersects(k) for k in keep + placed):
                found = (tx, ty, r)
                break
        assert found, f"brak miejsca na napis '{txt}' – zwiększ ROW_GAP/MARGIN albo skróć napis"
        placed.append(found[2].buffer(0.5))
        out.append((txt, poly, found[0], found[1]))
    return out


def label_cutters(W, D):
    import trimesh.creation as tc
    from shapely import affinity
    cuts = []
    for txt, poly, x, y in place_labels(W, D):
        p = affinity.translate(poly, x, y)
        geoms = list(p.geoms) if hasattr(p, "geoms") else [p]
        for g in geoms:
            m = tc.extrude_polygon(g, height=ENGRAVE + 1.0)
            m.apply_translation([0, 0, PLATE_T - ENGRAVE])
            cuts.append(m)
    return cuts


def hole_positions(W, D):
    """4 otwory M3 w pasach między rzędami: w każdym pasie jeden przy lewej i jeden przy prawej krawędzi,
    w pierwszym wolnym miejscu (omijają otwory opasek i kątowniki)."""
    r = HOLE_D / 2 + 1.0
    rects = [pocket_rect(m) for m in MODULES]
    cuts = [c.bounds for m in MODULES for c in cutouts(m)]

    def free(hx, hy):
        for (x0, y0, x1, y1) in rects:
            if x0 - BR_T - r < hx < x1 + BR_T + r and y0 - BR_T - r < hy < y1 + BR_T + r:
                return False
        for b in cuts:
            if b[0][0] - r < hx < b[1][0] + r and b[0][1] - r < hy < b[1][1] + r:
                return False
        return True

    ys, y = [], MARGIN
    for row in ROWS[:-1]:
        y += max(pd(BY[n]) for n in row)
        ys.append(y + ROW_GAP / 2)
        y += ROW_GAP
    out = []
    for yy in ys:
        for side, start in ((1, HOLE_X), (-1, HOLE_X)):
            xs = [HOLE_X[0] + i * 0.5 for i in range(0, 120)] if side == 1 else [W - HOLE_X[1] - i * 0.5 for i in range(0, 120)]
            x = next((x for x in xs if free(x, yy)), None)
            assert x is not None, f"brak miejsca na otwór M3 w pasie y={yy:.1f}"
            out.append((x, yy))
    return out


def build_plate():
    W, D = layout()
    plate = box(0, 0, 0, W, D, PLATE_T)
    cuts = [cyl(x, y, -1, PLATE_T + 1, HOLE_D) for x, y in hole_positions(W, D)]
    adds = []
    for m in MODULES:
        cuts += cutouts(m)
        adds += brackets(m)
    cuts += label_cutters(W, D)
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

    fig = plt.figure(figsize=(15, 8.5))
    ax = fig.add_axes([0.03, 0.06, 0.52, 0.86])
    ax.add_patch(Rectangle((0, 0), W, D, fc="#f1e3cf", ec="#2b2118", lw=2))
    for x, y in hole_positions(W, D):
        ax.add_patch(Circle((x, y), HOLE_D / 2, fc="white", ec="#2b2118"))
        ax.annotate("M3", (x, y), (x + 3, y + 3), fontsize=7)
    legend = []
    for m in MODULES:
        x0, y0, x1, y1 = pocket_rect(m)
        num, text = ROLE[m.name].split("|", 1)
        ax.add_patch(Rectangle((x0, y0), x1 - x0, y1 - y0, fc="#ffe29a", ec="#9a4f12", lw=1.5))
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        ax.text(cx, cy, f"{num}\n{m.name}\n{m.w:g} x {m.d:g}", ha="center", va="center", fontsize=8)
        legend.append((num, text))
        for c in cutouts(m):
            b = c.bounds
            ax.add_patch(Rectangle((b[0][0], b[0][1]), b[1][0] - b[0][0], b[1][1] - b[0][1], fc="white", ec="#2b2118", lw=0.8))
    for txt, poly, x, y in place_labels(W, D):
        ax.text(x, y, txt, fontsize=7, color="#2b2118", weight="bold", va="bottom", ha="left",
                bbox=dict(fc="none", ec="#2b2118", lw=0.4, pad=0.5))
    ax.set_xlim(-3, W + 3)
    ax.set_ylim(-3, D + 3)
    ax.set_aspect("equal")
    ax.set_title(f"Płytka montażowa Wall-E – {W:.0f} x {D:.0f} mm (widok z góry; białe = otwory przelotowe)")
    ax.set_xlabel("X [mm]  –  boki wzdłuż X to dłuższy bok płyty")
    fig.text(0.58, 0.9, "Co gdzie leży", fontsize=14, weight="bold")
    for i, (num, text) in enumerate(sorted(legend)):
        fig.text(0.58, 0.84 - i * 0.085, f"{num}.  {text}", fontsize=9.5, wrap=True, va="top")
    fig.text(0.58, 0.84 - len(legend) * 0.085 - 0.02,
             "Akumulator NIE jest na płytce – leży pod nią (rozmiar do ustalenia).\n"
             "Zasilanie: bateria → bezpiecznik → wyłącznik → bucki i TB6612.\n"
             "Masy wszystkich modułów połączone razem (wspólna masa).\n"
             "Przewody wychodzą od spodu przez otwory na opaski.", fontsize=9, va="top", style="italic")
    fig.savefig(OUT / "preview.png", dpi=110)


def check_layout(W, D):
    """Kontrole geometryczne układu; zgłasza błąd zamiast po cichu generować zły plik."""
    rects = [pocket_rect(m) for m in MODULES]
    need = 2 * BR_T + 1.0   # kątowniki obu sąsiadów + 1 mm
    for i, a in enumerate(rects):
        for b in rects[i + 1:]:
            ok = a[2] + need <= b[0] or b[2] + need <= a[0] or a[3] + need <= b[1] or b[3] + need <= a[1]
            assert ok, "kieszenie za blisko"
    # otwory M3 płyty: nie mogą wchodzić na kątowniki (1,6 mm poza kieszenią) ani na otwory opasek
    r = HOLE_D / 2 + 1.0
    for hx, hy in hole_positions(W, D):
        for (x0, y0, x1, y1) in rects:
            assert not (x0 - BR_T - r < hx < x1 + BR_T + r and y0 - BR_T - r < hy < y1 + BR_T + r), \
                f"otwór M3 ({hx:.1f},{hy:.1f}) koliduje z kieszenią"
        for m in MODULES:
            for c in cutouts(m):
                b = c.bounds
                assert not (b[0][0] - r < hx < b[1][0] + r and b[0][1] - r < hy < b[1][1] + r), \
                    f"otwór M3 ({hx:.1f},{hy:.1f}) koliduje z otworem opaski {m.name}"
    # zmieszczenie we wnętrzu korpusu (wymiary z 3MF – patrz docs/mounting-plate.md pkt 4)
    assert W <= PLATE_MAX_W + 1e-6, f"płyta za szeroka: {W:.1f} > {PLATE_MAX_W}"
    assert D <= PLATE_MAX_D + 1e-6, f"płyta za głęboka: {D:.1f} > {PLATE_MAX_D}"


def main():
    global SPACER_H
    if len(sys.argv) > 1:   # np. python3 plate.py 35  → odstępnik 35 mm (wysokość akumulatora + 3 mm)
        SPACER_H = float(sys.argv[1])
        assert SPACER_H >= 12, "odstępnik < 12 mm: piny ESP32 oprą się o dno"
    plate, W, D = build_plate()
    check_layout(W, D)
    coupon = build_coupon()
    spacer = build_spacer()
    check("plate", plate)
    check("coupon", coupon)
    check("spacer", spacer)
    plate.export(OUT / "walle_plate.stl")
    coupon.export(OUT / "walle_fit_coupon.stl")
    spacer.export(OUT / "walle_spacer.stl")
    preview(W, D)
    print(f"płyta {W:.1f} x {D:.1f} mm (limit {PLATE_MAX_W:g} x {PLATE_MAX_D:g}, zapas {PLATE_MAX_W - W:.1f} x {PLATE_MAX_D - D:.1f} mm) – OK")


if __name__ == "__main__":
    sys.exit(main())
