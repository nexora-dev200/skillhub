#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PUBLISH_SCRIPT="$REPO_ROOT/scripts/publish-cli.sh"
TMP_DIR="$(mktemp -d)"
CLI_DIR="$TMP_DIR/cli"
SCRIPTS_DIR="$TMP_DIR/scripts"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir -p "$CLI_DIR" "$SCRIPTS_DIR"
cp "$PUBLISH_SCRIPT" "$SCRIPTS_DIR/publish-cli.sh"
cat >"$CLI_DIR/package.json" <<'EOF'
{
  "name": "@astron-team/skillhub",
  "version": "0.1.0",
  "bin": { "skillhub": "./dist/index.js" },
  "files": ["dist", "README.md", "LICENSE"],
  "publishConfig": { "access": "public" }
}
EOF

if REPO_ROOT="$TMP_DIR" bash "$SCRIPTS_DIR/publish-cli.sh" >"$TMP_DIR/stdout.log" 2>"$TMP_DIR/stderr.log"; then
  echo "expected script to fail when cli/.env.local is missing" >&2
  exit 1
fi

grep -F "cli/.env.local not found" "$TMP_DIR/stderr.log"
grep -F "Copy cli/.env.example to cli/.env.local" "$TMP_DIR/stderr.log"

cat >"$CLI_DIR/.env.local" <<'EOF'
NPM_TOKEN=test-token
NPM_ORG=astron-team
EOF

cat >"$CLI_DIR/package.json" <<'EOF'
{
  "name": "astron-team/skillhub",
  "version": "0.1.0",
  "bin": { "skillhub": "./dist/index.js" },
  "files": ["dist", "README.md", "LICENSE"],
  "publishConfig": { "access": "public" }
}
EOF

if REPO_ROOT="$TMP_DIR" bash "$SCRIPTS_DIR/publish-cli.sh" >"$TMP_DIR/stdout.log" 2>"$TMP_DIR/stderr.log"; then
  echo "expected script to fail for invalid package scope" >&2
  exit 1
fi

grep -F "loading environment" "$TMP_DIR/stdout.log"
grep -F "validating package metadata" "$TMP_DIR/stdout.log"
grep -F "package name must match @astron-team/*" "$TMP_DIR/stderr.log"

cat >"$CLI_DIR/package.json" <<'EOF'
{
  "name": "@astron-team/skillhub",
  "version": "0.1.0",
  "bin": { "skillhub": "./dist/index.js" },
  "files": ["dist", "README.md", "LICENSE"],
  "publishConfig": { "access": "public" }
}
EOF

git -C "$TMP_DIR" init -q
git -C "$TMP_DIR" config user.name "Test User"
git -C "$TMP_DIR" config user.email "test@example.com"
git -C "$TMP_DIR" add cli/.env.local cli/package.json
git -C "$TMP_DIR" commit -q -m "init"

touch "$TMP_DIR/dirty-file.txt"

if REPO_ROOT="$TMP_DIR" bash "$SCRIPTS_DIR/publish-cli.sh" >"$TMP_DIR/stdout.log" 2>"$TMP_DIR/stderr.log"; then
  echo "expected script to fail when git working tree is dirty" >&2
  exit 1
fi

grep -F "checking git working tree" "$TMP_DIR/stdout.log"
grep -F "git working tree is not clean" "$TMP_DIR/stderr.log"

SUCCESS_DIR="$(mktemp -d)"
cleanup_success() {
  rm -rf "$SUCCESS_DIR"
}
SUCCESS_STDOUT="$(mktemp)"
SUCCESS_STDERR="$(mktemp)"
trap 'cleanup; cleanup_success; rm -f "$SUCCESS_STDOUT" "$SUCCESS_STDERR"' EXIT

