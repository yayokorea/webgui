#!/usr/bin/env bash
#
# CI smoke test for the Paper plugin: boot a REAL Paper server with it installed and assert
# that the server reaches "Done (…s)!", that WebGUI says it is enabled, and that nothing from
# WebGUI throws while enabling, running or disabling. Headless — a server needs no display.
# The server is started by run-paper (`:paper:runServer` / `:paper:runServer263`), which
# downloads that Paper build and puts the freshly built jar in plugins/.
#
# Usage: paper-smoke-test.sh <minecraft-version> [timeout-seconds] [--e2e]
#   e.g. paper-smoke-test.sh 1.21.11 600
#
#   --e2e   with the server up, also run paper/e2e/e2e.mjs against it (Paper 1.21.1 / 1.21.11
#           only; the protocol library behind it stops at 26.1). Needs `npm ci` in paper/e2e
#           first. Starts the server with the plugin's debug log on, which the test reads.
#
# Environment:
#   PAPER_PORT   port to listen on (default 25565)
#   GRADLE_ARGS  extra arguments for the Gradle runs, e.g. -Dorg.gradle.java.installations.paths=…
#
# The server runs in paper/run/<version>/ (git-ignored) and gets a new server.properties and
# eula.txt there; point it at a throwaway directory if that one holds a world you care about.
#
# Exit 0 = every assertion held; non-zero = the server never reached "Done", the plugin did not
# enable, WebGUI logged an error or an exception, or the e2e run failed.

set -uo pipefail

VERSION="${1:?usage: paper-smoke-test.sh <minecraft-version> [timeout-seconds] [--e2e]}"
shift
TIMEOUT=600
E2E=0
for arg in "$@"; do
    case "$arg" in
        --e2e) E2E=1 ;;
        *)     TIMEOUT="$arg" ;;
    esac
done
PORT="${PAPER_PORT:-25565}"

# 26.x runs on Java 25 and has a task of its own; every other version is the default task.
case "$VERSION" in
    26.3) TASK=(:paper:runServer263) ;;
    *)    TASK=(:paper:runServer "-PrunPaperVersion=${VERSION}") ;;
esac
(( E2E )) && TASK+=(-PrunPaperDebugLog=1)

RUN_DIR="paper/run/${VERSION}"
mkdir -p "$RUN_DIR"
printf 'eula=true\n' > "$RUN_DIR/eula.txt"
# Offline, flat, tiny view distance: first-run world generation stays fast, and the bots
# of the e2e run (which have no Mojang session) can join.
cat > "$RUN_DIR/server.properties" <<EOF
server-port=${PORT}
online-mode=false
enforce-secure-profile=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
max-players=20
view-distance=4
simulation-distance=3
pause-when-empty-seconds=-1
EOF
# A leftover log from an earlier run must not satisfy the assertions below.
rm -f "$RUN_DIR/logs/latest.log"

WORK="$(mktemp -d)"
LOG="$WORK/server.log"
CONSOLE="$WORK/console"
mkfifo "$CONSOLE"
# Both ends open: the server never sees EOF on its console, and we can type `stop` into it.
exec 3<>"$CONSOLE"

cleanup() {
    # setsid made the Gradle run a group leader of its own; its server JVM is in that group.
    if [[ -n "${PGID:-}" ]]; then
        kill -TERM -- "-$PGID" 2>/dev/null || true
        sleep 2
        kill -KILL -- "-$PGID" 2>/dev/null || true
    fi
    exec 3>&- 2>/dev/null || true
    rm -rf "$WORK"
}
trap cleanup EXIT

echo "==> Booting Paper ${VERSION} (${TASK[*]}, timeout ${TIMEOUT}s)"
# shellcheck disable=SC2086  # GRADLE_ARGS is meant to be word-split
setsid ./gradlew "${TASK[@]}" --console=plain --no-daemon ${GRADLE_ARGS:-} <&3 >"$LOG" 2>&1 &
GRADLE_PID=$!
PGID=$GRADLE_PID

