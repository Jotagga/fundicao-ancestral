#!/usr/bin/env python3
"""Generates every texture of the mod (16x16 blocks, the GUI sheet and the mod icon).

Original pixel art drawn by code, so the art can be tweaked and rebuilt:

    python3 tools/gen_textures.py
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/ancient_foundry"
BLOCK = ASSETS / "textures/block"
GUI = ASSETS / "textures/gui"

# Palette ---------------------------------------------------------------
D0, D1, D2, D3, D4 = (24, 21, 28), (38, 34, 43), (48, 43, 54), (59, 53, 66), (78, 71, 87)
BR0, BR1, BR2, BR3 = (92, 64, 30), (140, 104, 46), (186, 146, 62), (232, 200, 110)
TE0, TE1, TE2, TE3 = (16, 92, 96), (31, 168, 170), (107, 240, 230), (217, 255, 250)
OR0, OR1, OR2, OR3 = (150, 54, 16), (224, 98, 26), (245, 155, 43), (255, 214, 102)
RED = (205, 48, 44)
VOID = (14, 12, 17)


def new(size=16):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def px(img, x, y, color, alpha=255):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), (color[0], color[1], color[2], alpha))


def rect(img, x0, y0, x1, y1, color, alpha=255):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, x, y, color, alpha)


def noise_fill(img, x0, y0, x1, y1, colors, weights, seed):
    rng = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, x, y, rng.choices(colors, weights)[0])


def plate(seed):
    """Riveted dark plate: the base of every machine block."""
    img = new()
    noise_fill(img, 0, 0, 15, 15, [D1, D2, D3], [2, 6, 2], seed)
    # Raised rim: light on top/left, shadow on bottom/right.
    for i in range(16):
        px(img, i, 0, D4)
        px(img, 0, i, D4)
        px(img, i, 15, D0)
        px(img, 15, i, D0)
    px(img, 15, 0, D2)
    px(img, 0, 15, D2)
    # Recessed panel.
    for i in range(3, 13):
        px(img, i, 3, D0)
        px(img, 3, i, D0)
        px(img, i, 12, D3)
        px(img, 12, i, D3)
    px(img, 12, 3, D1)
    px(img, 3, 12, D1)
    noise_fill(img, 4, 4, 11, 11, [D1, D2], [5, 3], seed + 1)
    # Bronze rivets in the rim.
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px(img, x, y, BR2)
    px(img, 1, 1, BR3)
    return img


def casing():
    return plate(11)


def glass():
    img = new()
    base = plate(23)
    # Keep the rim, replace the middle with tinted glass.
    for y in range(16):
        for x in range(16):
            if x < 2 or x > 13 or y < 2 or y > 13:
                img.putpixel((x, y), base.getpixel((x, y)))
            else:
                px(img, x, y, (96, 70, 84), 70)
    # Inner bevel of the frame.
    for i in range(2, 14):
        px(img, i, 2, D0, 255)
        px(img, 2, i, D0, 255)
        px(img, i, 13, D3, 255)
        px(img, 13, i, D3, 255)
    # Reflections.
    for (x, y) in ((4, 7), (5, 6), (6, 5), (7, 4), (4, 9), (5, 8)):
        px(img, x, y, (255, 255, 255), 130)
    for (x, y) in ((9, 11), (10, 10), (11, 9), (11, 11)):
        px(img, x, y, (255, 255, 255), 90)
    return img


def controller_front(mode):
    """mode: 'off' (structure incomplete), 'idle' (complete, fire out) or 'on' (fire lit)."""
    img = plate(31)
    noise_fill(img, 2, 2, 13, 13, [D1, D2, D3], [2, 6, 2], 32)
    # Display.
    rect(img, 3, 2, 12, 6, D0)
    rect(img, 4, 3, 11, 5, VOID)
    if mode == "off":
        px(img, 11, 3, RED)
        for x in (5, 6, 8, 9):
            px(img, x, 4, (70, 26, 30))
    elif mode == "idle":
        px(img, 11, 3, OR2)
        for x in range(5, 10):
            px(img, x, 4, OR1 if x % 2 else OR0)
    else:
        px(img, 11, 3, TE3)
        for x, y in ((4, 4), (5, 3), (6, 4), (7, 5), (8, 4), (9, 3), (10, 4)):
            px(img, x, y, TE2)
        for x, y in ((5, 4), (9, 4), (7, 4)):
            px(img, x, y, TE0)
    # Firebox with a bronze frame.
    rect(img, 3, 8, 12, 13, BR1)
    for i in range(3, 13):
        px(img, i, 8, BR3)
        px(img, i, 13, BR0)
    for i in range(8, 14):
        px(img, 3, i, BR2)
        px(img, 12, i, BR0)
    rect(img, 4, 9, 11, 12, VOID)
    if mode == "idle":
        for x, c in ((4, OR0), (5, OR1), (6, OR0), (8, OR1), (9, OR0), (10, OR1), (11, OR0)):
            px(img, x, 12, c)
        px(img, 7, 12, OR2)
    elif mode == "on":
        fire = {
            12: [TE1, TE2, TE3, TE3, TE3, TE3, TE2, TE1],
            11: [TE1, TE2, TE2, TE3, TE3, TE2, TE2, TE1],
            10: [None, TE1, TE2, TE2, TE3, TE2, TE1, None],
            9: [None, None, TE1, TE2, TE1, TE1, None, None],
        }
        for y, row in fire.items():
            for i, c in enumerate(row):
                if c:
                    px(img, 4 + i, y, c)
    # Grate bars over the firebox.
    for x in (6, 9):
        for y in range(9, 13):
            px(img, x, y, D3 if mode != "on" else D1)
    return img


ARROW_DOWN = [
    "...XX...",
    "...XX...",
    "...XX...",
    "XXXXXXXX",
    ".XXXXXX.",
    "..XXXX..",
    "...XX...",
]


def hatch(kind):
    img = plate(41 if kind == "input" else 47)
    rect(img, 3, 3, 12, 12, D0)
    rect(img, 4, 4, 11, 11, VOID)
    main, light, dark = (TE1, TE2, TE0) if kind == "input" else (OR1, OR3, OR0)
    rows = ARROW_DOWN if kind == "input" else ARROW_DOWN[::-1]
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch == "X":
                color = light if dx < 4 and dy in (0, 1, 2, 3) and kind == "input" else main
                if kind == "output":
                    color = light if dx < 4 else main
                px(img, 4 + dx, 4 + dy, color)
    # Slot lip under the arrow.
    for x in range(4, 12):
        px(img, x, 11, BR2 if 5 <= x <= 10 else BR0)
    px(img, 5, 11, BR3)
    # Shade the right edge of the arrow a little.
    for dy, row in enumerate(rows):
        last = row.rfind("X")
        if last >= 0:
            px(img, 4 + last, 4 + dy, dark)
    return img


def vent_top():
    img = plate(53)
    rect(img, 2, 2, 13, 13, D0)
    for y in range(3, 13):
        slat = (y - 3) % 3 == 2
        for x in range(3, 13):
            if slat:
                px(img, x, y, D4 if x < 12 else D3)
            else:
                glow = 6 <= x <= 9
                px(img, x, y, (58, 26, 18) if glow else VOID)
    for y in range(3, 13):
        px(img, 3, y, D3)
        px(img, 12, y, D1)
    return img


def vent_side():
    img = plate(59)
    noise_fill(img, 2, 2, 13, 13, [D1, D2, D3], [2, 6, 2], 60)
    for y in (4, 7, 10):
        for x in range(3, 13):
            px(img, x, y, VOID)
            px(img, x, y + 1, D4)
        px(img, 3, y, D0)
        px(img, 12, y, D0)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px(img, x, y, BR2)
    return img


def ancient_core():
    img = new()
    noise_fill(img, 0, 0, 15, 15, [D0, D1, D2], [3, 5, 2], 67)
    noise_fill(img, 2, 2, 13, 13, [D1, D2, D3], [3, 5, 2], 68)
    # Glowing cracks from the corners.
    for i in range(1, 4):
        for (x, y) in ((i, i), (15 - i, i), (i, 15 - i), (15 - i, 15 - i)):
            px(img, x, y, TE1 if i > 1 else TE0)
    for (x, y) in ((2, 1), (13, 2), (1, 13), (14, 13), (7, 1), (8, 14), (1, 8), (14, 7)):
        px(img, x, y, TE0)
    # Bronze housing.
    rect(img, 4, 4, 11, 11, BR1)
    for i in range(4, 12):
        px(img, i, 4, BR3)
        px(img, 4, i, BR2)
        px(img, i, 11, BR0)
        px(img, 11, i, BR0)
    # The core itself.
    rect(img, 5, 5, 10, 10, TE0)
    rect(img, 6, 6, 9, 9, TE1)
    rect(img, 7, 7, 8, 8, TE3)
    for (x, y) in ((6, 6), (7, 6), (6, 7)):
        px(img, x, y, TE2)
    for (x, y) in ((5, 5), (10, 5), (5, 10), (10, 10)):
        px(img, x, y, BR2)
    return img


# GUI -------------------------------------------------------------------
GUI_W, GUI_H = 176, 200
PANEL = (198, 198, 198)
WHITE = (255, 255, 255)
SHADE = (85, 85, 85)
SLOT = (139, 139, 139)
SLOT_DARK = (55, 55, 55)
GHOST = (118, 118, 118)
SCREEN = (26, 24, 32)

FLAME = [
    "......X.......",
    "......XX......",
    ".....XXX......",
    ".....XXXX.....",
    "....XXXXX..X..",
    "..X.XXXXXX.X..",
    "..XXXXXXXXXX..",
    ".XXXXXXXXXXXX.",
    ".XXXXXXXXXXXX.",
    "XXXXXXXXXXXXXX",
    "XXXXXXXXXXXXXX",
    "XXXXXXXXXXXXXX",
    ".XXXXXXXXXXXX.",
    "..XXXXXXXXXX..",
]


def inset(img, x, y, w, h, fill):
    """Sunken box, vanilla slot style: dark top/left edge, white bottom/right edge."""
    rect(img, x, y, x + w - 1, y + h - 1, fill)
    for i in range(w):
        px(img, x + i, y, SLOT_DARK)
        px(img, x + i, y + h - 1, WHITE)
    for i in range(h):
        px(img, x, y + i, SLOT_DARK)
        px(img, x + w - 1, y + i, WHITE)
    px(img, x + w - 1, y, fill if fill == SLOT else SLOT)
    px(img, x, y + h - 1, fill if fill == SLOT else SLOT)


def slot(img, sx, sy):
    inset(img, sx - 1, sy - 1, 18, 18, SLOT)


def flame_mask():
    return [[c == "X" for c in row] for row in FLAME]


def eroded(mask):
    h, w = len(mask), len(mask[0])
    out = [[False] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            if not mask[y][x]:
                continue
            ok = True
            for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < w and 0 <= ny < h and mask[ny][nx]):
                    ok = ny >= h  # the flat bottom does not erode
                    if not ok:
                        break
            out[y][x] = ok
    return out


def arrow_pixels():
    pts = []
    for x in range(0, 14):
        for y in range(6, 11):
            pts.append((x, y))
    for i in range(0, 8):
        half = 7 - i
        for y in range(8 - half, 8 + half + 1):
            pts.append((14 + i, y))
    return pts


def gui():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    w, h = GUI_W, GUI_H
    # Panel body with the vanilla-style rounded outline and bevels.
    rect(img, 0, 0, w - 1, h - 1, PANEL)
    for i in range(w):
        px(img, i, 0, (0, 0, 0))
        px(img, i, h - 1, (0, 0, 0))
    for i in range(h):
        px(img, 0, i, (0, 0, 0))
        px(img, w - 1, i, (0, 0, 0))
    for i in range(1, w - 1):
        px(img, i, 1, WHITE)
        px(img, i, 2, WHITE)
        px(img, i, h - 2, SHADE)
        px(img, i, h - 3, SHADE)
    for i in range(1, h - 1):
        px(img, 1, i, WHITE)
        px(img, 2, i, WHITE)
        px(img, w - 2, i, SHADE)
        px(img, w - 3, i, SHADE)
    for (x, y) in ((w - 2, 1), (w - 3, 1), (w - 2, 2), (w - 3, 2), (1, h - 2), (2, h - 2), (1, h - 3), (2, h - 3)):
        px(img, x, y, PANEL)
    # Rounded corners.
    for (cx, cy, sx, sy) in ((0, 0, 1, 1), (w - 1, 0, -1, 1), (0, h - 1, 1, -1), (w - 1, h - 1, -1, -1)):
        for (dx, dy) in ((0, 0), (1, 0), (0, 1)):
            img.putpixel((cx + dx * sx, cy + dy * sy), (0, 0, 0, 0))
        px(img, cx + sx, cy + sy, (0, 0, 0))

    # Heat column.
    inset(img, 11, 19, 10, 52, SCREEN)
    # Input slots with ghost icons.
    slot(img, 44, 20)
    slot(img, 66, 20)
    slot(img, 55, 57)
    rock = ["................", "................", "................", "......XXXX......", "....XXXXXXXX....",
            "...XXXXXXXXXX...", "...XXXXXXXXXXX..", "..XXXXXXXXXXXX..", "..XXXXXXXXXXXX..", "..XXXXXXXXXXX...",
            "...XXXXXXXXXX...", "...XXXXXXXXX....", "....XXXXXXX.....", "................", "................",
            "................"]
    soul = ["................", "................", ".....XXXXXX.....", "....XXXXXXXX....", "...XXXXXXXXXX...",
            "...XX..XX..XX...", "...XX..XX..XX...", "...XXXXXXXXXX...", "...XXXXXXXXXX...", "...XXXX..XXXX...",
            "...XXXXXXXXXX...", "...XXXXXXXXXX...", "...XX.XXXX.XX...", "...X...XX...X...", "................",
            "................"]
    for (sx, sy, art) in ((44, 20, rock), (66, 20, soul)):
        for y, row in enumerate(art):
            for x, ch in enumerate(row):
                if ch == "X":
                    px(img, sx + x, sy + y, GHOST)
    mask = flame_mask()
    # Ghost flame in the fuel slot.
    for y in range(14):
        for x in range(14):
            if mask[y][x]:
                px(img, 55 + 1 + x, 57 + 1 + y, GHOST)

    # Unlit flame between inputs and fuel.
    inner = eroded(mask)
    for y in range(14):
        for x in range(14):
            if mask[y][x]:
                px(img, 56 + x, 40 + y, SLOT if inner[y][x] else (110, 110, 110))

    # Empty arrow.
    for (x, y) in arrow_pixels():
        px(img, 92 + x, 38 + y, SLOT)

    # Result slots: a big one for the scrap, a normal one for leftovers.
    inset(img, 123, 33, 26, 26, SLOT)
    slot(img, 152, 38)

    # Read-out panel.
    inset(img, 7, 79, 162, 25, SCREEN)

    # Player inventory.
    for row in range(3):
        for col in range(9):
            slot(img, 8 + col * 18, 118 + row * 18)
    for col in range(9):
        slot(img, 8 + col * 18, 176)

    # --- sprites to the right of the panel ---
    # Heat column fill, 8x50 at (176, 0): orange up to the working mark (80%), soul-teal above it.
    for y in range(50):
        t = 1.0 - y / 49.0  # 0 at the bottom, 1 at the top
        if t >= 0.8:
            a, b, k = TE1, TE3, (t - 0.8) / 0.2
        elif t >= 0.45:
            a, b, k = OR2, OR3, (t - 0.45) / 0.35
        elif t >= 0.2:
            a, b, k = OR1, OR2, (t - 0.2) / 0.25
        else:
            a, b, k = OR0, OR1, t / 0.2
        c = tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))
        for x in range(8):
            shade = 1.12 if x == 1 else (0.86 if x == 7 else 1.0)
            px(img, 176 + x, y, tuple(min(255, int(v * shade)) for v in c))
    # Lit flame, 14x14 at (176, 52).
    core = eroded(inner)
    for y in range(14):
        for x in range(14):
            if mask[y][x]:
                c = TE1
                if inner[y][x]:
                    c = TE2
                if core[y][x] and y >= 7:
                    c = TE3
                px(img, 176 + x, 52 + y, c)
    # Filled arrow, 24x17 at (176, 68).
    for (x, y) in arrow_pixels():
        px(img, 176 + x, 68 + y, TE2 if y <= 7 else TE1)
    return img


def main():
    BLOCK.mkdir(parents=True, exist_ok=True)
    GUI.mkdir(parents=True, exist_ok=True)
    textures = {
        "foundry_casing": casing(),
        "foundry_glass": glass(),
        "foundry_controller_front_off": controller_front("off"),
        "foundry_controller_front_idle": controller_front("idle"),
        "foundry_controller_front_on": controller_front("on"),
        "foundry_input_hatch": hatch("input"),
        "foundry_output_hatch": hatch("output"),
        "foundry_vent_top": vent_top(),
        "foundry_vent_side": vent_side(),
        "ancient_core": ancient_core(),
    }
    for name, img in textures.items():
        img.save(BLOCK / f"{name}.png")
    gui().save(GUI / "foundry.png")
    textures["ancient_core"].resize((128, 128), Image.NEAREST).save(ASSETS / "icon.png")
    print(f"wrote {len(textures)} block textures, the GUI sheet and the icon")


if __name__ == "__main__":
    main()
