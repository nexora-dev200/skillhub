#!/usr/bin/env bash

# Integration tests for scripts/publish-cli.sh.
#
# The script bumps cli/package.json, commits, tags `cli-vX.Y.Z`, and pushes
# both refs to origin. These tests build a self-contained fake repo for each
# scenario, using a real bare repository as origin so git fetch / pull / push
# are actually exercised. `npm` is stubbed to keep `npm version` deterministic.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PUBLISH_SCRIPT="$REPO_ROOT/scripts/publish-cli.sh"

TMP_DIRS=()
cleanup() {
  local d
  for d in "${TMP_DIRS[@]+"${TMP_DIRS[@]}"}"; do
    rm -rf "$d"
  done
}
trap cleanup EXIT

new_tmp() {
  local d
  d="$(mktemp -d)"
  TMP_DIRS+=("$d")
  echo "$d"
}

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

# init_repo <repo_dir> [initial_pkg_version] [tag_to_seed ...]
#
# Builds a minimal repo with:
#   - cli/package.json at the requested version
#   - scripts/publish-cli.sh (the script under test)
#   - bin/npm stub that implements `npm version <bump> --no-git-tag-version`
#   - a sibling bare repo as `origin`
#   - HEAD on `main` with the init commit already pushed
#   - optional pre-seeded `cli-v*` tags (created locally AND on origin)
init_repo() {
  local repo="$1"
  local version="${2:-0.1.0}"
  local tags=()
  if [[ $# -gt 2 ]]; then
    tags=("${@:3}")
  fi

  local origin="$repo.origin.git"
  TMP_DIRS+=("$origin")

  mkdir -p "$repo/cli" "$repo/scripts" "$repo/bin"
  cp "$PUBLISH_SCRIPT" "$repo/scripts/publish-cli.sh"

  cat >"$repo/cli/package.json" <<EOF
{
  "name": "@astron-team/skillhub",
  "version": "$version",
  "bin": { "skillhub": "./dist/index.js" },
  "files": ["dist", "README.md", "LICENSE"],
  "publishConfig": { "access": "public" }
}
EOF

  # Stub `npm`: only `npm version <patch|minor|major> --no-git-tag-version` is
  # supported. Mutates package.json in cwd and echoes `vX.Y.Z` (matching real
  # npm behaviour the script depends on).
  cat >"$repo/bin/npm" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" != "version" ]]; then
  echo "npm stub: unsupported subcommand: $*" >&2
  exit 1
fi
BUMP="$2"
node - "$PWD/package.json" "$BUMP" <<'NODE'
const fs = require("fs");
const path = process.argv[2];
const bump = process.argv[3];
const pkg = JSON.parse(fs.readFileSync(path, "utf8"));
const parts = pkg.version.split(".").map(Number);
if (bump === "patch") parts[2] += 1;
else if (bump === "minor") { parts[1] += 1; parts[2] = 0; }
else if (bump === "major") { parts[0] += 1; parts[1] = 0; parts[2] = 0; }
else throw new Error("unexpected bump: " + bump);
const next = parts.join(".");
pkg.version = next;
fs.writeFileSync(path, JSON.stringify(pkg, null, 2) + "\n");
console.log("v" + next);
NODE
EOF
  chmod +x "$repo/bin/npm"

  git init -q --bare "$origin"
  git -C "$repo" init -q -b main
  git -C "$repo" config user.name "Test User"
  git -C "$repo" config user.email "test@example.com"
  git -C "$repo" remote add origin "$origin"
  git -C "$repo" add cli/package.json scripts/publish-cli.sh bin/npm
  git -C "$repo" commit -q -m "init"
  git -C "$repo" push -q -u origin main

  local tag
  for tag in "${tags[@]+"${tags[@]}"}"; do
    git -C "$repo" tag "$tag"
    git -C "$repo" push -q origin "$tag"
  done
}

# run_publish <repo> <bump> [stdin]
# Writes stdout to $repo/stdout.log and stderr to $repo/stderr.log.
# Prints the exit code on stdout.
run_publish() {
  local repo="$1"
  local bump="$2"
  local input="${3-}"
  local status=0
  if [[ -n "$input" ]]; then
    printf '%s' "$input" | REPO_ROOT="$repo" PATH="$repo/bin:$PATH" \
      bash "$repo/scripts/publish-cli.sh" "$bump" \
      >"$repo/stdout.log" 2>"$repo/stderr.log" || status=$?
  else
    REPO_ROOT="$repo" PATH="$repo/bin:$PATH" \
      bash "$repo/scripts/publish-cli.sh" "$bump" \
      >"$repo/stdout.log" 2>"$repo/stderr.log" || status=$?
  fi
  echo "$status"
}

# ----------------------------------------------------------------------------
# Test 1: invalid bump type → usage error, exit non-zero
# ----------------------------------------------------------------------------
echo "[test] invalid bump type"
REPO1="$(new_tmp)"
init_repo "$REPO1"
status="$(run_publish "$REPO1" "foo")"
[[ "$status" -ne 0 ]] || fail "expected non-zero exit for invalid bump type"
grep -F "Usage:" "$REPO1/stderr.log" >/dev/null

# ----------------------------------------------------------------------------
# Test 2: dirty working tree → abort before any side effect
# ----------------------------------------------------------------------------
echo "[test] dirty working tree aborts"
REPO2="$(new_tmp)"
init_repo "$REPO2"
touch "$REPO2/dirty.txt"
status="$(run_publish "$REPO2" "patch")"
[[ "$status" -ne 0 ]] || fail "expected non-zero exit for dirty tree"
grep -F "checking git working tree" "$REPO2/stdout.log" >/dev/null
grep -F "git working tree is not clean" "$REPO2/stderr.log" >/dev/null

# ----------------------------------------------------------------------------
# Test 3: not on main branch → abort
# ----------------------------------------------------------------------------
echo "[test] non-main branch aborts"
REPO3="$(new_tmp)"
init_repo "$REPO3"
git -C "$REPO3" checkout -q -b feature/x
status="$(run_publish "$REPO3" "patch")"
[[ "$status" -ne 0 ]] || fail "expected non-zero exit when not on main"
grep -F "releases must be cut from 'main'" "$REPO3/stderr.log" >/dev/null
grep -F "feature/x" "$REPO3/stderr.log" >/dev/null

# ----------------------------------------------------------------------------
# Test 4: package.json behind latest cli-v* tag → baseline sync, then bump
# ----------------------------------------------------------------------------
echo "[test] baseline sync from latest cli-v* tag, then bump + push"
REPO4="$(new_tmp)"
init_repo "$REPO4" "0.1.0" "cli-v0.2.0"
status="$(run_publish "$REPO4" "patch" $'y\n')"
[[ "$status" -eq 0 ]] || { cat "$REPO4/stderr.log" >&2; fail "expected success, got $status"; }
grep -F "syncing package.json 0.1.0 -> 0.2.0 (from cli-v0.2.0)" "$REPO4/stdout.log" >/dev/null
grep -F "bumping version (patch)" "$REPO4/stdout.log" >/dev/null
grep -F "new version: 0.2.1 (tag: cli-v0.2.1)" "$REPO4/stdout.log" >/dev/null
grep -F '"version": "0.2.1"' "$REPO4/cli/package.json" >/dev/null
git -C "$REPO4" rev-parse "cli-v0.2.1" >/dev/null \
  || fail "local tag cli-v0.2.1 missing"
git -C "$REPO4" log --oneline | grep -F "chore(cli): bump version to 0.2.1" >/dev/null
git -C "$REPO4.origin.git" rev-parse "cli-v0.2.1" >/dev/null \
  || fail "origin tag cli-v0.2.1 missing — atomic push not delivered"

# ----------------------------------------------------------------------------
# Test 5: no cli-v* tags → fall back to package.json
# ----------------------------------------------------------------------------
echo "[test] no cli-v* tags falls back to package.json"
REPO5="$(new_tmp)"
init_repo "$REPO5" "0.1.0"
status="$(run_publish "$REPO5" "minor" $'y\n')"
[[ "$status" -eq 0 ]] || { cat "$REPO5/stderr.log" >&2; fail "expected success, got $status"; }
grep -F "no cli-v* tags found, bumping from package.json" "$REPO5/stdout.log" >/dev/null
grep -F "new version: 0.2.0 (tag: cli-v0.2.0)" "$REPO5/stdout.log" >/dev/null
git -C "$REPO5.origin.git" rev-parse "cli-v0.2.0" >/dev/null \
  || fail "origin tag cli-v0.2.0 missing"

# ----------------------------------------------------------------------------
# Test 6: confirmation cancel → revert package.json, no commit, no tag
# ----------------------------------------------------------------------------
echo "[test] confirmation cancel reverts everything"
REPO6="$(new_tmp)"
init_repo "$REPO6" "0.1.0" "cli-v0.1.0"
INITIAL_HEAD="$(git -C "$REPO6" rev-parse HEAD)"
status="$(run_publish "$REPO6" "patch" $'n\n')"
[[ "$status" -ne 0 ]] || fail "expected non-zero exit on cancel"
grep -F "release cancelled" "$REPO6/stderr.log" >/dev/null
grep -F '"version": "0.1.0"' "$REPO6/cli/package.json" >/dev/null \
  || fail "package.json not reverted to 0.1.0 after cancel"
[[ "$(git -C "$REPO6" rev-parse HEAD)" == "$INITIAL_HEAD" ]] \
  || fail "HEAD advanced after cancel — extra commit was made"
if git -C "$REPO6" rev-parse -q --verify "refs/tags/cli-v0.1.1" >/dev/null 2>&1; then
  fail "tag cli-v0.1.1 must not exist after cancel"
fi
[[ -z "$(git -C "$REPO6" status --porcelain)" ]] \
  || fail "working tree not clean after cancel — revert incomplete"

# ----------------------------------------------------------------------------
# Test 7: remote tag conflict → abort with package.json reverted
#
# Set up: cli-v0.1.1 exists on origin but not locally. Local has no cli-v*
# tags, so baseline sync skips and the patch bump from 0.1.0 lands on 0.1.1,
# which collides with origin.
# ----------------------------------------------------------------------------
echo "[test] remote tag conflict aborts and reverts"
REPO7="$(new_tmp)"
init_repo "$REPO7" "0.1.0"
git -C "$REPO7" tag "cli-v0.1.1"
git -C "$REPO7" push -q origin "cli-v0.1.1"
git -C "$REPO7" tag -d "cli-v0.1.1" >/dev/null
git -C "$REPO7" rev-parse -q --verify "refs/tags/cli-v0.1.1" >/dev/null 2>&1 \
  && fail "setup error: local tag still present"
git -C "$REPO7.origin.git" rev-parse "cli-v0.1.1" >/dev/null \
  || fail "setup error: remote tag missing"
# Disable automatic tag fetching so `git fetch --tags` doesn't pull cli-v0.1.1
# back into the local repo and short-circuit the remote check.
git -C "$REPO7" config remote.origin.tagOpt --no-tags
git -C "$REPO7" config --unset-all remote.origin.fetch 2>/dev/null || true
git -C "$REPO7" config remote.origin.fetch "+refs/heads/*:refs/remotes/origin/*"

status="$(run_publish "$REPO7" "patch")"
[[ "$status" -ne 0 ]] || fail "expected non-zero exit on remote tag conflict"
grep -F "tag cli-v0.1.1 already exists" "$REPO7/stderr.log" >/dev/null \
  || { cat "$REPO7/stderr.log" >&2; fail "expected tag conflict message"; }
grep -F '"version": "0.1.0"' "$REPO7/cli/package.json" >/dev/null \
  || fail "package.json not reverted after remote conflict"
[[ -z "$(git -C "$REPO7" status --porcelain)" ]] \
  || fail "working tree not clean after remote conflict"

# ----------------------------------------------------------------------------
# Test 8: happy path — atomic push delivers branch + tag together
# ----------------------------------------------------------------------------
echo "[test] happy path pushes branch and tag atomically"
REPO8="$(new_tmp)"
init_repo "$REPO8" "0.5.0"
status="$(run_publish "$REPO8" "patch" $'y\n')"
[[ "$status" -eq 0 ]] || { cat "$REPO8/stderr.log" >&2; fail "expected success, got $status"; }
ORIGIN8="$REPO8.origin.git"
git -C "$ORIGIN8" rev-parse "cli-v0.5.1" >/dev/null \
  || fail "origin missing tag cli-v0.5.1"
ORIGIN_HEAD="$(git -C "$ORIGIN8" rev-parse main)"
LOCAL_HEAD="$(git -C "$REPO8" rev-parse main)"
[[ "$ORIGIN_HEAD" == "$LOCAL_HEAD" ]] \
  || fail "origin/main HEAD did not advance to match local main"
# The bump commit must be the tip and the tag must point to it (atomic).
TAG_COMMIT="$(git -C "$ORIGIN8" rev-parse "cli-v0.5.1^{commit}")"
[[ "$TAG_COMMIT" == "$ORIGIN_HEAD" ]] \
  || fail "origin tag cli-v0.5.1 does not point to origin/main HEAD"
grep -F "release triggered" "$REPO8/stdout.log" >/dev/null

# ----------------------------------------------------------------------------
# Test 9: push failure → script exits non-zero (commit + tag stay local)
#
# Break origin to force `git push origin main cli-vX.Y.Z` to fail.
# ----------------------------------------------------------------------------
echo "[test] push failure surfaces error"
REPO9="$(new_tmp)"
init_repo "$REPO9" "0.6.0"
git -C "$REPO9" remote set-url origin "$REPO9.does-not-exist.git"
status="$(run_publish "$REPO9" "patch" $'y\n')"
[[ "$status" -ne 0 ]] || fail "expected non-zero exit when push fails"
# Local refs must still exist — operator can recover by deleting tag + resetting.
git -C "$REPO9" rev-parse "cli-v0.6.1" >/dev/null \
  || fail "local tag cli-v0.6.1 missing after push failure"
git -C "$REPO9" log --oneline | grep -F "chore(cli): bump version to 0.6.1" >/dev/null \
  || fail "local bump commit missing after push failure"

echo "all tests passed"
