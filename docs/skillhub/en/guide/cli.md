# SkillHub CLI

SkillHub CLI is the official command-line tool for SkillHub, designed for searching, installing, managing, and publishing Agent skill packages.

## Installation

```bash
# Install globally via npm
npm install -g skillhub

# Or run directly with npx
npx skillhub@latest version

# Or install globally via Bun
bun add -g skillhub
```

## Quick Start

```bash
# Login
skillhub login --token sk_xxx

# Search skills
skillhub search pdf

# Install skill to Agent directory
skillhub install pdf-parser --agent codex

# List installed skills
skillhub list

# Publish skill
skillhub publish ./my-skill --namespace myspace
```

## Registry Configuration

The active registry is resolved in the following priority order:

1. `--registry <url>` command-line argument
2. `SKILLHUB_REGISTRY` environment variable
3. `registry` in user configuration
4. Default value `https://skill.xfyun.cn`

```bash
# Temporarily use another registry
skillhub search pdf --registry https://skillhub.example.com

# Set via environment variable
export SKILLHUB_REGISTRY=https://skillhub.example.com
```

## Authentication

### Login

```bash
# Login with API token
skillhub login --token sk_xxx

# Login to specific registry
skillhub login --token sk_xxx --registry https://skillhub.example.com
```

Tokens are stored only in the user directory `~/.skillhub/credentials.json`, never in project files.

### Check Current Identity

```bash
skillhub whoami
```

### Logout

```bash
skillhub logout
```

Logout only removes the token, preserving registry configuration and installation records.

## Search

```bash
skillhub search pdf
skillhub search "" --limit 20
skillhub search pdf --json
```

## Installation

```bash
# Install to auto-detected Agent directory
skillhub install pdf-parser

# Specify namespace
skillhub install pdf-parser --namespace myspace

# Specify version
skillhub install pdf-parser --version 1.2.0

# Install to specific Agent
skillhub install pdf-parser --agent codex

# Install to multiple Agents
skillhub install pdf-parser --agent codex --agent claude-code

# Install to custom directory
skillhub install pdf-parser --dir ~/.claude/skills

# Force overwrite existing installation
skillhub install pdf-parser --force
```

### Supported Agents

Built-in support for the following Tier 1 Agents:

- `claude-code` - Claude Code
- `codex` - Codex
- `cursor` - Cursor
- `github-copilot` - GitHub Copilot
- `gemini-cli` - Gemini CLI
- `openhands` - OpenHands
- `windsurf` - Windsurf
- `openclaw` - OpenClaw
- `kiro-cli` - Kiro CLI
- `roo` - Roo
- `trae` - Trae
- `trae-cn` - Trae CN
- `opencode` - OpenCode
- `kilo` - Kilo

For Agents not in the list, use `--dir` to specify the installation path.

## Local Management

### List Installed Skills

```bash
skillhub list
skillhub list --agent codex
skillhub list --json
```

### Remove Local Skills

```bash
# Remove all local installation targets
skillhub remove pdf-parser

# Remove only specific Agent's installation
skillhub remove pdf-parser --agent codex

# Remove remote skill
skillhub remove pdf-parser --remote --hard --namespace myspace
```

### Rebuild Local Inventory

```bash
skillhub doctor
```

`doctor` scans `.*/skills/<slug>/.skillhub/metadata.json` under the current project and rebuilds `inventory.json`.

## Publishing

```bash
skillhub publish ./my-skill --namespace myspace
skillhub publish ./my-skill --namespace myspace --visibility private
```

## Self-Update

```bash
# Check for new version
skillhub update --check

# Execute update
skillhub update
```

## JSON Output

All commands support the `--json` parameter for machine-readable JSON output:

```bash
skillhub search pdf --json
skillhub list --json
skillhub whoami --json
```

## Security Notes

- Tokens are stored only in user directory `~/.skillhub/credentials.json`
- On Linux/macOS, credential file permissions are set to `0600`
- Tokens are never written to any project-local files
- Remote delete operations require explicit confirmation or `--hard` parameter
