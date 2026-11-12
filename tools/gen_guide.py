#!/usr/bin/env python3
"""Draws the build guide (docs/montagem.png): the four layers of the foundry seen from above.

    python3 tools/gen_guide.py
"""
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/ancient_foundry/textures/block"
OUT = ROOT / "docs/montagem.png"

SCALE = 3
TILE = 16 * SCALE
GAP = 2
BG = (24, 22, 30)
INK = (232, 230, 238)
MUTED = (160, 156, 172)
ACCENT = (107, 240, 230)
WALL_MARK = (255, 214, 102)


def font(size, bold=False):
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    try:
        return ImageFont.truetype(f"/usr/share/fonts/truetype/dejavu/{name}", size)
    except OSError:
        return ImageFont.load_default()


def texture(name):
    return Image.open(TEX / f"{name}.png").convert("RGBA").resize((TILE, TILE), Image.NEAREST)


def painted(seed, colors, weights, sparks=()):
    """A simple 16x16 stand-in for a vanilla block (the real textures belong to the game)."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rng.choices(colors, weights)[0] + (255,))
    for (x, y, c) in sparks:
        img.putpixel((x, y), c + (255,))
    return img.resize((TILE, TILE), Image.NEAREST)


def tiles():
    casing = texture("foundry_casing")
    wall = casing.copy()
    draw = ImageDraw.Draw(wall)
    # Dashed frame: this spot may also be glass or a hatch.
    for i in range(0, TILE, 6):
        draw.line([(i, 1), (min(i + 2, TILE - 1), 1)], fill=WALL_MARK, width=2)
        draw.line([(i, TILE - 2), (min(i + 2, TILE - 1), TILE - 2)], fill=WALL_MARK, width=2)
        draw.line([(1, i), (1, min(i + 2, TILE - 1))], fill=WALL_MARK, width=2)
        draw.line([(TILE - 2, i), (TILE - 2, min(i + 2, TILE - 1))], fill=WALL_MARK, width=2)
    magma = painted(5, [(74, 26, 18), (104, 36, 20), (140, 52, 22)], [5, 4, 2],
                    [(x, y, (245, 155, 43)) for (x, y) in ((3, 2), (4, 3), (5, 3), (6, 4), (10, 8), (11, 9), (12, 9), (2, 11), (3, 12), (4, 12), (9, 13), (13, 3))])
    lava = painted(7, [(224, 98, 26), (245, 140, 36), (255, 190, 70)], [4, 5, 2],
                   [(x, y, (255, 232, 140)) for (x, y) in ((2, 5), (3, 5), (4, 6), (9, 3), (10, 3), (11, 4), (6, 11), (7, 11), (8, 12), (12, 10))])
    return {
        "F": casing,
        "W": wall,
        "M": magma,
        "L": lava,
        "K": texture("ancient_core"),
        "V": texture("foundry_vent_top"),
        "C": texture("foundry_controller_front_idle"),
    }


# Rows run from the back of the foundry (top) to the front (bottom).
LAYERS = [
    ("Camada 1: base", ["FFFFF", "FMMMF", "FMMMF", "FMMMF", "FFFFF"]),
    ("Camada 2: controlador", ["FWWWF", "WLLLW", "WLKLW", "WLLLW", "FWCWF"]),
    ("Camada 3", ["FWWWF", "WLLLW", "WLLLW", "WLLLW", "FWWWF"]),
    ("Camada 4: teto", ["FFFFF", "FWWWF", "FWVWF", "FWWWF", "FFFFF"]),
]

LEGEND = [
    ("F", "Revestimento da Fundição (obrigatório)"),
    ("W", "Revestimento, Vidro ou Escotilha"),
    ("M", "Bloco de magma"),
    ("L", "Lava (fonte)"),
    ("K", "Núcleo Ancestral"),
    ("V", "Exaustor (ar livre em cima)"),
    ("C", "Controlador (virado para fora)"),
]


def main():
    art = tiles()
    grid = 5 * TILE + 4 * GAP
    margin = 28
    spacing = 34
    title_h = 64
    label_h = 30
    front_h = 26
    legend_rows = 2
    legend_h = legend_rows * (TILE + 14) + 20
    width = margin * 2 + 4 * grid + 3 * spacing
    height = title_h + label_h + grid + front_h + legend_h + margin

    img = Image.new("RGBA", (width, height), BG + (255,))
    draw = ImageDraw.Draw(img)
    draw.text((margin, 18), "Fundição Ancestral: montagem camada por camada", font=font(24, True), fill=INK)
    draw.text((width - margin, 26), "5 x 5 x 4 blocos, vista de cima", font=font(15), fill=MUTED, anchor="ra")

    for index, (title, rows) in enumerate(LAYERS):
        x0 = margin + index * (grid + spacing)
        y0 = title_h + label_h
        draw.text((x0, title_h + 2), title, font=font(17, True), fill=ACCENT)
        for row, line in enumerate(rows):
            for col, symbol in enumerate(line):
                img.alpha_composite(art[symbol], (x0 + col * (TILE + GAP), y0 + row * (TILE + GAP)))
        draw.text((x0 + grid // 2, y0 + grid + 5), "▲ frente", font=font(14), fill=MUTED, anchor="ma")

    ly = title_h + label_h + grid + front_h + 16
    col_w = (width - 2 * margin) // 4
    for i, (symbol, text) in enumerate(LEGEND):
        cx = margin + (i % 4) * col_w
        cy = ly + (i // 4) * (TILE + 14)
        img.alpha_composite(art[symbol], (cx, cy))
        # Wrap the label in two lines at most.
        words, lines, current = text.split(" "), [], ""
        for word in words:
            trial = (current + " " + word).strip()
            if draw.textlength(trial, font=font(14)) > col_w - TILE - 22 and current:
                lines.append(current)
                current = word
            else:
                current = trial
        lines.append(current)
        for n, line in enumerate(lines[:2]):
            draw.text((cx + TILE + 10, cy + 6 + n * 18), line, font=font(14), fill=INK)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(OUT)
    print(f"wrote {OUT.relative_to(ROOT)} ({width}x{height})")


if __name__ == "__main__":
    main()
