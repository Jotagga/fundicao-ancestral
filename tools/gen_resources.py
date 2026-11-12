#!/usr/bin/env python3
"""Writes the mod's JSON resources: blockstates, models, loot tables, recipes, recipe unlocks and tags.

Kept as a script so the files stay consistent with each other:

    python3 tools/gen_resources.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
NS = "ancient_foundry"
ASSETS = RES / "assets" / NS
DATA = RES / "data" / NS
MC_DATA = RES / "data" / "minecraft"

BLOCKS = [
    "foundry_controller",
    "foundry_casing",
    "foundry_glass",
    "foundry_input_hatch",
    "foundry_output_hatch",
    "foundry_vent",
    "ancient_core",
]


def write(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def mod(path: str) -> str:
    return f"{NS}:{path}"


# ---------------------------------------------------------------- blockstates and models
def blockstates_and_models() -> None:
    for name in ("foundry_casing", "foundry_glass", "foundry_input_hatch", "foundry_output_hatch", "ancient_core"):
        write(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": mod(f"block/{name}")}}})
        write(ASSETS / "models/block" / f"{name}.json",
              {"parent": "minecraft:block/cube_all", "textures": {"all": mod(f"block/{name}")}})

    write(ASSETS / "blockstates/foundry_vent.json", {"variants": {"": {"model": mod("block/foundry_vent")}}})
    write(ASSETS / "models/block/foundry_vent.json", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": mod("block/foundry_vent_top"),
            "side": mod("block/foundry_vent_side"),
            "bottom": mod("block/foundry_casing"),
        },
    })

    # Controller: one model per look, rotated by facing.
    looks = {"foundry_controller": "off", "foundry_controller_idle": "idle", "foundry_controller_on": "on"}
    for model, look in looks.items():
        write(ASSETS / "models/block" / f"{model}.json", {
            "parent": "minecraft:block/orientable",
            "textures": {
                "top": mod("block/foundry_casing"),
                "front": mod(f"block/foundry_controller_front_{look}"),
                "side": mod("block/foundry_casing"),
            },
        })

    rotation = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in rotation.items():
        for formed in ("false", "true"):
            for lit in ("false", "true"):
                if formed == "false":
                    model = "foundry_controller"
                elif lit == "true":
                    model = "foundry_controller_on"
                else:
                    model = "foundry_controller_idle"
                variant = {"model": mod(f"block/{model}")}
                if y:
                    variant["y"] = y
                variants[f"facing={facing},formed={formed},lit={lit}"] = variant
    write(ASSETS / "blockstates/foundry_controller.json", {"variants": variants})

    for name in BLOCKS:
        parent = "foundry_controller_idle" if name == "foundry_controller" else name
        write(ASSETS / "models/item" / f"{name}.json", {"parent": mod(f"block/{parent}")})


# ---------------------------------------------------------------- loot tables
def loot_tables() -> None:
    for name in BLOCKS:
        write(DATA / "loot_table/blocks" / f"{name}.json", {
            "type": "minecraft:block",
            "pools": [{
                "bonus_rolls": 0.0,
                "conditions": [{"condition": "minecraft:survives_explosion"}],
                "entries": [{"type": "minecraft:item", "name": mod(name)}],
                "rolls": 1.0,
            }],
            "random_sequence": mod(f"blocks/{name}"),
        })


# ---------------------------------------------------------------- recipes
def item(name: str) -> dict:
    return {"item": name}


def shaped(name, pattern, key, count=1, category="misc"):
    write(DATA / "recipe" / f"{name}.json", {
        "type": "minecraft:crafting_shaped",
        "category": category,
        "key": {symbol: item(value) for symbol, value in key.items()},
        "pattern": pattern,
        "result": {"count": count, "id": mod(name)},
    })


def shapeless(name, ingredients, count=1, category="misc"):
    write(DATA / "recipe" / f"{name}.json", {
        "type": "minecraft:crafting_shapeless",
        "category": category,
        "ingredients": [item(value) for value in ingredients],
        "result": {"count": count, "id": mod(name)},
    })


def unlock(name, trigger_items):
    """Recipe-book unlock: the recipe shows up once the player has held one of the trigger items."""
    write(DATA / "advancement/recipes/misc" / f"{name}.json", {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_the_recipe": {
                "conditions": {"recipe": mod(name)},
                "trigger": "minecraft:recipe_unlocked",
            },
            "has_trigger_item": {
                "conditions": {"items": [{"items": trigger_items}]},
                "trigger": "minecraft:inventory_changed",
            },
        },
        "requirements": [["has_the_recipe", "has_trigger_item"]],
        "rewards": {"recipes": [mod(name)]},
    })


def recipes() -> None:
    casing = mod("foundry_casing")

    shaped("foundry_casing", ["BIB", "IMI", "BIB"], {
        "B": "minecraft:polished_blackstone_bricks",
        "I": "minecraft:iron_ingot",
        "M": "minecraft:magma_block",
    }, count=4, category="building")
    unlock("foundry_casing", ["minecraft:polished_blackstone_bricks", "minecraft:ancient_debris", "minecraft:netherite_scrap"])

    shaped("foundry_glass", [" G ", "GCG", " G "], {
        "G": "minecraft:glass",
        "C": casing,
    }, count=4, category="building")
    unlock("foundry_glass", [casing])

    shaped("foundry_controller", ["CKC", "DBD", "CRC"], {
        "C": casing,
        "K": "minecraft:comparator",
        "D": "minecraft:diamond",
        "B": "minecraft:blast_furnace",
        "R": "minecraft:redstone_block",
    })
    unlock("foundry_controller", [casing])

    shaped("ancient_core", ["OGO", "MAM", "OGO"], {
        "O": "minecraft:crying_obsidian",
        "G": "minecraft:gold_block",
        "M": "minecraft:magma_block",
        "A": "minecraft:ancient_debris",
    })
    unlock("ancient_core", [casing, "minecraft:ancient_debris"])

    shaped("foundry_vent", ["III", "CSC", "C C"], {
        "I": "minecraft:iron_bars",
        "S": "minecraft:soul_campfire",
        "C": casing,
    })
    unlock("foundry_vent", [casing])

    shapeless("foundry_input_hatch", [casing, "minecraft:hopper"])
    unlock("foundry_input_hatch", [casing])

    shapeless("foundry_output_hatch", [casing, "minecraft:dropper"])
    unlock("foundry_output_hatch", [casing])


# ---------------------------------------------------------------- tags
def tags() -> None:
    write(MC_DATA / "tags/block/mineable/pickaxe.json", {"replace": False, "values": [mod(b) for b in BLOCKS]})
    write(MC_DATA / "tags/block/needs_iron_tool.json",
          {"replace": False, "values": [mod(b) for b in BLOCKS if b != "foundry_glass"]})
    write(DATA / "tags/item/foundry_rock.json", {"replace": False, "values": [
        "minecraft:basalt",
        "minecraft:smooth_basalt",
        "minecraft:polished_basalt",
        "minecraft:blackstone",
        "minecraft:netherrack",
    ]})
    write(DATA / "tags/item/foundry_soul.json", {"replace": False, "values": [
        "minecraft:soul_sand",
        "minecraft:soul_soil",
    ]})


def main() -> None:
    blockstates_and_models()
    loot_tables()
    recipes()
    tags()
    print("resources written")


if __name__ == "__main__":
    main()
