#!/usr/bin/env bash

# Release entrypoint for the SkillHub CLI.
#
# This script bumps cli/package.json, commits the bump, creates a `cli-vX.Y.Z`
# tag, and pushes it. The GitHub Actions workflow `release-cli.yml` picks up
# the tag and performs the actual build + npm publish + GitHub Release.
#
# Prefer this over direct `npm publish`: avoids local network/TLS issues with
# registry.npmjs.org, and keeps release provenance tied to CI.

set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
CLI_DIR="$REPO_ROOT/cli"
PACKAGE_JSON="$CLI_DIR/package.json"

log_stage() {
  echo "[publish-cli] $1"
}

usage() {
  echo "Usage: $0 [patch|minor|major]" >&2
}

confirm() {
  local prompt="$1"
  local answer
  read -r -p "$prompt [y/N]: " answer
  [[ "$answer" =~ ^[Yy]$ ]]
}

BUMP_TYPE="${1:-patch}"
if [[ "$BUMP_TYPE" != "patch" && "$BUMP_TYPE" != "minor" && "$BUMP_TYPE" != "major" ]]; then
  usage
  exit 1
fi

log_stage "checking git working tree"
if [[ -n "$(git -C "$REPO_ROOT" status --porcelain)" ]]; then
  echo "git working tree is not clean — commit or stash changes first" >&2
  exit 1
fi

CURRENT_BRANCH="$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "main" ]]; then
  echo "releases must be cut from 'main' (current: '$CURRENT_BRANCH')" >&2
  exit 1
fi

log_stage "pulling latest from origin/$CURRENT_BRANCH"
git -C "$REPO_ROOT" pull --ff-only origin "$CURRENT_BRANCH"

log_stage "fetching tags from origin"
git -C "$REPO_ROOT" fetch --tags --prune origin

log_stage "resolving baseline version from latest cli-v* tag"
LATEST_TAG="$(git -C "$REPO_ROOT" tag --list 'cli-v*' --sort=-version:refname | head -n1)"
if [[ -n "$LATEST_TAG" ]]; then
  BASE_VERSION="${LATEST_TAG#cli-v}"
  CURRENT_PKG_VERSION="$(node -p "require('$PACKAGE_JSON').version")"
  if [[ "$BASE_VERSION" != "$CURRENT_PKG_VERSION" ]]; then
    log_stage "syncing package.json $CURRENT_PKG_VERSION -> $BASE_VERSION (from $LATEST_TAG)"
    node -e "
      const fs = require('fs');
      const pkg = JSON.parse(fs.readFileSync('$PACKAGE_JSON', 'utf8'));
      pkg.version = '$BASE_VERSION';
      fs.writeFileSync('$PACKAGE_JSON', JSON.stringify(pkg, null, 2) + '\n');
    "
  fi
else
  log_stage "no cli-v* tags found, bumping from package.json"
fi

log_stage "bumping version ($BUMP_TYPE)"
NPM_VERSION_OUTPUT="$(cd "$CLI_DIR" && npm version "$BUMP_TYPE" --no-git-tag-version)"
NEW_VERSION="${NPM_VERSION_OUTPUT#v}"

if [[ -z "$NEW_VERSION" ]]; then
  echo "failed to parse version from npm output: $NPM_VERSION_OUTPUT" >&2
  git -C "$REPO_ROOT" checkout -- "$PACKAGE_JSON"
  exit 1
fi

TAG="cli-v${NEW_VERSION}"

if git -C "$REPO_ROOT" rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "tag $TAG already exists locally" >&2
  git -C "$REPO_ROOT" checkout -- "$PACKAGE_JSON"
  exit 1
fi

if git -C "$REPO_ROOT" ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
  echo "tag $TAG already exists on origin" >&2
  git -C "$REPO_ROOT" checkout -- "$PACKAGE_JSON"
  exit 1
fi

log_stage "new version: $NEW_VERSION (tag: $TAG)"

if ! confirm "Commit, tag, and push $TAG to origin?"; then
  echo "release cancelled — reverting package.json" >&2
  git -C "$REPO_ROOT" checkout -- "$PACKAGE_JSON"
  exit 1
fi

log_stage "committing version bump"
git -C "$REPO_ROOT" add "$PACKAGE_JSON"
git -C "$REPO_ROOT" commit -m "chore(cli): bump version to $NEW_VERSION"

log_stage "creating tag $TAG"
git -C "$REPO_ROOT" tag "$TAG"

log_stage "pushing commit and tag to origin"
git -C "$REPO_ROOT" push origin "$CURRENT_BRANCH"
git -C "$REPO_ROOT" push origin "$TAG"

log_stage "release triggered — CI workflow will build and publish"
log_stage "watch progress at: https://github.com/iflytek/skillhub/actions/workflows/release-cli.yml"
