# Astron Skills 核心业务流

## 1 发布流程

```
用户提交发布
    │
    ▼
① 身份与权限校验（用户是否为该 namespace 的 MEMBER 以上）
    │
    ▼
② 技能包校验
   - SKILL.md 存在性、frontmatter 格式
   - 文件类型白名单、单文件大小限制、总包大小限制
   - 版本号 semver 合法性、不与已有版本冲突
   - [扩展点] PrePublishValidator 链（一期空实现）
    │
    ▼
③ 写入对象存储临时区（文件逐个上传到 `tmp/{uploadId}/{filePath}`，记录 SHA-256）
    │
    ▼
④ 持久化数据
   - 创建 skill_version (status=DRAFT, file_transfer_status=PENDING)
   - 创建 skill_file 记录
   - 解析 SKILL.md frontmatter → parsed_metadata_json
   - 生成 manifest_json
   - 异步将文件从 `tmp/` 转正到 `skills/{skillId}/{versionId}/{filePath}`
   - 转正成功 → file_transfer_status=COMPLETED
   - 转正失败 → file_transfer_status=FAILED，记录失败原因
    │
    ▼
⑤ 提交审核（前置检查：file_transfer_status 必须为 COMPLETED，否则拒绝提审）
   - skill_version.status → PENDING_REVIEW
   - 创建 review_task (status=PENDING)
    │
    ▼
⑥ 审核（人工）
   ├── 通过 → skill_version.status → PUBLISHED
   │         review_task.status → APPROVED（乐观锁）
   │         更新 skill.latest_version_id（自动跟随最新已发布版本）
   │         同步写入审计日志
   │         异步触发: 搜索索引写入（取 latest_version_id 对应版本内容）
   │
   └── 拒绝 → skill_version.status → REJECTED
             review_task.status → REJECTED（乐观锁）
             记录 reject_reason
             同步写入审计日志
```

④ 和 ⑤ 分开，给用户一个检查草稿的机会。CLI 发布走同样的流程。

### 对象存储临时区与 GC

- 上传阶段文件写入 `tmp/{uploadId}/{filePath}`
- 数据库事务提交成功后，异步将文件从 `tmp/` copy 到正式路径 `skills/{skillId}/{versionId}/{filePath}`，完成后删除 `tmp/` 副本
- 定时 GC 任务：清理超过 24h 的 `tmp/` 前缀对象（覆盖事务失败、用户取消、流程中断等场景）
- 如果数据库事务失败，`tmp/` 中的文件由 GC 自动清理，不产生孤儿对象

### 文件转正补偿机制

`skill_version` 增加 `file_transfer_status` 字段（`PENDING` / `COMPLETED` / `FAILED`），用于追踪异步转正状态。

安全门控：
- 提交审核（DRAFT → PENDING_REVIEW）：前置检查 `file_transfer_status = COMPLETED`，否则返回 400
- 下载接口：前置检查 `file_transfer_status = COMPLETED`，否则返回 404

失败重试：
- 转正失败时标记 `file_transfer_status = FAILED`
- 定时任务每 5 分钟扫描 `file_transfer_status = PENDING` 且 `created_at > 5min ago` 或 `file_transfer_status = FAILED` 的记录，重试转正
- 最多重试 3 次，超过后标记为 FAILED 并保留，用户可在草稿页看到"文件处理失败"提示，可手动触发重试或删除该版本重新上传
- `tmp/` 中的源文件在转正成功前不删除，确保重试有源可用

### CLI publish 请求规范

```
POST /api/v1/cli/publish
Content-Type: multipart/form-data
Parts:
  - file: zip 包（必需）
  - namespace: 目标命名空间 slug（必需）
  - auto_submit: boolean（可选，默认 false，为 true 时自动提交审核）
```

CLI 默认行为：上传 → 创建 DRAFT → 自动提交审核（`auto_submit=true`）。
Web 端默认行为：上传 → 创建 DRAFT → 用户预览确认 → 手动提交审核。

### CLI publish 异步协议

