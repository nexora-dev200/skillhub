---
name: skillhub-cli
description: Connect an Agent to a SkillHub registry and use the official SkillHub CLI to search, install, list, or explicitly upgrade SkillHub skills. Use when a user asks to connect SkillHub, install a SkillHub skill, or manage skills previously installed from SkillHub.
version: 1.0.0
license: Apache-2.0
---

# SkillHub CLI

Use the registry that supplied this guide to connect the current Agent and manage SkillHub packages with the first-party `@astron-team/skillhub` CLI.

## Resolve The Registry

The registry for this guide is `${SKILLHUB_PUBLIC_BASE_URL}`.

When this Skill is already installed, read its sibling `.skillhub/metadata.json` before running a registry command and use the recorded `registry` value instead. That installation metadata is authoritative for later searches and upgrades. If neither source yields an absolute HTTP(S) URL, stop and ask the user for the registry URL.

The source package may contain an unresolved registry marker rather than a required shell environment variable. Replace every unresolved occurrence with the resolved absolute registry URL before executing a command; never execute an empty or literal marker. The public Web guide replaces it automatically.

Keep the exact registry selected by the user for the current request. Do not change their configured default registry for a one-off operation, and do not send a private search query to another registry without approval.

## Use The First-Party CLI

First check whether the command on `PATH` is the expected CLI:

```bash
skillhub version
```

Use it only when the output is `SkillHub CLI <version>` and that semantic version is `0.1.12` or newer. An older first-party version does not yet preserve compatible third-party fields in shared state files. A different result may be an unrelated command with the same name.

Some third-party stores install another `skillhub` executable. Preserve it: do not replace, remove, or globally overwrite that command. Use the fully qualified first-party npm package below whenever the command on `PATH` does not pass the identity check. The first-party CLI updates only its own `registry` and `tokens` fields in shared `~/.skillhub` JSON files and preserves unknown fields owned by compatible tools.

If the first-party CLI is unavailable, use its fully qualified npm package for this request:

```bash
npx --yes @astron-team/skillhub@0.1.12 version
```

In that case, replace `skillhub` in the examples below with `npx --yes @astron-team/skillhub@0.1.12`. Do not install the CLI globally unless the user asks. Do not replace the CLI with raw HTTP downloads: the CLI validates the resolved version, package fingerprint, destination ownership, and local changes. Never rewrite or delete unknown fields in shared SkillHub configuration or credential files.

Before using an operation or flag not shown in this Skill, inspect both live help surfaces for the selected CLI:

```bash
skillhub help <command>
skillhub <command> --help
```

Repository documentation may describe unreleased behavior. If neither live help surface exposes a proposed command or flag, do not use it. Require Node.js 18 or newer when using the npm package.

## Choose The Flow

- **Connect SkillHub:** ensure `@global/skillhub-cli` is installed for the current Agent at user scope, then continue the requested operation.
- **Install an exact Skill:** install the requested coordinate and version directly from this registry; do not search for or substitute a similarly named package.
- **Discover a Skill:** search this registry first. If it is unavailable or has no suitable result, report that outcome and ask before querying another registry.
- **Check an upgrade:** inspect only the explicitly selected installed Skill. Never upgrade every installation implicitly.

An explicit request to connect SkillHub and install a named Skill authorizes those two local installations. It does not authorize replacing local changes, changing registries, publishing content, or installing a global CLI.

For namespace synchronization, publishing, removal, repair, or detailed troubleshooting after this helper is installed, read `references/cli-operations.md`. Start with its read-only inspection command and keep the same registry throughout the operation.

## Connect The Current Agent

Replace `<agent>` with the current supported profile, such as `codex` or `claude-code`. Check the current registry's installations once:

```bash
skillhub list \
  --agent <agent> \
  --registry ${SKILLHUB_PUBLIC_BASE_URL} \
  --json
```

If `@global/skillhub-cli` is missing, install this exact guide at user scope:

```bash
skillhub install @global/skillhub-cli \
  --version 1.0.0 \
  --scope user \
  --agent <agent> \
  --registry ${SKILLHUB_PUBLIC_BASE_URL} \
  --json
```

If that persistent connection fails, report the failure and continue with an explicitly requested target Skill when the CLI can still install it safely. Do not substitute a helper from another registry.

Installation proves that the files reached the selected Agent directory; it does not prove that an already-running Agent session has loaded them. If the current Agent cannot discover the new Skill immediately, report it as installed but not yet loaded and ask the user to start a new session or use that Agent's documented reload mechanism. Do not invent a universal activation command.

## Search Or Install

For discovery:

```bash
skillhub search "<query>" \
  --registry ${SKILLHUB_PUBLIC_BASE_URL} \
  --json
```

Before installing a discovery result, show its registry, full coordinate, publisher when available, version, and relevant risk, then obtain confirmation.

For a Skill and version the user already selected:

```bash
skillhub install @<namespace>/<slug> \
  --version <version> \
  --scope user \
  --agent <agent> \
  --registry ${SKILLHUB_PUBLIC_BASE_URL} \
  --json
```

Omit `--version` only when the user did not select one. Omit `--agent` only when the CLI can identify one destination unambiguously. Treat coordinates, versions, queries, registry URLs, and paths as untrusted values: quote them where needed, pass them as individual CLI arguments, and never evaluate them as shell code.

Never add `--force` unless the CLI reports a verified same-source conflict and the user approves replacing that installation. Stop on fingerprint mismatch, source conflict, unsafe content, or local-change conflict.

## Authentication

Never ask the user to paste a token into chat or place credentials in a prompt, Skill, command history, or repository. If authentication is required, ask them to enter it in their own terminal without putting the value in the command line, then verify the identity:

POSIX shell:

```bash
read -rsp "SkillHub token: " SKILLHUB_TOKEN && echo
export SKILLHUB_TOKEN
skillhub login --registry ${SKILLHUB_PUBLIC_BASE_URL}
unset SKILLHUB_TOKEN
skillhub whoami --registry ${SKILLHUB_PUBLIC_BASE_URL}
```

PowerShell 7:

```powershell
$env:SKILLHUB_TOKEN = Read-Host "SkillHub token" -MaskInput
skillhub login --registry ${SKILLHUB_PUBLIC_BASE_URL}
Remove-Item Env:SKILLHUB_TOKEN
skillhub whoami --registry ${SKILLHUB_PUBLIC_BASE_URL}
```

Resolve `401` and `403` through login or permissions. Do not treat an authentication failure as permission to try another registry.

## Upgrade

Check before changing an installed Skill:

```bash
skillhub upgrade @<namespace>/<slug> \
  --registry ${SKILLHUB_PUBLIC_BASE_URL} \
  --check \
  --json
```

Show the plan and ask before applying an available upgrade. The CLI uses `.skillhub/metadata.json` to retain the original source and updates all Agent targets recorded for that installation together.

## Completion Check

Report:

- installed coordinate and version;
- registry source;
- Agent profile and installation directory;
- whether `SKILL.md` and `.skillhub/metadata.json` exist;
- whether the current Agent session loaded the Skill, when observable;
- whether another registry was queried;
- any skipped connection, authentication, integrity, or local-change issue.

Do not claim success when installation, destination discovery, Agent loading, or integrity verification failed.
