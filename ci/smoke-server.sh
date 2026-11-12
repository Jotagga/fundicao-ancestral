#!/usr/bin/env bash
# Boots the packaged jar on a real Fabric server (production launcher, not the dev environment),
# builds a foundry with commands and checks that it forms and starts heating.
#
# Usage: ci/smoke-server.sh        (after ./gradlew assemble)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
prop() { grep "^$1=" "$ROOT/gradle.properties" | cut -d= -f2-; }

MC_VERSION="$(prop minecraft_version)"
LOADER_VERSION="$(prop loader_version)"
FABRIC_API_VERSION="$(prop fabric_api_version)"

# Minecraft 1.21.1 is played on Java 21; prefer it when the runner has it.
if [ -n "${JAVA_HOME_21_X64:-}" ]; then
	JAVA_BIN="$JAVA_HOME_21_X64/bin/java"
else
	JAVA_BIN="java"
fi

WORK="$ROOT/build/smoke-server"
rm -rf "$WORK"
mkdir -p "$WORK/mods"
cd "$WORK"

MOD_JAR="$(ls "$ROOT"/build/libs/*.jar | grep -v -- '-sources' | head -n 1)"
echo "== mod jar: $MOD_JAR"
cp "$MOD_JAR" mods/

echo "== downloading the Fabric server launcher and Fabric API"
INSTALLER_VERSION="$(curl -fsSL https://meta.fabricmc.net/v2/versions/installer \
	| python3 -c 'import json, sys; print(next(v["version"] for v in json.load(sys.stdin) if v["stable"]))')"
curl -fsSL -o server.jar \
	"https://meta.fabricmc.net/v2/versions/loader/${MC_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar"
curl -fsSL -o mods/fabric-api.jar \
	"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION//+/%2B}/fabric-api-${FABRIC_API_VERSION//+/%2B}.jar"
echo "   Minecraft $MC_VERSION, loader $LOADER_VERSION, installer $INSTALLER_VERSION, Fabric API $FABRIC_API_VERSION"
"$JAVA_BIN" -version 2>&1 | head -n 1

echo "eula=true" > eula.txt
cat > server.properties <<'PROPS'
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
view-distance=4
simulation-distance=4
max-tick-time=-1
sync-chunk-writes=false
PROPS

mkfifo console
"$JAVA_BIN" -Xmx2G -jar server.jar nogui < console > server.log 2>&1 &
SERVER_PID=$!
exec 3> console

cleanup() {
	if kill -0 "$SERVER_PID" 2>/dev/null; then
		echo "stop" >&3 || true
		sleep 5
		kill "$SERVER_PID" 2>/dev/null || true
	fi
}
trap cleanup EXIT

fail() {
	echo "::error::smoke test failed: $1"
	echo "---------------- last 80 lines of server.log ----------------"
	tail -n 80 server.log || true
	exit 1
}

# wait_for <regex> <seconds>: waits until the log contains the pattern.
wait_for() {
	local pattern="$1" seconds="$2"

	for _ in $(seq "$seconds"); do
		if grep -qE "$pattern" server.log; then
			return 0
		fi

		if ! kill -0 "$SERVER_PID" 2>/dev/null; then
			return 1
		fi

		sleep 1
	done

	return 1
}

say() {
	echo "$1" >&3
}

echo "== waiting for the server to start"
wait_for 'Done \([0-9.]+s\)!' 300 || fail "the server did not reach Done"
grep -E 'Done \(' server.log | tail -n 1

grep -q "ancient_foundry" server.log || fail "the mod is not in the list of loaded mods"
grep -q "Ancient Foundry loaded" server.log || fail "the mod did not log its start-up line"

echo "== building a foundry with commands"
say "forceload add -2 0 2 4"
sleep 4
say "fill -2 100 0 2 103 4 ancient_foundry:foundry_casing"
say "fill -1 100 1 1 100 3 minecraft:magma_block"
say "fill -1 101 1 1 102 3 minecraft:lava"
say "setblock 0 101 2 ancient_foundry:ancient_core"
say "setblock 0 103 2 ancient_foundry:foundry_vent"
say "setblock 0 102 0 ancient_foundry:foundry_glass"
say "setblock 2 101 2 ancient_foundry:foundry_input_hatch"
say "setblock -2 101 2 ancient_foundry:foundry_output_hatch"
say "setblock 0 101 0 ancient_foundry:foundry_controller[facing=north]"
sleep 4

say "data get block 0 101 0 Formed"
wait_for '0, 101, 0 has the following block data: 1b' 20 || fail "the foundry did not form on the production server"
echo "   foundry formed"

say "execute if block 0 101 0 ancient_foundry:foundry_controller[formed=true,lit=false]"
wait_for 'Test passed' 20 || fail "the controller block state is not formed=true,lit=false"

echo "== feeding it and checking that it heats up"
say "item replace block 0 101 0 container.0 with minecraft:basalt 64"
say "item replace block 0 101 0 container.1 with minecraft:soul_sand 64"
say "item replace block 0 101 0 container.2 with minecraft:coal_block 8"
sleep 6
say "data get block 0 101 0 Heat"
wait_for '0, 101, 0 has the following block data: [1-9][0-9]*$' 20 || fail "the foundry did not heat up"
echo "   $(grep -E '0, 101, 0 has the following block data: [0-9]+$' server.log | tail -n 1 | sed 's/.*data: /heat after ~6 s: /')"

say "execute if block 0 101 0 ancient_foundry:foundry_controller[formed=true,lit=true] run say foundry-is-lit"
wait_for 'foundry-is-lit' 20 || fail "the controller did not light up"

say "loot spawn 0 110 0 loot ancient_foundry:blocks/foundry_casing"
wait_for 'Dropped 1 \[Foundry Casing\]' 20 || fail "the casing loot table did not drop anything"

echo "== stopping the server"
say "stop"

for _ in $(seq 60); do
	kill -0 "$SERVER_PID" 2>/dev/null || break
	sleep 1
done

if kill -0 "$SERVER_PID" 2>/dev/null; then
	fail "the server did not stop"
fi

trap - EXIT

echo "== warnings and errors in the log"
grep -nE '/(WARN|ERROR)\]' server.log || echo "   none"

if grep -E '/ERROR\]|Exception' server.log | grep -qi 'ancient'; then
	fail "the log has errors that mention the mod"
fi

if grep -qE "Couldn't parse|Parsing error loading|Couldn't load tag|Failed to load (registries|datapacks)|Error loading registry data" server.log; then
	fail "the log has data pack errors"
fi

echo "== production server smoke test passed"
