#!/usr/bin/env bash
# Dependency download helper, sourced by build.sh and tested in isolation by
# test/deps_fetch_test.sh.

# fetch_dependency <url> <out> <sha256> [timeout_seconds]
#
# Downloads <url> to <out> atomically (temp file + rename) and verifies the
# SHA-256 checksum. A file already at <out> is reused only when its checksum
# matches. Returns non-zero on any failure, never leaving a partial <out>.
fetch_dependency() {
  local url="$1" out="$2" want="$3" timeout="${4:-120}"
  local tmp="${out}.tmp.$$"

  if [[ -f "$out" ]] && [[ "$(sha256sum "$out" 2>/dev/null | cut -d' ' -f1)" == "$want" ]]; then
    return 0
  fi

  rm -f "$tmp"
  if ! curl --fail --show-error --location --retry 2 --max-time "$timeout" -o "$tmp" "$url"; then
    rm -f "$tmp"
    return 1
  fi
  if [[ "$(sha256sum "$tmp" 2>/dev/null | cut -d' ' -f1)" != "$want" ]]; then
    rm -f "$tmp"
    return 1
  fi
  mv "$tmp" "$out"
}
