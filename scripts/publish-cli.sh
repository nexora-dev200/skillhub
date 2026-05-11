#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
CLI_DIR="$REPO_ROOT/cli"
ENV_LOCAL="$CLI_DIR/.env.local"
PACKAGE_JSON="$CLI_DIR/package.json"
PKG_INFO_TS="$CLI_DIR/src/generated/pkg-info.ts"
DIST_ENTRY="$CLI_DIR/dist/index.js"

log_stage() {
  echo "[publish-cli] $1"
}

usage() {
  echo "Usage: $0 [patch|minor|major|skip]" >&2
}

confirm() {
  local prompt="$1"
  local answer
  read -r -p "$prompt [y/N]: " answer
  [[ "$answer" =~ ^[Yy]$ ]]
}

verify_version_sync() {
  local expected_version="$1"
  local generated_version
  local runtime_version

  generated_version="$(node - "$PKG_INFO_TS" <<'NODE'
const fs = require('fs')
const path = process.argv[2]
const contents = fs.readFileSync(path, 'utf8')
const match = contents.match(/export const PKG_VERSION = ["']([^"']+)["']/)
if (!match) process.exit(1)
process.stdout.write(match[1])
NODE
)"

  runtime_version="$(node "$DIST_ENTRY" version | sed -E 's/^SkillHub CLI //')"

  if [[ "$generated_version" != "$expected_version" ]]; then
    echo "generated PKG_VERSION mismatch: expected $expected_version, got $generated_version" >&2
    exit 1
  fi

  if [[ "$runtime_version" != "$expected_version" ]]; then
    echo "built CLI version mismatch: expected $expected_version, got $runtime_version" >&2
    exit 1
  fi
}

assert_version_not_published() {
  local package_name="$1"
  local version="$2"
  local view_output

  if view_output="$(npm view "${package_name}@${version}" version --registry "$NPM_REGISTRY" 2>&1)"; then
    echo "${package_name}@${version} already exists on $NPM_REGISTRY" >&2
    echo "Update cli/package.json to the latest published version before running this release." >&2
    exit 1
  fi

  if echo "$view_output" | grep -Eiq '(E404|404 Not Found|is not in this registry|Not found)'; then
    return 0
  fi

  echo "failed to verify whether ${package_name}@${version} exists on $NPM_REGISTRY" >&2
  echo "$view_output" >&2
  exit 1
}

next_package_version() {
  local version="$1"
  local bump_type="$2"

  node - "$version" "$bump_type" <<'NODE'
const version = process.argv[2]
const bumpType = process.argv[3]
const parts = version.split('.').map(Number)

if (parts.length !== 3 || parts.some(part => !Number.isInteger(part) || part < 0)) {
  throw new Error(`unsupported package version: ${version}`)
}

if (bumpType === 'patch') {
  parts[2] += 1
} else if (bumpType === 'minor') {
  parts[1] += 1
  parts[2] = 0
} else if (bumpType === 'major') {
  parts[0] += 1
  parts[1] = 0
  parts[2] = 0
} else if (bumpType !== 'skip') {
  throw new Error(`unsupported bump type: ${bumpType}`)
}

process.stdout.write(parts.join('.'))
NODE
}

BUMP_TYPE="${1:-patch}"
if [[ "$BUMP_TYPE" != "patch" && "$BUMP_TYPE" != "minor" && "$BUMP_TYPE" != "major" && "$BUMP_TYPE" != "skip" ]]; then
  usage
  exit 1
fi

if [[ ! -f "$ENV_LOCAL" ]]; then
  echo "cli/.env.local not found" >&2
  echo "Copy cli/.env.example to cli/.env.local" >&2
  exit 1
fi

log_stage "loading environment"
set -a
# shellcheck disable=SC1090
source "$ENV_LOCAL"
set +a

: "${NPM_REGISTRY:=https://registry.npmjs.org}"
: "${DRY_RUN:=false}"

