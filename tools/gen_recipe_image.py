#!/usr/bin/env python3
"""Builds docs/receitas.png from in-game screenshots of the crafting table.

The client test (./gradlew runClientTest) crafts every recipe of the mod and saves
screenshots/recipe_<block>.png. This script cuts the crafting grid out of each one and lays
them out with the block names and the ingredient lists, read from the recipe files.

    python3 tools/gen_recipe_image.py build/run/clientTest/screenshots
"""
import json
import sys
from collections import Counter
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
RECIPES = RES / "data/ancient_foundry/recipe"
LANG = RES / "assets/ancient_foundry/lang/pt_br.json"
OUT = ROOT / "docs/receitas.png"

ORDER = ["foundry_casing", "foundry_glass", "foundry_controller", "ancient_core", "foundry_vent",
         "foundry_input_hatch", "foundry_output_hatch"]

# Portuguese names of the vanilla ingredients, as the game shows them.
VANILLA = {
    "minecraft:polished_blackstone_bricks": "Tijolos de pedra-negra polida",
    "minecraft:iron_ingot": "Barra de ferro",
    "minecraft:magma_block": "Bloco de magma",
    "minecraft:glass": "Vidro",
    "minecraft:comparator": "Comparador de redstone",
    "minecraft:diamond": "Diamante",
    "minecraft:blast_furnace": "Alto-forno",
    "minecraft:redstone_block": "Bloco de redstone",
    "minecraft:crying_obsidian": "Obsidiana chorona",
    "minecraft:gold_block": "Bloco de ouro",
    "minecraft:ancient_debris": "Detritos ancestrais",
    "minecraft:iron_bars": "Grades de ferro",
    "minecraft:soul_campfire": "Fogueira das almas",
    "minecraft:hopper": "Funil",
    "minecraft:dropper": "Liberador",
}

# The vanilla crafting screen is 176x166 GUI pixels; this box holds the grid, the arrow and the
# result, and stops short of the screen title above and the inventory label below.
SCREEN_W, SCREEN_H = 176, 166
BOX = (26, 16, 150, 71)
PANEL = (198, 198, 198)
PAD = 4  # GUI pixels of plain panel added above and below the cut-out

BG = (24, 22, 30)
INK = (232, 230, 238)
MUTED = (160, 156, 172)
ACCENT = (107, 240, 230)
GOLD = (255, 214, 102)


def font(size, bold=False):
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    try:
        return ImageFont.truetype(f"/usr/share/fonts/truetype/dejavu/{name}", size)
    except OSError:
        return ImageFont.load_default()


def gui_scale(width, height):
    """The automatic GUI scale Minecraft picks for a window of this size."""
    scale = 1
    while width // (scale + 1) >= 320 and height // (scale + 1) >= 240:
        scale += 1
    return scale


def crop_grid(path):
    shot = Image.open(path).convert("RGB")
    scale = gui_scale(*shot.size)
    scaled_w = -(-shot.width // scale)
    scaled_h = -(-shot.height // scale)
    left = (scaled_w - SCREEN_W) // 2
    top = (scaled_h - SCREEN_H) // 2
    box = tuple(int((offset + value) * scale) for offset, value in zip((left, top, left, top), BOX))
    grid = shot.crop(box)
    padded = Image.new("RGB", (grid.width, grid.height + 2 * PAD * scale), PANEL)
    padded.paste(grid, (0, PAD * scale))
    return padded


def ingredient_text(recipe, names):
    if "key" in recipe:
        symbols = Counter("".join(recipe["pattern"]).replace(" ", ""))
        counts = Counter()
        for symbol, amount in symbols.items():
            counts[recipe["key"][symbol]["item"]] += amount
    else:
        counts = Counter(entry["item"] for entry in recipe["ingredients"])
    return ", ".join(f"{amount}× {names[item]}" for item, amount in counts.items())


def wrap(draw, text, fnt, width):
    lines, current = [], ""
    for word in text.split(" "):
        trial = (current + " " + word).strip()
        if draw.textlength(trial, font=fnt) > width and current:
            lines.append(current)
            current = word
        else:
            current = trial
    lines.append(current)
    return lines


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    shots = Path(sys.argv[1])
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    names = dict(VANILLA)
    for block in ORDER:
        names[f"ancient_foundry:{block}"] = lang[f"block.ancient_foundry.{block}"]

    # Inside the ingredient lists the casing goes by its short name.
    short = dict(names)
    short["ancient_foundry:foundry_casing"] = "Revestimento"

    grids = {block: crop_grid(shots / f"recipe_{block}.png") for block in ORDER}
    grid_w, grid_h = next(iter(grids.values())).size

    margin, gap_x, gap_y = 36, 40, 30
    header_h, title_h, text_h = 74, 30, 62
    cell_h = title_h + grid_h + text_h
    columns, rows = 2, 4
    width = margin * 2 + columns * grid_w + (columns - 1) * gap_x
    height = header_h + rows * cell_h + (rows - 1) * gap_y + margin

    img = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(img)
    draw.text((margin, 20), "Fundição Ancestral: receitas", font=font(24, True), fill=INK)
    draw.text((width - margin, 28), "todas na bancada de trabalho", font=font(15), fill=MUTED, anchor="ra")

    for index, block in enumerate(ORDER):
        recipe = json.loads((RECIPES / f"{block}.json").read_text(encoding="utf-8"))
        x = margin + (index % columns) * (grid_w + gap_x)
        y = header_h + (index // columns) * (cell_h + gap_y)
        title = names[f"ancient_foundry:{block}"]
        draw.text((x, y), title, font=font(17, True), fill=ACCENT)
        note = []
        if recipe["result"]["count"] > 1:
            note.append(f"rende {recipe['result']['count']}")
        if "ingredients" in recipe:
            note.append("em qualquer posição")
        if note:
            draw.text((x + grid_w, y + 3), ", ".join(note), font=font(13), fill=GOLD, anchor="ra")
        img.paste(grids[block], (x, y + title_h))
        for n, line in enumerate(wrap(draw, ingredient_text(recipe, short), font(13), grid_w)[:3]):
            draw.text((x, y + title_h + grid_h + 7 + n * 17), line, font=font(13), fill=INK)

    # The spare cell holds two hints.
    x = margin + (len(ORDER) % columns) * (grid_w + gap_x)
    y = header_h + (len(ORDER) // columns) * (cell_h + gap_y)
    hints = [
        "Comece pelo Revestimento: ele entra em quase todas as outras receitas.",
        "No jogo, as receitas aparecem no livro de receitas quando você pega um tijolo de pedra-negra polida ou um Revestimento.",
        "A fundição mais simples usa 79 revestimentos: 20 fabricações.",
    ]
    line_y = y + title_h
    for hint in hints:
        for line in wrap(draw, hint, font(14), grid_w):
            draw.text((x, line_y), line, font=font(14), fill=MUTED)
            line_y += 19
        line_y += 10

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(f"wrote {OUT.relative_to(ROOT)} ({width}x{height})")


if __name__ == "__main__":
    main()