`auto_submit=true` 时，服务端在文件转正完成后自动提交审核，CLI 不需要额外调用 submit-review。

```
CLI 调用 POST /api/v1/cli/publish (auto_submit=true)
    │
    ▼
服务端返回 202 Accepted + publishId + 初始状态
    │
    ▼
CLI 轮询 GET /api/v1/cli/publish/{publishId}/status
    │
    ├── TRANSFERRING → 文件转正中，继续轮询（建议间隔 2s）
    ├── SUBMITTED → 文件转正完成 + 已自动提交审核，CLI 结束
    ├── DRAFT → auto_submit=false 时，文件转正完成但未提审，CLI 结束
    └── FAILED → 文件转正失败，返回错误原因，CLI 提示用户
```

`/api/v1/cli/publish/{publishId}/status` 响应：

```json
{
  "data": {
    "publishId": "uuid",
    "skillVersionId": 123,
    "fileTransferStatus": "COMPLETED",
    "versionStatus": "PENDING_REVIEW",
    "error": null
  }
}
```

## 2 团队技能提升到全局空间（派生发布）

不直接修改原 skill 的 `namespace_id`，而是在全局空间创建新的 skill，保留来源追溯。原团队 skill 继续存在，安装坐标 `@team/skill` 不受影响。

```
团队空间技能（已发布）
    │
    ▼
① 技能 owner 或 namespace admin 发起"提升到全局"申请
    │
    ▼
② 创建 promotion_request (source_skill_id, source_version_id, target_namespace_id, status=PENDING)
    │
    ▼
③ 平台管理员审核
   ├── 通过 →
   │   ① 在全局空间创建新 skill（source_skill_id = 原 skill ID）
   │   ② 复制 source_version_id 对应版本的文件和元数据到新 skill（严格使用申请时指定的版本，不取最新）
   │   ③ 新 skill.visibility = PUBLIC
   │   ④ promotion_request.target_skill_id = 新 skill ID，status → APPROVED
   │   ⑤ 搜索索引写入新 skill，同步写入审计日志
   │   （提升关系唯一事实来源是 promotion_request，UI 查询"是否已提升"通过该表判定）
   │
   └── 拒绝 → 记录原因，原技能不受影响
```

后续版本更新：
- 全局空间的新 skill 由其 owner 独立管理版本
- 原团队 skill 可继续独立迭代
- 两者版本不自动同步，如需同步由 owner 手动操作

## 3 下载流程

```
下载请求
    │
    ▼
① 校验技能状态（ACTIVE）、版本状态（PUBLISHED）
    │
    ▼
② 可见性检查
   - PUBLIC: 任何人（包括匿名用户）
   - NAMESPACE_ONLY: 该 namespace 的成员（需登录）
   - PRIVATE: owner 本人 + 该 namespace 的 ADMIN 以上（需登录）
    │
    ▼
③ 返回预生成包或按文件清单打包
    │
    ▼
④ 审计与统计
   - audit_log 同步写入（记录下载人/IP/版本）
   - download_count 异步更新（原子 SQL: download_count = download_count + 1）
   - 匿名下载：审计记录 IP + User-Agent，不关联用户
   - 已登录下载：审计记录用户 ID
```

### download_count 热点行优化预案

一期使用原子 SQL 直接更新，可接受。如出现热点行瓶颈，切换为：
1. Redis `INCR` 做实时计数（key: `skill:downloads:{skillId}`）
2. 定时任务每 5 分钟批量回写 MySQL
3. 查询时合并 MySQL 存量 + Redis 增量

## 4 搜索流程

```
搜索请求 (keyword, namespaceSlug?, sortBy)
    │
    ▼
① 构建 SearchQuery
   - 匿名用户：visibility 限定为 PUBLIC
   - 已登录用户：根据命名空间成员关系计算可见范围
    │
    ▼
② SearchQueryService.search(query)
    │
    ▼
③ 返回分页结果（技能摘要 + 命名空间信息 + 评分 + 下载量）
```

## 5 收藏流程

