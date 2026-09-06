#!/usr/bin/env bash
#
# Isolated test of the fetch_dependency helper: exercises successful download,
# HTTP 404, checksum mismatch and replacement of a corrupt existing file, all
# against a local HTTP server (no dependency on Maven Central or the real jars).
set -euo pipefail
cd "$(dirname "$0")/.."

# shellcheck source=scripts/fetch.sh
source "$PWD/scripts/fetch.sh"

command -v python3 >/dev/null || { echo "skip: python3 not available"; exit 0; }

TMP=$(mktemp -d)
SERVER_PID=""
cleanup() {
  [[ -n "$SERVER_PID" ]] && kill "$SERVER_PID" 2>/dev/null || true
  rm -rf "$TMP"
}
trap cleanup EXIT

SERVE_DIR="$TMP/serve"
mkdir -p "$SERVE_DIR"
printf 'hello world\n' > "$SERVE_DIR/ok.txt"
OK_SHA=$(sha256sum "$SERVE_DIR/ok.txt" | cut -d' ' -f1)

PORT=$(( (RANDOM % 20000) + 20000 ))
python3 -m http.server "$PORT" --directory "$SERVE_DIR" >/dev/null 2>&1 &
SERVER_PID=$!
sleep 1

BASE="http://127.0.0.1:$PORT"

fail() { echo "FAIL: $1" >&2; exit 1; }

# 1. successful download
OUT1="$TMP/out1.txt"
fetch_dependency "$BASE/ok.txt" "$OUT1" "$OK_SHA" || fail "successful download failed"
[[ "$(cat "$OUT1")" == "hello world" ]] || fail "wrong content after download"
echo "PASS success"

# 2. HTTP 404 must fail and leave no partial file
OUT2="$TMP/out2.txt"
if fetch_dependency "$BASE/missing.txt" "$OUT2" "deadbeef"; then
  fail "404 should have failed"
fi
[[ ! -e "$OUT2" ]] || fail "404 left a partial file"
echo "PASS 404 no partial"

# 3. checksum mismatch must fail and leave no file
OUT3="$TMP/out3.txt"
if fetch_dependency "$BASE/ok.txt" "$OUT3" "deadbeef"; then
  fail "checksum mismatch should have failed"
fi
[[ ! -e "$OUT3" ]] || fail "checksum mismatch left a file"
echo "PASS checksum mismatch no file"

# 4. pre-existing corrupt file is replaced
OUT4="$TMP/out4.txt"
echo "corrupt" > "$OUT4"
fetch_dependency "$BASE/ok.txt" "$OUT4" "$OK_SHA" || fail "re-download over corrupt file failed"
[[ "$(cat "$OUT4")" == "hello world" ]] || fail "corrupt file not replaced"
echo "PASS corrupt file replaced"

echo "ALL DEPENDENCY TESTS PASSED"
