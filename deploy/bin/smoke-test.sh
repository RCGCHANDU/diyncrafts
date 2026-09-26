#!/usr/bin/env bash
# Read-only smoke test of a deployed environment through its public edge.
#
#   deploy/bin/smoke-test.sh https://staging.example.com
#
# Creates no data and needs no credentials, so it is safe against production. Exits non-zero on the
# first failed check. Set CURL_CA_BUNDLE to trust a private CA (for example Caddy's local CA).
set -euo pipefail

BASE_URL=${1:?usage: smoke-test.sh <base-url>}
BASE_URL=${BASE_URL%/}
TIMEOUT=${SMOKE_TIMEOUT:-10}
failures=0
workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

pass() { printf 'PASS  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*"; failures=$((failures + 1)); }

# fetch PATH -> sets status, headers file, body file
fetch() {
  local path=$1; shift
  status=$(curl -sS --max-time "$TIMEOUT" -o "$workdir/body" -D "$workdir/headers" -w '%{http_code}' "$@" "$BASE_URL$path" || echo 000)
}
header() { grep -i "^$1:" "$workdir/headers" | tail -n 1 | cut -d: -f2- | tr -d '\r' | sed 's/^ //'; }
# expect DESCRIPTION VALUE PATTERN: passes when VALUE matches the glob PATTERN.
expect() {
  # shellcheck disable=SC2053 # the pattern is a glob on purpose
  if [[ $2 == $3 ]]; then pass "$1"; else fail "$1 (got: ${2:-nothing})"; fi
}

check_status() {
  local path=$1 expected=$2; shift 2
  fetch "$path" "$@"
  if [[ $status == "$expected" ]]; then pass "GET $path -> $status"; else fail "GET $path -> $status (expected $expected)"; fi
}

# Frontend shell and security headers.
check_status / 200
expect "/ is HTML" "$(header content-type)" 'text/html*'
expect "Content-Security-Policy present" "$(header content-security-policy)" "*default-src*"
expect "X-Content-Type-Options: nosniff" "$(header x-content-type-options)" nosniff
if [[ $BASE_URL == https://* ]]; then
  expect "HSTS present" "$(header strict-transport-security)" 'max-age=*'
fi
expect "HTML shell is not cached" "$(header cache-control)" '*no-cache*'
asset=$(grep -o '/assets/[^"]*\.js' "$workdir/body" | head -n 1 || true)

# Deep links fall back to the SPA shell.
check_status /video/1 200

# Hashed assets are immutable; a missing asset is a real 404.
if [[ -n $asset ]]; then
  check_status "$asset" 200
  expect "assets are immutable" "$(header cache-control)" '*immutable*'
else
  fail "no /assets/*.js reference found in index.html"
fi
check_status /assets/does-not-exist.js 404

# API (public, read-only endpoints).
check_status /api/categories 200
expect "/api/categories is JSON" "$(header content-type)" 'application/json*'
check_status '/api/videos/trending' 200

# WebSocket endpoint: SockJS info and a raw WebSocket upgrade (STOMP runs over it).
check_status /ws/info 200
expect "SockJS reports WebSocket support" "$(head -c 300 "$workdir/body")" '*"websocket":true*'
origin=$(sed -E 's#^(https?://[^/]+).*#\1#' <<<"$BASE_URL")
ws_status=$(curl -sS --http1.1 --max-time 5 -o /dev/null -w '%{http_code}' \
  -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' \
  -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' -H "Origin: $origin" \
  "$BASE_URL/ws/websocket" 2>/dev/null || true)
# curl stops after the 101 because nothing follows; the timeout is expected.
expect "WebSocket upgrade on /ws/websocket -> 101" "$ws_status" 101

# Internal endpoints are not reachable from outside.
check_status /actuator/health 404
check_status /actuator/prometheus 404

if ((failures > 0)); then
  echo "$failures check(s) failed"
  exit 1
fi
echo "all checks passed"
