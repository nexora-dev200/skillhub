# Skill Version Compare Redesign

**Date:** 2026-05-06
**Issue:** [#267](https://github.com/iflytek/skillhub/issues/267)
**Status:** Approved

## Problem Statement

当前技能版本对比功能（PR #345）存在以下问题：
1. **交互不便**：逐文件点击展开/折叠，无法一次性浏览所有变更
2. **功能缺失**：缺少文件树导航、全局统计、文件搜索等关键功能
3. **视觉局限**：对话框容器限制了 diff 呈现空间

用户需要类似 GitHub PR Files Changed 的体验：独立页面、左侧文件树、右侧 diff 主区、一次性加载所有变更。

## Goals

1. 提供独立的版本对比页面，支持 URL 分享和收藏
2. 类 GitHub PR Files Changed 的双栏布局（左侧文件树 + 右侧 diff 主区）
3. 后端计算 diff 并返回结构化数据，前端专注渲染
4. 支持文件树导航、全局统计、文件搜索、超大文件按需加载
5. 仅支持 unified 视图（简化 MVP 范围）

## Non-Goals

- Split 视图（左右双栏对照）
- Markdown 渲染对比（README 也按文本 diff 处理）
- 行内评论（inline comments）
- 三方合并冲突解决
- 对比草稿版本或审核中版本

---

## Design Overview

### Architecture Choice: 方案 A

**后端**：
- 新增 API `GET /api/v1/skills/{ns}/{slug}/versions/compare?from=vX&to=vY`
- 使用 `java-diff-utils` 库（轻量级 ~100KB，无传递依赖）计算行级 diff
- 返回结构化 JSON：文件列表 + 每个文件的 hunks（变更块）+ 行号映射 + 统计数据
- 超大文件（>1MB 或 >5000 行）标记为 `truncated: true`，前端按需加载

**前端**：
- 新路由 `/skills/:namespace/:slug/compare`，TanStack Router
- 使用 `react-diff-view`（Uber 开源，~50KB，专为 React 设计）
- 左侧文件树：Radix Collapsible + 虚拟滚动（文件数 >100 时）
- 右侧 diff 主区：`react-diff-view` 渲染 unified 视图，Prism.js syntax highlight
- 顶部版本选择器：Radix Select 双下拉框（base → head）

**依赖库选型依据**（详见后端调研报告）：
- `java-diff-utils` 优于 `diff-match-patch`（字符级需转换）和 JGit（5.5MB 过重）
- `react-diff-view` 成熟稳定，API 清晰，bundle 小，社区活跃

---

## User Flow

1. 用户在技能详情页版本列表中点击某版本的"对比版本"按钮
2. 跳转到 `/skills/{ns}/{slug}/compare?from={当前版本}&to={最新已发布版本}`
3. 页面加载，显示双栏布局：
   - 顶部：版本选择器（两个下拉框）+ 全局统计（X 文件，+Y/-Z 行）
   - 左侧：文件树（可搜索、可折叠、点击跳转）
   - 右侧：所有文件的 diff 纵向铺开，长页面滚动
4. 用户可切换版本下拉框，URL 更新，数据重新加载
5. 用户可在文件树搜索框输入关键词过滤文件
6. 用户点击文件树中的文件，右侧滚动到对应 diff 位置
7. 超大文件默认折叠，显示"Load diff"按钮，点击后加载

---

## Backend API Design

### Primary API

**Endpoint**: `GET /api/v1/skills/{namespace}/{slug}/versions/compare`

**Query Parameters**:
- `from`: 源版本号（必填）
- `to`: 目标版本号（必填）

**Response Structure**:
```json
{
  "from": "1.0.0",
  "to": "1.2.0",
  "summary": {
    "totalFiles": 12,
    "addedFiles": 3,
    "modifiedFiles": 7,
    "removedFiles": 2,
    "addedLines": 245,
    "removedLines": 89
  },
  "files": [
    {
      "path": "src/main.py",
      "changeType": "MODIFIED",
      "oldSize": 1024,
      "newSize": 1280,
      "isBinary": false,
      "isTruncated": false,
      "hunks": [
        {
          "oldStart": 10,
          "oldLines": 5,
          "newStart": 10,
          "newLines": 8,
          "lines": [
            {
              "type": "CONTEXT",
              "content": "def foo():",
              "oldLineNumber": 10,
              "newLineNumber": 10
            },
            {
              "type": "DELETE",
              "content": "    return 1",
              "oldLineNumber": 11,
              "newLineNumber": null
            },
            {
              "type": "ADD",
              "content": "    return 2",
              "oldLineNumber": null,
              "newLineNumber": 11
            }
          ]
        }
      ]
    }
  ]
}
```

**Field Descriptions**:
- `changeType`: `ADDED` | `MODIFIED` | `REMOVED`
- `isBinary`: 按扩展名判断（`.png`, `.jpg`, `.zip`, `.jar`, `.exe` 等）
- `isTruncated`: 单文件 >1MB 或 >5000 行时为 `true`，此时 `hunks` 为空数组
- `hunks[].lines[].type`: `CONTEXT` | `ADD` | `DELETE`

**Implementation**:
- Controller: `VersionCompareController` in `skillhub-app`
- Service: 新增 `VersionCompareService`（或复用现有 Service 层）
- Diff 库: `io.github.java-diff-utils:java-diff-utils:4.12`（Maven Central）
- 流程：
  1. 校验两个版本都是 `PUBLISHED` 状态
  2. 从对象存储并行拉取两个版本的文件列表和内容
  3. 对比文件列表，分类为 Added/Modified/Removed
  4. 对每个文本文件调用 `DiffUtils.diff()` 计算 hunks
  5. 二进制文件标记 `isBinary: true`，不计算 diff
  6. 超大文件标记 `isTruncated: true`，返回空 hunks
  7. 返回结构化 JSON，启用 gzip 压缩

### Secondary API (Optional, for truncated files)

**Endpoint**: `GET /api/v1/skills/{namespace}/{slug}/versions/compare/file`

**Query Parameters**:
- `from`: 源版本号
- `to`: 目标版本号
- `path`: 文件路径

**Purpose**: 强制返回超大文件的完整 diff（无大小限制），用于用户点击"Load diff"按钮时按需加载。

**Response**: 与主 API 中单个文件的结构相同，但 `isTruncated` 始终为 `false`。

---

## Frontend Design

### Route Definition

**Path**: `/skills/:namespace/:slug/compare`

**Query Parameters**:
- `from`: 源版本号（必填）
- `to`: 目标版本号（必填）

**Example**: `/skills/clawhub/git-helper/compare?from=1.0.0&to=1.2.0`

**TanStack Router Config**:
```typescript
export const Route = createFileRoute('/skills/$namespace/$slug/compare')({
  validateSearch: (search) => ({
    from: search.from as string,
    to: search.to as string,
  }),
  loaderDeps: ({ search }) => ({ from: search.from, to: search.to }),
  loader: async ({ params, deps }) => {
    await queryClient.ensureQueryData(
      skillVersionCompareQueryOptions(
        params.namespace,
        params.slug,
        deps.from,
        deps.to
      )
    )
  },
})
```

### Component Structure

```
SkillVersionComparePage (web/src/pages/skill-version-compare.tsx)
├── CompareHeader (顶部固定栏)
│   ├── VersionSelector (两个 Radix Select 下拉框)
│   └── CompareSummary (全局统计：X 文件，+Y/-Z 行)
├── CompareLayout (双栏布局容器)
│   ├── FileTree (左侧文件树，280px 宽，可折叠)
│   │   ├── FileTreeSearch (搜索框)
│   │   └── FileTreeList (文件列表)
│   │       └── FileTreeItem (单个文件项，点击跳转)
│   └── DiffMainArea (右侧 diff 主区)
│       └── FileDiffSection (每个文件一个 section)
│           ├── FileDiffHeader (文件名 + 统计 + 展开/折叠按钮)
│           └── FileDiffContent (diff 内容)
│               ├── BinaryFilePlaceholder (二进制文件占位)
│               ├── TruncatedFilePlaceholder (超大文件占位 + Load 按钮)
│               └── UnifiedDiffView (react-diff-view 渲染)
```

### Key Interactions

1. **版本切换**：顶部下拉框切换 → 更新 URL query → TanStack Query 自动重新拉取数据
2. **文件树跳转**：点击文件 → `scrollIntoView` 滚动到右侧对应 section → 高亮当前文件
3. **文件树搜索**：输入关键词 → 过滤文件列表（前端内存过滤）
4. **超大文件加载**：点击"Load diff"按钮 → 调用后端 `/compare/file` API 单独拉取该文件 diff

### Dependencies

- `react-diff-view`: diff 渲染（~50KB）
- `prismjs`: syntax highlight（按需加载语言包）
- `@tanstack/react-virtual`: 文件数 >100 时，文件树使用虚拟滚动

---

## Entry Point Modification

**Location**: `web/src/pages/skill-detail.tsx`

**Change**: 在版本列表的每个版本卡片操作区增加"对比版本"按钮。

**Logic**:
```typescript
const handleCompareVersion = (version: string) => {
  const publishedVersions = versions.filter(v => v.status === 'PUBLISHED')
  if (publishedVersions.length < 2) {
    toast.error(t('skillDetail.compareUnavailable'))
    return
  }
  const latestVersion = publishedVersions[0].version
  navigate({
    to: '/skills/$namespace/$slug/compare',
    params: { namespace, slug },
    search: { from: version, to: latestVersion }
  })
}
```

**Button Placement**: 在版本卡片的"下载"按钮旁边，仅对已发布版本显示。

---

## Edge Cases & Error Handling

| Case | Handling |
|------|----------|
| 版本不存在 | 后端返回 404，前端 toast 提示"版本不存在" |
| 非已发布版本 | 后端返回 400，前端 toast 提示"仅支持对比已发布版本" |
| 相同版本对比 | 后端返回 400，前端 toast 提示"无法对比相同版本" |
| 无权限访问 | 复用技能详情页权限逻辑，无权限 → 重定向到 404 |
| 二进制文件 | 后端按扩展名判断，前端显示"Binary file — cannot display diff" + 下载链接 |
| 超大文件 (>1MB 或 >5000 行) | 后端标记 `isTruncated: true`，前端显示"Load diff"按钮，点击后调用 `/compare/file` API |
| 完全相同的版本 | 前端显示空状态："No changes between these versions" |
| API 请求失败 | 显示错误提示 + 重试按钮 |
| 文件数 >100 | 文件树使用 `@tanstack/react-virtual` 虚拟滚动 |

**Binary File Extensions**:
`.png`, `.jpg`, `.jpeg`, `.gif`, `.ico`, `.woff`, `.woff2`, `.ttf`, `.eot`, `.zip`, `.tar`, `.gz`, `.jar`, `.war`, `.class`, `.so`, `.dll`, `.exe`, `.pdf`

---

## i18n Keys

新增 key 到 `web/src/i18n/locales/zh.json` 和 `en.json`：

```json
{
  "skillCompare": {
    "pageTitle": "版本对比",
    "selectBaseVersion": "选择基准版本",
    "selectTargetVersion": "选择目标版本",
    "filesChanged": "{count} 个文件变更",
    "linesAdded": "+{count} 行",
    "linesRemoved": "-{count} 行",
    "searchFiles": "搜索文件",
    "noFilesFound": "未找到匹配文件",
    "fileAdded": "新增",
    "fileModified": "修改",
    "fileRemoved": "删除",
    "binaryFile": "二进制文件，无法显示差异",
    "fileTooLarge": "文件过大 ({size} / {lines} 行)，点击加载",
    "loadDiff": "加载差异",
    "noChanges": "这两个版本之间无变更",
    "errorLoadingCompare": "加载对比数据失败",
    "retry": "重试",
    "onlyPublishedVersions": "仅支持对比已发布版本",
    "sameVersionError": "无法对比相同版本",
    "compareUnavailable": "至少需要两个已发布版本才能对比"
  }
}
```

---

## Testing Strategy

### Backend Unit Tests (JUnit 5 + Spring Boot Test)

**`VersionCompareControllerTest`**:
- 正常对比：返回正确的文件列表和 hunks
- 版本不存在：返回 404
- 非已发布版本：返回 400
- 相同版本：返回 400
- 权限校验：私有技能未授权访问返回 403

**`VersionCompareServiceTest`**（如果抽取 Service 层）:
- 文本文件 diff 计算正确性
- 二进制文件识别
- 超大文件截断逻辑
- 空文件、单行文件等边界情况

### Frontend Unit Tests (Vitest + React Testing Library)

- `VersionSelector.test.tsx`：下拉框切换更新 URL
- `FileTree.test.tsx`：搜索过滤、点击跳转
- `FileDiffSection.test.tsx`：二进制占位、超大文件占位、diff 渲染

### E2E Tests (Playwright)

**File**: `web/e2e/skill-version-compare.spec.ts`

**Scenario**:
1. 前置条件：上传同一技能的两个版本（v1.0.0 和 v1.1.0），v1.1.0 修改了 2 个文件
2. 进入技能详情页
3. 点击 v1.0.0 的"对比版本"按钮
4. 验证跳转到 `/compare?from=1.0.0&to=1.1.0`
5. 验证顶部显示"2 个文件变更"
6. 验证左侧文件树显示 2 个文件
7. 点击第一个文件，验证右侧滚动到对应位置
8. 验证 diff 内容正确渲染（检查 DOM 中是否有 `+` 和 `-` 行）
9. 切换目标版本下拉框，验证 URL 更新并重新加载数据

---

## Implementation Checklist

### Backend
- [ ] 引入 `java-diff-utils:4.12` 依赖到 `skillhub-app/pom.xml`
- [ ] 新增 `VersionCompareController` 和 `/compare` API
- [ ] 实现 diff 计算逻辑（Service 层）
- [ ] 实现二进制文件判断
- [ ] 实现超大文件截断逻辑
- [ ] 编写单元测试
- [ ] 更新 OpenAPI 文档

### Frontend
- [ ] 安装依赖：`react-diff-view`, `prismjs`, `@tanstack/react-virtual`
- [ ] 新增路由 `/skills/$namespace.$slug.compare.tsx`
- [ ] 实现 `SkillVersionComparePage` 组件
- [ ] 实现 `CompareHeader` 和 `VersionSelector`
- [ ] 实现 `FileTree` 和搜索功能
- [ ] 实现 `DiffMainArea` 和 `FileDiffSection`
- [ ] 集成 `react-diff-view` 渲染 unified diff
- [ ] 实现超大文件按需加载
- [ ] 添加 i18n keys
- [ ] 编写单元测试
- [ ] 编写 E2E 测试

### Entry Point
- [ ] 修改 `skill-detail.tsx`，增加"对比版本"按钮
- [ ] 实现跳转逻辑

---

## Performance Considerations

1. **后端响应体压缩**：启用 gzip（Spring Boot 默认支持）
2. **文件内容并行拉取**：使用 `CompletableFuture` 并行从对象存储拉取文件
3. **前端虚拟滚动**：文件数 >100 时，文件树使用 `@tanstack/react-virtual`
4. **Syntax highlight 按需加载**：Prism.js 语言包按需加载，避免 bundle 膨胀
5. **超大文件按需加载**：>1MB 或 >5000 行的文件默认折叠，点击后才加载

---

## Future Enhancements (Out of Scope for MVP)

- Split 视图（左右双栏对照）
- Markdown 渲染对比（README 特殊处理）
- 行内评论功能
- 对比草稿版本或审核中版本
- 后端分页返回（文件数 >500 时）
- 文件树按目录层级折叠
- Diff 统计图表（文件变更热力图）
