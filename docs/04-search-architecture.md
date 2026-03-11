# Astron Skills 搜索架构

## 1 SPI 接口

```java
public interface SearchIndexService {
    void index(SkillSearchDocument doc);
    void batchIndex(List<SkillSearchDocument> docs);
    void remove(Long skillId);
}

public interface SearchQueryService {
    SearchResult search(SearchQuery query);
}

public interface SearchRebuildService {
    void rebuildAll();
    void rebuildByNamespace(Long namespaceId);
    void rebuildBySkill(Long skillId);
}
```

## 2 SearchQuery 模型

```java
public record SearchQuery(
    String keyword,
    Long namespaceId,           // 可选，指定空间搜索
    String namespaceSlug,       // 可选
    SearchVisibilityScope scope, // ACL 投影，由应用服务层计算注入
    SortField sortBy,           // RELEVANCE / DOWNLOADS / RATING / NEWEST
    int page,
    int size
) {}

// 搜索可见范围投影，由应用服务层根据当前用户计算
public record SearchVisibilityScope(
    boolean includeAllPublic,        // 是否包含所有 PUBLIC 技能
    Set<Long> memberNamespaceIds,    // 用户是 MEMBER 的 namespace（可见 NAMESPACE_ONLY）
    Set<Long> adminNamespaceIds,     // 用户是 ADMIN 的 namespace（可见 PRIVATE）
    Long userId                      // 当前用户 ID（可见自己的 PRIVATE skill），匿名为 null
) {}
```

ACL 投影计算规则：
- 匿名用户：`includeAllPublic=true`，其余为空集，`userId=null`
- 已登录用户：`includeAllPublic=true`，`memberNamespaceIds` = 用户所属空间，`adminNamespaceIds` = 用户是 ADMIN 以上的空间，`userId` = 当前用户 ID

一期 MySQL 实现中，`SearchVisibilityScope` 转换为 WHERE 条件：
```sql
WHERE (visibility = 'PUBLIC')
   OR (visibility = 'NAMESPACE_ONLY' AND namespace_id IN (:memberNamespaceIds))
   OR (visibility = 'PRIVATE' AND (namespace_id IN (:adminNamespaceIds) OR owner_id = :userId))
```

迁移到 ES 时，`SearchVisibilityScope` 可直接映射为 bool query 的 should/filter 子句。

## 3 搜索文档表 skill_search_document

一个 skill 对应一条搜索文档，内容取 `latest_version_id` 对应版本。版本发布时自动更新该条文档。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| skill_id | bigint | 唯一，一 skill 一条 |
| namespace_id | bigint | 用于空间过滤 |
| owner_id | bigint | 用于 PRIVATE 可见性判定 |
| title | varchar(256) | |
| summary | varchar(512) | |
| keywords | varchar(512) | |
| search_text | text | SKILL.md 正文 + frontmatter 拼接 |
| visibility | enum | 冗余，避免搜索时 join |
| status | enum | |
| updated_at | datetime | |

唯一约束：`(skill_id)`

MySQL Full-Text Index 建在 `(title, summary, keywords, search_text)` 上。

## 4 索引写入时机

以下场景触发搜索文档更新（upsert by skill_id）：
- 审核通过（`PENDING_REVIEW → PUBLISHED`）：`latest_version_id` 自动更新，用新版本内容更新搜索文档
- 技能状态变更（隐藏/归档/恢复）：更新搜索文档的 status 字段

## 5 搜索演进路线

### 5.1 一期数据建模约束

一期"每个 skill 一条搜索文档、内容永远取 latest_version_id"是有意的简化。这个模型在以下场景下会不够用：

- 版本级检索（搜索某个旧版本的内容）
- 自定义标签/通道检索（搜索 `@beta` 标签指向的版本内容）
- 向量 chunk 索引（一个 skill 的 SKILL.md 拆成多个 embedding chunk）

这些场景不是简单换 provider 能解决的，需要改表结构和索引写入逻辑。

### 5.2 演进阶段

| 阶段 | 实现 | 索引粒度 | 切换方式 |
|------|------|---------|---------|
| 一期 | MySQL Full-Text | 每 skill 一条（latest_version_id） | 默认 |
| 二期 | ES / OpenSearch | 每 skill_version 一条 + skill 聚合文档 | 配置 `search.provider=elasticsearch` |
| 三期 | 向量检索 | 每 skill_version 多条（chunk 级） | 配置 `search.provider=vector` |
| 四期 | 混合排序 | 关键词 + 向量混合 | 配置 `search.provider=hybrid` |

### 5.3 SPI 演进策略

一期 SPI 接口（`SearchIndexService` / `SearchQueryService`）的入参是 `SkillSearchDocument`（skill 粒度）。二期切换到 ES 时：

1. 新增 `SkillVersionSearchDocument` 模型（version 粒度）
2. `SearchIndexService` 新增 `indexVersion()` 方法（向下兼容，一期实现空方法）
3. ES 实现同时写入 skill 聚合文档 + version 文档
4. `SearchQueryService.search()` 的返回结果不变（仍返回 skill 级摘要），内部实现切换为 ES 查询

这意味着二期切换不是零成本的——需要新增模型、扩展 SPI、重建索引。但一期不为此过度设计，SPI 抽象保证了切换时不需要改业务层代码。

通过 `@ConditionalOnProperty` 或自定义 SPI 加载机制切换。

## 6 分布式安全

`rebuildAll()` / `rebuildByNamespace()` 执行前获取 Redis 分布式锁（key: `search:rebuild:{scope}`，TTL: 10min），获取失败则跳过。

## 7 MySQL 全文搜索中文支持

MySQL Full-Text Index 必须使用 ngram parser：

```sql
ALTER TABLE skill_search_document
ADD FULLTEXT INDEX ft_search (title, summary, keywords, search_text)
WITH PARSER ngram;
```

配置 `ngram_token_size=2`（my.cnf）。

已知局限：ngram 分词精度不如专业搜索引擎，中文搜索体验有限。建议 Phase 2 完成后评估搜索效果，如不满足需求则在 Phase 3 提前引入 ES。