SUCCESS_CLI_DIR="$SUCCESS_DIR/cli"
SUCCESS_SCRIPTS_DIR="$SUCCESS_DIR/scripts"
SUCCESS_BIN_DIR="$SUCCESS_DIR/bin"
SUCCESS_CALLS="$SUCCESS_DIR/calls.log"
mkdir -p "$SUCCESS_CLI_DIR" "$SUCCESS_SCRIPTS_DIR" "$SUCCESS_BIN_DIR"
cp "$PUBLISH_SCRIPT" "$SUCCESS_SCRIPTS_DIR/publish-cli.sh"
cat >"$SUCCESS_CLI_DIR/.env.local" <<'EOF'
NPM_TOKEN=test-token
NPM_ORG=astron-team
DRY_RUN=true
EOF
cat >"$SUCCESS_CLI_DIR/package.json" <<'EOF'
{
  "name": "@astron-team/skillhub",
  "version": "0.1.0",
  "bin": { "skillhub": "./dist/index.js" },
  "files": ["dist", "README.md", "LICENSE"],
  "publishConfig": { "access": "public" }
}
EOF
mkdir -p "$SUCCESS_CLI_DIR/dist"
cat >"$SUCCESS_BIN_DIR/bun" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf "bun %s\\n" "\$*" >>"$SUCCESS_CALLS"
case "\$1 \$2" in
  "run build")
    mkdir -p dist
    printf "build output" > dist/index.js
    ;;
  "run test")
    ;;
  *)
    exit 1
    ;;
esac
EOF
cat >"$SUCCESS_BIN_DIR/npm" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf "npm %s\\n" "\$*" >>"$SUCCESS_CALLS"
case "\$1" in
  pack)
    ;;
  version)
    node - "\$PWD/package.json" "\$2" <<'NODE'
const fs = require("fs")
const path = process.argv[2]
const bump = process.argv[3]
const pkg = JSON.parse(fs.readFileSync(path, "utf8"))
const parts = pkg.version.split(".").map(Number)
if (bump === "patch") parts[2] += 1
else if (bump === "minor") { parts[1] += 1; parts[2] = 0 }
else if (bump === "major") { parts[0] += 1; parts[1] = 0; parts[2] = 0 }
else throw new Error('unexpected bump: ' + bump)
pkg.version = parts.join(".")
fs.writeFileSync(path, JSON.stringify(pkg, null, 2) + "\n")
NODE
    ;;
  publish)
    echo "publish should not run in dry run" >&2
    exit 1
    ;;
  *)
    exit 1
    ;;
esac
EOF
chmod +x "$SUCCESS_BIN_DIR/bun" "$SUCCESS_BIN_DIR/npm"

git -C "$SUCCESS_DIR" init -q
git -C "$SUCCESS_DIR" config user.name "Test User"
git -C "$SUCCESS_DIR" config user.email "test@example.com"
git -C "$SUCCESS_DIR" add cli/.env.local cli/package.json cli/dist scripts/publish-cli.sh bin/bun bin/npm
git -C "$SUCCESS_DIR" commit -q -m "init"

if printf 'y\n' | REPO_ROOT="$SUCCESS_DIR" PATH="$SUCCESS_BIN_DIR:$PATH" bash "$SUCCESS_SCRIPTS_DIR/publish-cli.sh" patch >"$SUCCESS_STDOUT" 2>"$SUCCESS_STDERR"; then
  :
else
  status=$?
  cat "$SUCCESS_STDOUT" >&2 || true
  cat "$SUCCESS_STDERR" >&2 || true
  exit "$status"
fi

grep -F "running preflight build" "$SUCCESS_STDOUT"
grep -F "running preflight tests" "$SUCCESS_STDOUT"
grep -F "running preflight pack" "$SUCCESS_STDOUT"
grep -F "bumping version (patch)" "$SUCCESS_STDOUT"
grep -F "ready to publish @astron-team/skillhub@0.1.1" "$SUCCESS_STDOUT"
grep -F "DRY_RUN=true, skipping npm publish" "$SUCCESS_STDOUT"
grep -F "bun run build" "$SUCCESS_CALLS"
grep -F "bun run test" "$SUCCESS_CALLS"
grep -F "npm pack --dry-run" "$SUCCESS_CALLS"
if grep -Fq "npm publish" "$SUCCESS_CALLS"; then
  echo "expected npm publish to be skipped in dry run" >&2
  exit 1
fi

grep -F '"version": "0.1.1"' "$SUCCESS_CLI_DIR/package.json"

CANCEL_DIR="$(mktemp -d)"
cleanup_cancel() {
  rm -rf "$CANCEL_DIR"
}
CANCEL_STDOUT="$(mktemp)"
CANCEL_STDERR="$(mktemp)"
trap 'cleanup; cleanup_success; cleanup_cancel; rm -f "$SUCCESS_STDOUT" "$SUCCESS_STDERR" "$CANCEL_STDOUT" "$CANCEL_STDERR"' EXIT

