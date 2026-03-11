# Astron Skills 部署架构与运维

## 1 K8s 部署拓扑

```
                    ┌─────────────┐
                    │   Ingress   │
                    │  (Nginx)    │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │ /api/*                  │ /*
              ▼                         ▼
    ┌──────────────────┐    ┌──────────────────┐
    │  Spring Boot     │    │  Nginx / CDN     │
    │  replicas: 2+    │    │  静态资源          │
    └────────┬─────────┘    └──────────────────┘
             │
    ┌────────┴──────────────────────┐
    │            │                  │
    ▼            ▼                  ▼
┌────────┐  ┌────────┐    ┌──────────────┐
│ MySQL  │  │ Redis  │    │ S3 / MinIO   │
│ (主从)  │  │        │    │              │
└────────┘  └────────┘    └──────────────┘
```

## 2 服务配置

- 无状态设计，所有状态存储在 MySQL / Redis / S3
- 健康检查：`/actuator/health`（liveness + readiness 分离）
- 优雅停机：`spring.lifecycle.timeout-per-shutdown-phase=30s`
- JVM：`-XX:MaxRAMPercentage=75.0`

## 3 环境 Profile

| Profile | 用途 | 特点 |
|---------|------|------|
| `local` | 本地开发 | 本地 MySQL/MinIO，Mock OAuth（见下方说明） |
| `dev` | 开发环境 | 共享基础设施，GitHub OAuth 测试应用 |
| `staging` | 预发布 | 与生产同构 |
| `prod` | 生产 | 多 Pod，完整基础设施 |

### 本地开发 Mock 登录

`local` profile 下提供两种开发登录方式：

1. **MockAuthFilter**（默认）：通过 `X-Mock-User-Id` Header 模拟登录，自动创建 Session，无需真实 OAuth 流程
2. **GitHub OAuth 测试应用**：配置 `OAUTH2_GITHUB_CLIENT_ID` / `OAUTH2_GITHUB_CLIENT_SECRET` 后可走真实 OAuth 流程（GitHub 支持 `http://localhost` 回调）

MockAuthFilter 仅在 `local` profile 激活，通过 `@Profile("local")` 注解保证不会泄漏到其他环境。

## 4 配置管理

- 敏感配置：K8s Secret（数据库/Redis/S3 凭证、OAuth2 Client ID/Secret）
- 非敏感配置：K8s ConfigMap（文件大小限制、Session TTL 等）

## 5 可观测性

| 维度 | 方案 |
|------|------|
| 日志 | JSON 格式 stdout，包含 traceId/requestId |
| 指标 | Actuator + Micrometer → Prometheus |
| 链路追踪 | 一期 requestId 透传，后续接 Jaeger/Zipkin |
| 告警 | 基于 Prometheus（5xx 率、延迟 P99、Pod 重启） |

requestId 透传：Ingress 注入 → Spring Filter 读取放入 MDC → 日志自动携带 → 响应 Header 回传。

## 6 构建与发布

```
代码提交 → CI Pipeline
    ├── server: mvn package → JAR
    └── web: pnpm build → dist/
         │
         ▼
    Docker 多阶段构建
    ├── server → openjdk:21-jre-slim
    └── web → nginx:alpine
         │
         ▼
    推送镜像 → K8s 滚动更新
```

Makefile 顶层命令：`make dev-server`, `make dev-web`, `make build`, `make docker`, `make generate-api`

## 7 数据库迁移

Flyway 管理 schema 变更：
- 脚本路径：`server/astron-skills-app/src/main/resources/db/migration/`
- 命名：`V{version}__{description}.sql`
- 多 Pod 安全：Flyway 自带数据库锁