if [[ -z "${NPM_TOKEN:-}" ]]; then
  echo "NPM_TOKEN is required" >&2
  exit 1
fi

if [[ -z "${NPM_ORG:-}" ]]; then
  echo "NPM_ORG is required" >&2
  exit 1
fi

log_stage "validating package metadata"
PACKAGE_NAME="$(node -p "require('$PACKAGE_JSON').name ?? ''")"
PACKAGE_VERSION="$(node -p "require('$PACKAGE_JSON').version ?? ''")"
PACKAGE_ACCESS="$(node -p "require('$PACKAGE_JSON').publishConfig?.access ?? ''")"

if [[ "$PACKAGE_NAME" != "@${NPM_ORG}/"* ]]; then
  echo "package name must match @${NPM_ORG}/* (got $PACKAGE_NAME)" >&2
  exit 1
fi

if [[ -z "$PACKAGE_VERSION" ]]; then
  echo "package version is required" >&2
  exit 1
fi

if [[ "$PACKAGE_ACCESS" != "public" ]]; then
  echo "publishConfig.access must be public" >&2
  exit 1
fi

log_stage "checking git working tree"
if [[ -n "$(git -C "$REPO_ROOT" status --porcelain)" ]]; then
  echo "git working tree is not clean" >&2
  exit 1
fi

if [[ "$BUMP_TYPE" == "skip" ]]; then
  NEW_VERSION="$PACKAGE_VERSION"
else
  NEW_VERSION="$(next_package_version "$PACKAGE_VERSION" "$BUMP_TYPE")"
fi

log_stage "checking registry version"
assert_version_not_published "$PACKAGE_NAME" "$NEW_VERSION"

if [[ "$BUMP_TYPE" != "skip" ]]; then
  if ! confirm "Proceed with version bump ($BUMP_TYPE)?"; then
    echo "version bump cancelled" >&2
    exit 1
  fi

  log_stage "bumping version ($BUMP_TYPE)"
  (
    cd "$CLI_DIR"
    npm version "$BUMP_TYPE" --no-git-tag-version
  )
fi

ACTUAL_VERSION="$(node -p "require('$PACKAGE_JSON').version ?? ''")"
if [[ "$ACTUAL_VERSION" != "$NEW_VERSION" ]]; then
  echo "npm version produced $ACTUAL_VERSION, expected $NEW_VERSION" >&2
  exit 1
fi

log_stage "running preflight build"
(
  cd "$CLI_DIR"
  bun run build
)

log_stage "running preflight tests"
(
  cd "$CLI_DIR"
  bun run test
)

log_stage "verifying built version"
verify_version_sync "$NEW_VERSION"

log_stage "running preflight pack"
(
  cd "$CLI_DIR"
  npm pack --dry-run
)

log_stage "ready to publish $PACKAGE_NAME@$NEW_VERSION"

if [[ "$DRY_RUN" == "true" ]]; then
  log_stage "DRY_RUN=true, skipping npm publish"
  exit 0
fi

if ! confirm "Publish $PACKAGE_NAME@$NEW_VERSION to $NPM_REGISTRY?"; then
  echo "publish cancelled" >&2
  exit 2
fi

NPM_CONFIG_FILE="$(mktemp)"
cleanup() {
  rm -f "$NPM_CONFIG_FILE"
}
trap cleanup EXIT

REGISTRY_HOST="${NPM_REGISTRY#http://}"
REGISTRY_HOST="${REGISTRY_HOST#https://}"
REGISTRY_HOST="${REGISTRY_HOST%/}"

cat >"$NPM_CONFIG_FILE" <<EOF
registry=${NPM_REGISTRY}
//${REGISTRY_HOST}/:_authToken=${NPM_TOKEN}
always-auth=true
EOF

log_stage "publishing package"
(
  cd "$CLI_DIR"
  NPM_CONFIG_USERCONFIG="$NPM_CONFIG_FILE" npm publish --access public --registry "$NPM_REGISTRY"
)

log_stage "publish completed"
