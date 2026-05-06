# SkillHub CLI

Manage and install skills for AI coding agents.

SkillHub is an enterprise-grade, self-hosted skill registry that enables teams to discover, share, and install reusable skills for AI coding agents like Claude Code. This CLI provides a seamless interface to interact with SkillHub registries.

## Installation

```bash
npm install -g @astron-team/skillhub
```

## Quick Start

### Using the default registry

```bash
# Login to the default registry
skillhub login

# Search for skills
skillhub search react

# Install a skill
skillhub install @astron-team/react-component-builder

# List installed skills
skillhub list
```

### Using a custom registry

```bash
# Set custom registry URL
export SKILLHUB_REGISTRY=https://skillhub.yourcompany.com

# Login to custom registry
skillhub login

# Install from custom registry
skillhub install @yourorg/custom-skill
```

## Commands

### Authentication

- `skillhub login` - Authenticate with a SkillHub registry
- `skillhub logout` - Remove stored credentials

### Skill Management

- `skillhub search <query>` - Search for skills in the registry
- `skillhub install <skill-name>` - Install a skill to ~/.claude/skills/
- `skillhub uninstall <skill-name>` - Remove an installed skill
- `skillhub list` - List all installed skills
- `skillhub info <skill-name>` - Show detailed information about a skill

### Utilities

- `skillhub version` - Display CLI version
- `skillhub help` - Show help information

## Examples

### Search and install a skill

```bash
# Search for React-related skills
skillhub search react

# Install a specific skill
skillhub install @astron-team/react-component-builder

# Verify installation
skillhub list
```

### Manage installed skills

```bash
# View details about an installed skill
skillhub info @astron-team/react-component-builder

# Uninstall a skill
skillhub uninstall @astron-team/react-component-builder
```

### Work with custom registries

```bash
# Set custom registry for your organization
export SKILLHUB_REGISTRY=https://skillhub.yourcompany.com

# Login to your private registry
skillhub login

# Search and install from your private registry
skillhub search internal-tools
skillhub install @yourorg/internal-skill
```

## Registry

### Default registry

By default, the CLI connects to the public SkillHub registry at `https://skillhub.astron.team`. This registry hosts community-contributed skills that are freely available.

### Custom registry

Organizations can deploy their own private SkillHub instance. To use a custom registry, set the `SKILLHUB_REGISTRY` environment variable:

```bash
export SKILLHUB_REGISTRY=https://skillhub.yourcompany.com
```

You can also configure this in your shell profile (~/.bashrc, ~/.zshrc) to make it persistent.

### Skill namespaces

Skills are namespaced by organization to prevent naming conflicts:

- `@astron-team/skill-name` - Skills from the Astron team
- `@yourorg/skill-name` - Skills from your organization

When installing skills, always include the full namespaced name.

## License

Apache-2.0

Copyright 2026 iFlytek Co., Ltd.
