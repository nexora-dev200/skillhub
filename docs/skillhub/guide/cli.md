# SkillHub CLI

SkillHub CLI 是 SkillHub 的第一方命令行工具，用于搜索、安装、管理和发布 Agent 技能包。

## 安装

```bash
# 通过 npm 全局安装
npm install -g skillhub

# 或使用 npx 直接运行
npx skillhub@latest version

# 或通过 Bun 全局安装
bun add -g skillhub
```

## 快速开始

```bash
# 登录
skillhub login --token sk_xxx

# 搜索技能
skillhub search pdf

# 安装技能到 Agent 目录
skillhub install pdf-parser --agent codex

# 查看已安装技能
skillhub list

# 发布技能
skillhub publish ./my-skill --namespace myspace
```

## Registry 配置

当前生效的 registry 按以下优先级解析：

1. `--registry <url>` 命令行参数
2. `SKILLHUB_REGISTRY` 环境变量
3. 用户配置中的 `registry`
4. 默认值 `https://skill.xfyun.cn`

```bash
# 临时使用其他 registry
skillhub search pdf --registry https://skillhub.example.com

# 通过环境变量设置
export SKILLHUB_REGISTRY=https://skillhub.example.com
```

## 认证

### 登录

```bash
# 使用 API token 登录
skillhub login --token sk_xxx

# 指定 registry 登录
skillhub login --token sk_xxx --registry https://skillhub.example.com
```

Token 只存储在用户目录 `~/.skillhub/credentials.json` 中，不会写入任何项目文件。

### 查看当前身份

```bash
skillhub whoami
```

### 登出

```bash
skillhub logout
```

登出只删除 token，保留 registry 配置和安装记录。

## 搜索

```bash
skillhub search pdf
skillhub search "" --limit 20
skillhub search pdf --json
```

## 安装

```bash
# 安装到自动探测的 Agent 目录
skillhub install pdf-parser

# 指定 namespace
skillhub install pdf-parser --namespace myspace

# 指定版本
skillhub install pdf-parser --version 1.2.0

# 安装到指定 Agent
skillhub install pdf-parser --agent codex

# 安装到多个 Agent
skillhub install pdf-parser --agent codex --agent claude-code

# 安装到自定义目录
skillhub install pdf-parser --dir ~/.claude/skills

# 强制覆盖已存在的安装
skillhub install pdf-parser --force
```

### 支持的 Agent

内建支持以下 Tier 1 Agent：

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

对于不在列表中的 Agent，使用 `--dir` 指定安装路径。

## 本地管理

### 查看已安装技能

```bash
skillhub list
skillhub list --agent codex
skillhub list --json
```

### 删除本地技能

```bash
# 删除所有本地安装目标
skillhub remove pdf-parser

# 只删除指定 Agent 的安装
skillhub remove pdf-parser --agent codex

# 删除远程技能
skillhub remove pdf-parser --remote --hard --namespace myspace
```

### 重建本地清单

```bash
skillhub doctor
```

`doctor` 扫描当前项目下的 `.*/skills/<slug>/.skillhub/metadata.json`，重建 `inventory.json`。

## 发布

```bash
skillhub publish ./my-skill --namespace myspace
skillhub publish ./my-skill --namespace myspace --visibility private
```

## 自更新

```bash
# 检查是否有新版本
skillhub update --check

# 执行更新
skillhub update
```

## JSON 输出

所有命令都支持 `--json` 参数，输出机器可读的 JSON 格式：

```bash
skillhub search pdf --json
skillhub list --json
skillhub whoami --json
```

## 安全说明

- Token 只存储在用户目录 `~/.skillhub/credentials.json`
- 在 Linux/macOS 上，凭据文件权限设置为 `0600`
- 不会将 token 写入任何项目本地文件
- 远程删除操作需要显式确认或 `--hard` 参数
