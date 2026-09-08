#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
COOKIE_FILE="$(mktemp)"
WORK_DIR="$(mktemp -d)"
TOKEN="$(date +%s)${RANDOM}"
SKILL_NAME="suite-smoke-member-${TOKEN}"
SUITE_SLUG="suite-smoke-${TOKEN}"
SKILL_ID=""
SKILL_VERSION_ID=""
SUITE_ID=""
SMOKE_ADMIN_USERNAME="${SMOKE_ADMIN_USERNAME:-}"
SMOKE_ADMIN_PASSWORD="${SMOKE_ADMIN_PASSWORD:-}"
AUTH_HEADERS=()

json_field() {
  JSON_INPUT="$1" python3 - "$2" <<'PY'
import json
import os
import sys

value = json.loads(os.environ["JSON_INPUT"])
for part in sys.argv[1].split("."):
    value = value[int(part)] if part.isdigit() else value[part]
print(json.dumps(value, ensure_ascii=False) if isinstance(value, (dict, list)) else value)
PY
}

assert_code() {
  local description="$1"
  local body="$2"
  local expected="$3"
  local actual
  actual="$(json_field "$body" code)"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL: $description (expected code $expected, got $actual)"
    exit 1
  fi
  echo "PASS: $description"
}

cleanup() {
  if [[ -n "$SUITE_ID" && -n "${CSRF_TOKEN:-}" ]]; then
    curl -sS -o /dev/null -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
      "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
      -X DELETE "$BASE_URL/api/web/suites/$SUITE_ID" || true
  fi
  if [[ -n "$SKILL_ID" && -n "${CSRF_TOKEN:-}" ]]; then
    curl -sS -o /dev/null -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
      "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
      -X DELETE "$BASE_URL/api/v1/skills/id/$SKILL_ID" || true
  fi
  rm -f "$COOKIE_FILE"
  rm -rf "$WORK_DIR"
}

trap cleanup EXIT

echo "=== Skill Suite Smoke Test ==="
echo "Target: $BASE_URL"
echo "Suite:  @global/$SUITE_SLUG"

if [[ -n "$SMOKE_ADMIN_USERNAME" || -n "$SMOKE_ADMIN_PASSWORD" ]]; then
  if [[ -z "$SMOKE_ADMIN_USERNAME" || -z "$SMOKE_ADMIN_PASSWORD" ]]; then
    echo "FAIL: SMOKE_ADMIN_USERNAME and SMOKE_ADMIN_PASSWORD must be set together"
    exit 1
  fi
  curl -sS -c "$COOKIE_FILE" "$BASE_URL/api/v1/auth/me" >/dev/null
else
  AUTH_HEADERS=(-H "X-Mock-User-Id: local-admin")
  curl -sS -c "$COOKIE_FILE" "${AUTH_HEADERS[@]}" \
    "$BASE_URL/api/v1/auth/providers" >/dev/null
fi
CSRF_TOKEN="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIE_FILE" | tail -n 1)"
if [[ -z "$CSRF_TOKEN" ]]; then
  echo "FAIL: could not bootstrap CSRF token"
  exit 1
fi

if [[ -n "$SMOKE_ADMIN_USERNAME" ]]; then
  LOGIN_PAYLOAD="$(SMOKE_ADMIN_USERNAME="$SMOKE_ADMIN_USERNAME" SMOKE_ADMIN_PASSWORD="$SMOKE_ADMIN_PASSWORD" \
    python3 - <<'PY'
import json
import os

print(json.dumps({
    "username": os.environ["SMOKE_ADMIN_USERNAME"],
    "password": os.environ["SMOKE_ADMIN_PASSWORD"],
}))
PY
)"
  LOGIN_STATUS="$(curl -sS -o "$WORK_DIR/login.json" -w '%{http_code}' \
    -b "$COOKIE_FILE" -c "$COOKIE_FILE" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
    -H "Content-Type: application/json" -X POST \
    "$BASE_URL/api/v1/auth/local/login" -d "$LOGIN_PAYLOAD")"
  if [[ "$LOGIN_STATUS" != "200" ]]; then
    echo "FAIL: local administrator login returned HTTP $LOGIN_STATUS"
    exit 1
  fi
  CSRF_TOKEN="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIE_FILE" | tail -n 1)"
  echo "PASS: authenticated with the local administrator account"
else
  echo "PASS: authenticated with the local mock administrator"
fi

cat > "$WORK_DIR/SKILL.md" <<EOF
---
name: $SKILL_NAME
description: Temporary member for the Skill Suite smoke test
version: 1.0.0
---
# Suite smoke member
EOF
python3 - "$WORK_DIR" <<'PY'
from pathlib import Path
import sys
import zipfile

root = Path(sys.argv[1])
with zipfile.ZipFile(root / "member.zip", "w", zipfile.ZIP_DEFLATED) as archive:
    archive.write(root / "SKILL.md", "SKILL.md")
PY

PUBLISH_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -F "file=@$WORK_DIR/member.zip;type=application/zip" -F "visibility=PUBLIC" \
  "$BASE_URL/api/web/skills/global/publish")"
assert_code "publish the temporary member Skill" "$PUBLISH_RESPONSE" 0
SKILL_ID="$(json_field "$PUBLISH_RESPONSE" data.skillId)"
SKILL_SLUG="$(json_field "$PUBLISH_RESPONSE" data.slug)"

SKILL_DETAIL=""
for _ in $(seq 1 60); do
  SKILL_DETAIL="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
    "${AUTH_HEADERS[@]}" "$BASE_URL/api/web/skills/global/$SKILL_SLUG")"
  SKILL_VERSION_ID="$(JSON_INPUT="$SKILL_DETAIL" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_INPUT"]).get("data") or {}