```
收藏/取消收藏（需登录）→ 校验权限 → 写入/删除 skill_star
→ 异步更新 skill.star_count（原子 SQL）
```

## 6 评分流程

```
提交评分 (score: 1-5)（需登录）→ 校验权限 → 写入/更新 skill_rating
→ 异步重算 skill.rating_avg 和 rating_count（SELECT AVG + Redis 分布式锁防重复重算）
```

## 7 异步事件汇总

| 事件 | 触发时机 | 消费方 |
|------|---------|--------|
| `SkillPublishedEvent` | 审核通过 | 搜索索引写入 |
| `SkillYankedEvent` | 版本撤回 | 搜索索引移除 |
| `SkillDownloadedEvent` | 下载完成 | 下载计数 |
| `SkillStarredEvent` | 收藏/取消 | 收藏计数 |
| `SkillRatedEvent` | 评分提交 | 评分重算 |
| `ReviewCompletedEvent` | 审核完成 | 通知提交者（一期可选） |
| `SkillPromotedEvent` | 提升到全局 | 搜索索引写入（新 skill） |

一期用 Spring ApplicationEvent + `@Async` 实现，后续可替换为消息队列。

### 审计日志写入策略

审计日志统一同步落库，与业务操作在同一请求内同步写入，不走异步事件。审计是企业内部平台的刚性需求，不可容忍丢失。

异步事件仅用于搜索索引、计数器等可容忍延迟的场景。如果后续需要更强一致性，引入 outbox 模式，不依赖 ApplicationEvent + @Async 承担可靠性。

### 异步事件可靠性保障

Spring ApplicationEvent + @Async 存在 Pod 被杀时事件丢失的风险。补充以下兜底机制：

- 搜索索引：定时任务每小时检查 `skill_version.status = PUBLISHED` 但 `skill_search_document` 中无对应记录的版本，补建索引
- 计数器：可接受少量丢失，定时任务每天凌晨从 `skill_star` / `skill_rating` 表重算修正
- 优雅停机：`@Async` 线程池配置 `awaitTerminationSeconds=25`，配合 30s shutdown timeout

## 8 分布式并发安全措施

| 操作 | 并发控制方式 |
|------|-------------|
| 审核通过/拒绝 | 乐观锁：`UPDATE review_task SET status=? WHERE id=? AND version=?` |
| 版本发布 | 唯一约束：`(skill_id, version)` |
| 计数器更新 | 原子 SQL：`SET count = count + 1` |
| 评分重算 | 异步 + Redis 分布式锁防重复重算 |
| 写操作幂等 | Redis 存储 `X-Request-Id`，TTL 24h |

### 幂等去重规范

基于 `idempotency_record` 表实现完整幂等：

- `X-Request-Id` 由客户端生成（UUID v4 格式）
- 客户端不传时，服务端自动生成但不做幂等去重

去重流程：
1. Redis `SETNX` key=`idempotent:{requestId}`（快速去重缓存，TTL=24h）
   - key 已存在：查询 `idempotency_record` 表返回原始结果
2. key 不存在：插入 `idempotency_record`（status=`PROCESSING`）
3. 执行业务逻辑
4. 成功：更新 record 为 `COMPLETED`，填充 `resource_type` + `resource_id` + `response_status_code`
5. 失败：更新 record 为 `FAILED`
6. 重复请求时：查 record，COMPLETED 返回原始资源 ID，PROCESSING 返回 `409 Conflict`，FAILED 允许重试

适用范围：所有 POST/PUT/DELETE 写操作（发布、提审、创建 Token 等）

异常恢复策略：
- Redis key 存在但 `idempotency_record` 无记录（进程在两步之间崩溃）：视为脏状态，删除 Redis key，允许请求正常重入
- `idempotency_record.status = FAILED`：删除对应 Redis key，允许客户端用相同 `request_id` 重试
- `idempotency_record.status = PROCESSING` 超过 5 分钟未更新：视为僵死，标记为 FAILED，删除 Redis key，允许重试
