#!/usr/bin/env bash
#
# Minimal build for libcni-java: compile main + test sources and run JUnit.
# No external build tool required beyond a JDK and the jars in lib/.
#
# Usage:
#   ./build.sh            compile everything and run the tests
#   ./build.sh deps       download the dependency jars into lib/
set -euo pipefail
cd "$(dirname "$0")"

# ---- dependency download -------------------------------------------------
if [[ "${1:-}" == "deps" ]]; then
  mkdir -p lib
  GSON=2.11.0
  JUNIT=1.10.3
  fetch() { # fetch <url> <out>
    if [[ -f "$2" ]]; then
      echo "exists  $2"
    else
      code=$(curl -sS -m 60 -o "$2" -w "%{http_code}" "$1")
      echo "$code  $2"
    fi
  }
  fetch "https://repo1.maven.org/maven2/com/google/code/gson/gson/$GSON/gson-$GSON.jar" "lib/gson-$GSON.jar"
  fetch "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JUNIT/junit-platform-console-standalone-$JUNIT.jar" "lib/junit-platform-console-standalone-$JUNIT.jar"
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