versions = [data.get("headlineVersion") or {}, data.get("ownerPreviewVersion") or {}]
match = next((item for item in versions if item.get("version") == "1.0.0" and item.get("status") == "PUBLISHED"), {})
print(match.get("id", ""))
PY
)"
  [[ -n "$SKILL_VERSION_ID" ]] && break
  sleep 1
done
if [[ -z "$SKILL_VERSION_ID" ]]; then
  echo "FAIL: member Skill did not become PUBLISHED within 60 seconds"
  exit 1
fi
echo "PASS: member Skill is published and downloadable"

SUITE_PAYLOAD="$(python3 - "$SUITE_SLUG" "$SKILL_SLUG" <<'PY'
import json
import sys

member = {"namespace": "global", "slug": sys.argv[2], "version": "1.0.0"}
print(json.dumps({
    "namespace": "global",
    "slug": sys.argv[1],
    "displayName": "Suite smoke test",
    "summary": "Temporary private Suite",
    "version": "1.0.0",
    "visibility": "PRIVATE",
    "changelog": "Initial smoke version",
    "entrySkill": member,
    "members": [member],
}))
PY
)"
CREATE_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Content-Type: application/json" -X POST "$BASE_URL/api/web/suites" \
  -d "$SUITE_PAYLOAD")"
assert_code "create a Suite draft with one exact member" "$CREATE_RESPONSE" 0
SUITE_ID="$(json_field "$CREATE_RESPONSE" data.id)"
SUITE_VERSION_ID="$(json_field "$CREATE_RESPONSE" data.versionId)"

PUBLISH_SUITE_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -X POST "$BASE_URL/api/web/suites/$SUITE_ID/versions/$SUITE_VERSION_ID/publish")"
assert_code "publish the private Suite directly" "$PUBLISH_SUITE_RESPONSE" 0

MY_SUITES_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" "$BASE_URL/api/web/me/suites?q=$SUITE_SLUG")"
assert_code "discover the published Suite in the owner dashboard" "$MY_SUITES_RESPONSE" 0
JSON_INPUT="$MY_SUITES_RESPONSE" python3 - "$SUITE_SLUG" <<'PY'
import json
import os
import sys

items = json.loads(os.environ["JSON_INPUT"])["data"]["items"]
raise SystemExit(0 if any(item["slug"] == sys.argv[1] and item["versionStatus"] == "PUBLISHED" for item in items) else 1)
PY
echo "PASS: owner dashboard contains the published Suite"

IDEMPOTENCY_KEY="suite-smoke-$TOKEN"
PLAN_ONE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" -X POST \
  "$BASE_URL/api/web/suites/global/$SUITE_SLUG/install-plan?version=1.0.0")"
assert_code "issue an exact-member install plan" "$PLAN_ONE" 0
PLAN_TWO="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" -X POST \
  "$BASE_URL/api/web/suites/global/$SUITE_SLUG/install-plan?version=1.0.0")"
assert_code "safely replay the install plan" "$PLAN_TWO" 0
if [[ "$(json_field "$PLAN_ONE" data.operationId)" != "$(json_field "$PLAN_TWO" data.operationId)" ]]; then
  echo "FAIL: replayed plan returned a different operation ID"
  exit 1
fi
echo "PASS: replayed plan keeps the server operation ID"

RESOURCE_RESPONSE="$(curl -sS "$BASE_URL/api/v1/resources?resourceType=SKILL&q=$SKILL_SLUG")"
assert_code "ordinary Skill discovery remains available" "$RESOURCE_RESPONSE" 0
JSON_INPUT="$RESOURCE_RESPONSE" python3 - "$SKILL_SLUG" <<'PY'
import json
import os
import sys

items = json.loads(os.environ["JSON_INPUT"])["data"]["items"]
raise SystemExit(0 if any(item["resourceType"] == "SKILL" and item["slug"] == sys.argv[1] for item in items) else 1)
PY
echo "PASS: typed discovery still returns the ordinary Skill"

YANK_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Content-Type: application/json" -X POST \
  "$BASE_URL/api/v1/admin/skills/versions/$SKILL_VERSION_ID/yank" -d '{"reason":"suite smoke"}')"
assert_code "yank the exact member version" "$YANK_RESPONSE" 0

DEGRADED_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" \
  "$BASE_URL/api/web/suites/global/$SUITE_SLUG?version=1.0.0")"
assert_code "load the degraded Suite snapshot" "$DEGRADED_RESPONSE" 0
JSON_INPUT="$DEGRADED_RESPONSE" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_INPUT"])["data"]
reasons = {member.get("blockingReason") for member in data["members"]}
raise SystemExit(0 if data["available"] is False and "VERSION_UNAVAILABLE" in reasons else 1)
PY
echo "PASS: an unavailable member degrades the Suite without changing its snapshot"

HTTP_RESULT="$(curl -sS -o "$WORK_DIR/blocked-plan.json" -w '%{http_code}' \
  -b "$COOKIE_FILE" -c "$COOKIE_FILE" "${AUTH_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" -H "Idempotency-Key: blocked-$TOKEN" -X POST \
  "$BASE_URL/api/web/suites/global/$SUITE_SLUG/install-plan?version=1.0.0")"
if [[ "$HTTP_RESULT" != "400" ]]; then
  echo "FAIL: degraded Suite install plan should return HTTP 400, got $HTTP_RESULT"
  exit 1
fi
echo "PASS: degraded Suite cannot issue a new install plan"
echo "=== Skill Suite Smoke Test Passed ==="
