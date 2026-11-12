#!/usr/bin/env python3
"""Static checks on the mod's resources. Fails (exit 1) on the first class of problems that would
show up in game as a purple/black block, a missing name, a broken recipe or a missing drop.

    python3 tools/validate_resources.py
"""
import json
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NS = "ancient_foundry"
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets" / NS
DATA = RES / "data" / NS
JAVA_DIRS = [ROOT / "src/main/java", ROOT / "src/client/java"]

# Vanilla parents the models rely on, and the texture slots each of them needs.
VANILLA_PARENTS = {
    "minecraft:block/cube_all": {"all"},
    "minecraft:block/cube_bottom_top": {"top", "side", "bottom"},
    "minecraft:block/orientable": {"top", "front", "side"},
}

errors = []


def error(message: str) -> None:
    errors.append(message)


def load(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001 - report every broken file
        error(f"{path.relative_to(ROOT)}: invalid JSON: {exc}")
        return None


def png_size(path: Path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", data[16:24])


def registered_blocks():
    source = (ROOT / "src/main/java/dev/ancientfoundry/registry/ModBlocks.java").read_text(encoding="utf-8")
    return re.findall(r'register\("([a-z0-9_]+)"', source)


def check_all_json():
    count = 0
    for path in sorted(RES.rglob("*.json")) + sorted((ROOT / "src/gametest/resources").rglob("*.json")):
        load(path)
        count += 1
    return count


def model_path(ref: str) -> Path:
    namespace, _, path = ref.partition(":")
    return RES / "assets" / namespace / "models" / f"{path}.json"


def texture_path(ref: str) -> Path:
    namespace, _, path = ref.partition(":")
    return RES / "assets" / namespace / "textures" / f"{path}.png"


def check_model(ref: str, origin: str) -> None:
    """Follows a model to its vanilla parent and checks every texture it needs is a real 16x16 file."""
    if not ref.startswith(NS + ":"):
        error(f"{origin}: model {ref} is outside the mod")
        return
    path = model_path(ref)
    if not path.is_file():
        error(f"{origin}: model file missing for {ref}")
        return
    model = load(path)
    if model is None:
        return
    parent = model.get("parent")
    textures = dict(model.get("textures", {}))
    seen = {ref}
    while parent and parent.startswith(NS + ":"):
        if parent in seen:
            error(f"{origin}: model parent loop at {parent}")
            return
        seen.add(parent)
        parent_file = model_path(parent)
        if not parent_file.is_file():
            error(f"{origin}: parent model missing: {parent}")
            return
        parent_model = load(parent_file) or {}
        for key, value in parent_model.get("textures", {}).items():
            textures.setdefault(key, value)
        parent = parent_model.get("parent")
    if parent not in VANILLA_PARENTS:
        error(f"{origin}: {ref} ends in unknown parent {parent!r}")
        return
    missing = VANILLA_PARENTS[parent] - set(textures)
    if missing:
        error(f"{origin}: {ref} does not set textures {sorted(missing)} needed by {parent}")
    for slot, texture in textures.items():
        if texture.startswith("#"):
            continue
        if not texture.startswith(NS + ":"):
            continue
        file = texture_path(texture)
        if not file.is_file():
            error(f"{origin}: texture file missing for {texture} (slot {slot})")
        elif png_size(file) != (16, 16):
            error(f"{origin}: {texture} is {png_size(file)}, expected 16x16")


def check_blocks(blocks):
    for name in blocks:
        state_file = ASSETS / "blockstates" / f"{name}.json"
        if not state_file.is_file():
            error(f"blockstate missing for {name}")
            continue
        state = load(state_file) or {}
        variants = state.get("variants", {})
        if not variants:
            error(f"{name}: blockstate has no variants")
        for key, variant in variants.items():
            for entry in variant if isinstance(variant, list) else [variant]:
                check_model(entry["model"], f"blockstate {name}[{key}]")

        item_model = ASSETS / "models/item" / f"{name}.json"
        if not item_model.is_file():
            error(f"item model missing for {name}")
        else:
            check_model(f"{NS}:item/{name}", f"item {name}")

        loot = DATA / "loot_table/blocks" / f"{name}.json"
        if not loot.is_file():
            error(f"loot table missing for {name}")
        else:
            table = load(loot) or {}
            names = [e.get("name") for p in table.get("pools", []) for e in p.get("entries", [])]
            if f"{NS}:{name}" not in names:
                error(f"loot table of {name} does not drop the block itself")

        recipe = DATA / "recipe" / f"{name}.json"
        if not recipe.is_file():
            error(f"recipe missing for {name}")
        unlock = DATA / "advancement/recipes/misc" / f"{name}.json"
        if not unlock.is_file():
            error(f"recipe unlock missing for {name}")

    # The controller's blockstate must cover every combination of its three properties.
    controller = load(ASSETS / "blockstates/foundry_controller.json") or {}
    expected = {f"facing={f},formed={fo},lit={l}" for f in ("north", "east", "south", "west")
                for fo in ("false", "true") for l in ("false", "true")}
    got = set(controller.get("variants", {}))
    if got != expected:
        error(f"controller blockstate variants differ: missing {sorted(expected - got)}, extra {sorted(got - expected)}")


def check_recipes(blocks):
    known = {f"{NS}:{b}" for b in blocks}
    for path in sorted((DATA / "recipe").glob("*.json")):
        recipe = load(path) or {}
        result = recipe.get("result", {}).get("id")
        if result not in known:
            error(f"{path.name}: result {result} is not a block of the mod")
        ingredients = list(recipe.get("key", {}).values()) + list(recipe.get("ingredients", []))
        for ingredient in ingredients:
            item = ingredient.get("item", "")
            if item.startswith(NS + ":") and item not in known:
                error(f"{path.name}: unknown ingredient {item}")
        if recipe.get("type") == "minecraft:crafting_shaped":
            symbols = set("".join(recipe["pattern"])) - {" "}
            if symbols != set(recipe["key"]):
                error(f"{path.name}: pattern symbols {sorted(symbols)} do not match key {sorted(recipe['key'])}")
            if len({len(row) for row in recipe["pattern"]}) != 1:
                error(f"{path.name}: pattern rows have different widths")


def check_tags(blocks):
    pickaxe = load(RES / "data/minecraft/tags/block/mineable/pickaxe.json") or {}
    for name in blocks:
        if f"{NS}:{name}" not in pickaxe.get("values", []):
            error(f"{name} is not in mineable/pickaxe")
    for tag in ("foundry_rock", "foundry_soul"):
        if not (DATA / "tags/item" / f"{tag}.json").is_file():
            error(f"item tag missing: {tag}")


def check_lang(blocks):
    langs = {}
    for code in ("en_us", "pt_br"):
        langs[code] = load(ASSETS / "lang" / f"{code}.json") or {}
    if set(langs["en_us"]) != set(langs["pt_br"]):
        only_en = sorted(set(langs["en_us"]) - set(langs["pt_br"]))
        only_pt = sorted(set(langs["pt_br"]) - set(langs["en_us"]))
        error(f"lang files differ: only en_us {only_en}, only pt_br {only_pt}")

    needed = set()
    for name in blocks:
        needed.add(f"block.{NS}.{name}")
        needed.add(f"block.{NS}.{name}.tooltip")
    java = "\n".join(p.read_text(encoding="utf-8") for d in JAVA_DIRS for p in d.rglob("*.java"))
    # Every complete key written in the code.
    for key in re.findall(r'"((?:gui|message|container|itemGroup|tooltip)\.' + NS + r'[a-z0-9_.]*)"', java):
        if key.endswith("."):
            continue
        needed.add(key)
    needed.add(f"itemGroup.{NS}")
    # Keys built at run time from enums.
    core = (ROOT / "src/main/java/dev/ancientfoundry/logic/FoundryCore.java").read_text(encoding="utf-8")
    statuses = re.search(r"enum Status \{\s*([A-Z_,\s]+);", core).group(1).replace(" ", "").replace("\n", "").replace("\t", "").split(",")
    for status in statuses:
        needed.add(f"gui.{NS}.status.{status.lower()}")
    structure = (ROOT / "src/main/java/dev/ancientfoundry/multiblock/FoundryStructure.java").read_text(encoding="utf-8")
    parts = re.search(r"enum Part \{\s*([A-Z_,\s]+)\}", structure).group(1).replace(" ", "").replace("\n", "").replace("\t", "").split(",")
    for part in parts:
        needed.add(f"message.{NS}.part.{part.lower()}")
    for layer in range(1, 6):
        needed.add(f"message.{NS}.layer.{layer}")

    for code, table in langs.items():
        for key in sorted(needed - set(table)):
            error(f"{code}.json: missing key {key}")
        for key, value in table.items():
            # Minecraft only understands %s, %n$s and %%.
            stripped = re.sub(r"%%|%(\d+\$)?s", "", value)
            if "%" in stripped:
                error(f"{code}.json: {key} has an unsupported format specifier: {value!r}")
    for key in sorted(set(langs["en_us"])):
        a = len(re.findall(r"%(\d+\$)?s", langs["en_us"][key]))
        b = len(re.findall(r"%(\d+\$)?s", langs["pt_br"].get(key, "")))
        if a != b:
            error(f"{key}: en_us has {a} arguments, pt_br has {b}")
    return len(needed)


def check_misc():
    gui = ASSETS / "textures/gui/foundry.png"
    if not gui.is_file() or png_size(gui) != (256, 256):
        error("GUI texture must be a 256x256 PNG")
    icon = ASSETS / "icon.png"
    if not icon.is_file():
        error("mod icon missing")
    meta = load(RES / "fabric.mod.json") or {}
    for group in meta.get("entrypoints", {}).values():
        for entry in group:
            relative = entry.replace(".", "/") + ".java"
            if not any((d / relative).is_file() for d in JAVA_DIRS):
                error(f"fabric.mod.json: entrypoint class not found: {entry}")
    if meta.get("id") != NS:
        error("fabric.mod.json id does not match the namespace")
    java = "\n".join(p.read_text(encoding="utf-8") for d in JAVA_DIRS for p in d.rglob("*.java"))
    for ref in re.findall(r'id\("(textures/[a-z0-9_/.]+)"\)', java):
        if not (ASSETS / ref).is_file():
            error(f"code refers to a missing texture: {ref}")


def main() -> int:
    blocks = registered_blocks()
    if len(blocks) != 7:
        error(f"expected 7 registered blocks, found {blocks}")
    files = check_all_json()
    check_blocks(blocks)
    check_recipes(blocks)
    check_tags(blocks)
    keys = check_lang(blocks)
    check_misc()

    if errors:
        print(f"{len(errors)} problem(s):")
        for message in errors:
            print("  - " + message)
        return 1

    print(f"resources OK: {len(blocks)} blocks, {files} JSON files, {keys} translation keys in 2 languages")
    return 0


if __name__ == "__main__":
    sys.exit(main())
