#!/usr/bin/env bash
#
# Minimal build for libcni-java: compile main + test sources and run JUnit.
# No external build tool required beyond a JDK and the jars in lib/.
#
# Usage:
#   ./build.sh            compile everything and run the tests
#   ./build.sh deps       download the dependency jars into lib/ (checksum-verified)
set -euo pipefail
cd "$(dirname "$0")"

# shellcheck source=scripts/fetch.sh
source "$PWD/scripts/fetch.sh"

# ---- dependency download -------------------------------------------------
if [[ "${1:-}" == "deps" ]]; then
  mkdir -p lib
  GSON=2.11.0
  JUNIT=1.10.3
  fetch_dependency "https://repo1.maven.org/maven2/com/google/code/gson/gson/$GSON/gson-$GSON.jar" \
    "lib/gson-$GSON.jar" \
    "57928d6e5a6edeb2abd3770a8f95ba44dce45f3b23b7a9dc2b309c581552a78b" || {
      echo "error: failed to fetch gson" >&2
      exit 1
    }
  fetch_dependency "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JUNIT/junit-platform-console-standalone-$JUNIT.jar" \
    "lib/junit-platform-console-standalone-$JUNIT.jar" \
    "1455afad75d4eb4f44b493febc4a1bc32bc5f02eb25679361abd55e0f6421050" || {
      echo "error: failed to fetch junit" >&2
      exit 1
    }
  echo "dependencies ok"
  exit 0
fi

# ---- classpath -----------------------------------------------------------
RUNNER=$(ls lib/junit-platform-console-standalone-*.jar 2>/dev/null | head -1 || true)
DEPS=$(ls lib/*.jar 2>/dev/null | grep -v junit-platform-console-standalone | paste -sd: - || true)

if [[ -z "$RUNNER" ]]; then
  echo "error: junit runner missing; run './build.sh deps' first" >&2
  exit 1
fi

mkdir -p out/classes out/test-classes

# ---- compile -------------------------------------------------------------
echo "== compiling main sources =="
find src/main/java -name '*.java' > /tmp/libcni-sources.txt
if [[ -s /tmp/libcni-sources.txt ]]; then
  javac -encoding UTF-8 -cp "${DEPS}" -d out/classes @/tmp/libcni-sources.txt
else
  echo "(no main sources)"
fi

echo "== compiling test sources =="
find src/test/java -name '*.java' > /tmp/libcni-test-sources.txt
if [[ -s /tmp/libcni-test-sources.txt ]]; then
  javac -encoding UTF-8 -cp "out/classes:${DEPS}:${RUNNER}" -d out/test-classes @/tmp/libcni-test-sources.txt
else
  echo "(no test sources)"
fi

# ---- run tests -----------------------------------------------------------
echo "== running tests =="
java -jar "$RUNNER" \
  --class-path "out/classes:out/test-classes:${DEPS}" \
  --scan-class-path \
  --disable-banner