DONE_RE='Done \([0-9.]+s\)!'
ENABLED_RE='WebGUI [^ ]+ enabled; settings in'
# Startup failures of the server or of the plugin. A plugin that fails to enable does not stop
# Paper from starting, so "Done" alone would pass — the explicit lines are what catch it.
FAIL_RE='Error occurred while enabling WebGUI|Error occurred while disabling WebGUI|Could not load( plugin)? .[^ ]*webgui|Cannot find main class .land\.webgui|Unsupported API version|Failed to start the minecraft server|Exception in thread "main"|Crash report|BUILD FAILED|FAILURE: Build failed'
# Anything WebGUI itself throws, wherever it is logged: its frames, or an error from its logger.
WEBGUI_ERR_RE='^[[:space:]]+at land\.webgui\.|Caused by: .*land\.webgui|ERROR\]: (\[(WebGUI|webgui)\]|webgui)'

result=1
elapsed=0
while (( elapsed < TIMEOUT )); do
    if grep -qE "$DONE_RE" "$LOG"; then
        result=0
        break
    fi
    if grep -qE "$FAIL_RE" "$LOG"; then
        echo "::error::Detected a startup or plugin-loading failure before 'Done'."
        result=1
        break
    fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
        echo "::error::The server process exited before reaching 'Done'."
        result=1
        break
    fi
    sleep 3
    elapsed=$((elapsed + 3))
done

if (( result == 0 )); then
    # The plugin's own line comes from onEnable, which runs before "Done"; a few seconds of
    # grace also lets the first tick (and the asynchronous update check) surface their errors.
    sleep 5
    if ! grep -qE "$ENABLED_RE" "$LOG"; then
        echo "::error::The server reached 'Done' but the WebGUI plugin never reported being enabled."
        result=1
    elif grep -qE "$FAIL_RE|$WEBGUI_ERR_RE" "$LOG"; then
        echo "::error::WebGUI logged an error or an exception while the server was starting."
        result=1
    else
        echo "✅ Paper ${VERSION} started with the WebGUI plugin enabled:"
        grep -E "Enabling WebGUI|webgui:|$ENABLED_RE|$DONE_RE" "$LOG" | sed 's/^/    /'
    fi
else
    (( elapsed >= TIMEOUT )) && echo "::error::Server did not reach 'Done' within ${TIMEOUT}s."
fi

if (( result == 0 && E2E )); then
    echo "==> Running the protocol test (paper/e2e)"
    if [[ ! -d paper/e2e/node_modules/minecraft-protocol ]]; then
        echo "::error::paper/e2e/node_modules is missing — run 'npm ci' in paper/e2e first."
        result=1
    else
        # Run from here with a script path: node must not pick up files from the working directory.
        node paper/e2e/e2e.mjs --run-dir "$RUN_DIR" --port "$PORT" --version "$VERSION" --console "$CONSOLE" \
            || result=1
    fi
fi

# Stop the server the way an operator would, and check the plugin disables without complaint.
if kill -0 "$GRADLE_PID" 2>/dev/null; then
    echo "stop" >&3
    for _ in $(seq 1 30); do
        kill -0 "$GRADLE_PID" 2>/dev/null || break
        sleep 1
    done
fi
if (( result == 0 )); then
    if grep -qE "$FAIL_RE|$WEBGUI_ERR_RE" "$LOG"; then
        echo "::error::WebGUI logged an error or an exception (see the log)."
        result=1
    elif ! grep -q 'Disabling WebGUI' "$LOG"; then
        echo "::error::The server did not shut down cleanly after 'stop' (WebGUI was never disabled)."
        result=1
    fi
fi

if (( result != 0 )); then
    echo "::group::Server log (last 200 lines)"
    tail -n 200 "$LOG"
    echo "::endgroup::"
fi

exit "$result"
