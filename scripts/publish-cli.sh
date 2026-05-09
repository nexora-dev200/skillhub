#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
CLI_DIR="$REPO_ROOT/cli"
ENV_LOCAL="$CLI_DIR/.env.local"
PACKAGE_JSON="$CLI_DIR/package.json"

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

log_stage "running preflight pack"
(
  cd "$CLI_DIR"
  npm pack --dry-run
)

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

NEW_VERSION="$(node -p "require('$PACKAGE_JSON').version ?? ''")"
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