CANCEL_CLI_DIR="$CANCEL_DIR/cli"
CANCEL_SCRIPTS_DIR="$CANCEL_DIR/scripts"
CANCEL_BIN_DIR="$CANCEL_DIR/bin"
CANCEL_CALLS="$CANCEL_DIR/calls.log"
mkdir -p "$CANCEL_CLI_DIR" "$CANCEL_SCRIPTS_DIR" "$CANCEL_BIN_DIR"
cp "$PUBLISH_SCRIPT" "$CANCEL_SCRIPTS_DIR/publish-cli.sh"
cat >"$CANCEL_CLI_DIR/.env.local" <<'EOF'
NPM_TOKEN=test-token
NPM_ORG=astron-team
DRY_RUN=false
EOF
cat >"$CANCEL_CLI_DIR/package.json" <<'EOF'
{
  "name": "@astron-team/skillhub",
  "version": "0.2.0",
  "bin": { "skillhub": "./dist/index.js" },
  "files": ["dist", "README.md", "LICENSE"],
  "publishConfig": { "access": "public" }
}
EOF
mkdir -p "$CANCEL_CLI_DIR/dist"
cat >"$CANCEL_BIN_DIR/bun" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf "bun %s\\n" "\$*" >>"$CANCEL_CALLS"
case "\$1 \$2" in
  "run build")
    mkdir -p dist
    printf "build output" > dist/index.js
    ;;
  "run test")
    ;;
  *)
    exit 1
    ;;
esac
EOF
cat >"$CANCEL_BIN_DIR/npm" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf "npm %s\\n" "\$*" >>"$CANCEL_CALLS"
case "\$1" in
  pack)
    ;;
  version)
    node - "\$PWD/package.json" "\$2" <<'NODE'
const fs = require("fs")
const path = process.argv[2]
const bump = process.argv[3]
const pkg = JSON.parse(fs.readFileSync(path, "utf8"))
const parts = pkg.version.split(".").map(Number)
if (bump === "patch") parts[2] += 1
else if (bump === "minor") { parts[1] += 1; parts[2] = 0 }
else if (bump === "major") { parts[0] += 1; parts[1] = 0; parts[2] = 0 }
else throw new Error('unexpected bump: ' + bump)
pkg.version = parts.join(".")
fs.writeFileSync(path, JSON.stringify(pkg, null, 2) + "\n")
NODE
    ;;
  publish)
    echo "npm publish should not be called after cancellation" >&2
    exit 1
    ;;
  *)
    exit 1
    ;;
esac
EOF
chmod +x "$CANCEL_BIN_DIR/bun" "$CANCEL_BIN_DIR/npm"

git -C "$CANCEL_DIR" init -q
git -C "$CANCEL_DIR" config user.name "Test User"
git -C "$CANCEL_DIR" config user.email "test@example.com"
git -C "$CANCEL_DIR" add cli/.env.local cli/package.json cli/dist scripts/publish-cli.sh bin/bun bin/npm
git -C "$CANCEL_DIR" commit -q -m "init"

if printf 'y\nn\n' | REPO_ROOT="$CANCEL_DIR" PATH="$CANCEL_BIN_DIR:$PATH" bash "$CANCEL_SCRIPTS_DIR/publish-cli.sh" patch >"$CANCEL_STDOUT" 2>"$CANCEL_STDERR"; then
  CANCEL_EXIT_CODE=0
else
  CANCEL_EXIT_CODE=$?
fi

if [[ "$CANCEL_EXIT_CODE" -eq 0 ]]; then
  echo "expected script to exit with non-zero when publish is cancelled" >&2
  exit 1
fi

if [[ "$CANCEL_EXIT_CODE" -ne 2 ]]; then
  echo "expected exit code 2 when publish is cancelled, got $CANCEL_EXIT_CODE" >&2
  exit 1
fi

grep -F "ready to publish @astron-team/skillhub@0.2.1" "$CANCEL_STDOUT"
grep -F "publish cancelled" "$CANCEL_STDERR"
if grep -Fq "npm publish" "$CANCEL_CALLS"; then
  echo "npm publish should not be called after cancellation" >&2
  exit 1
fi

grep -F '"version": "0.2.1"' "$CANCEL_CLI_DIR/package.json"
