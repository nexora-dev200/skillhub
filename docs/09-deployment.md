# skillhub 部署架构与运维

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
│ PostgreSQL│  │ Redis  │    │ S3 / MinIO   │
│ (主从)    │  │        │    │              │
└────────┘  └────────┘    └──────────────┘
```

## 2 服务配置

- 无状态设计，所有状态存储在 PostgreSQL / Redis / S3
- 健康检查：`/actuator/health`（liveness + readiness 分离）
- 优雅停机：`spring.lifecycle.timeout-per-shutdown-phase=30s`
- JVM：`-XX:MaxRAMPercentage=75.0`

## 3 环境 Profile

| Profile | 用途 | 特点 |
|---------|------|------|
| `local` | 本地开发 | Docker Compose 一键启动（PostgreSQL/Redis/MinIO），Mock OAuth（见下方说明） |
| `dev` | 开发环境 | 共享基础设施，GitHub OAuth 测试应用 |
| `staging` | 预发布 | 与生产同构 |
| `prod` | 生产 | 多 Pod，完整基础设施 |

### 本地开发 Mock 登录

`local` profile 下提供两种开发登录方式：

1. **MockAuthFilter**（默认）：通过 `X-Mock-User-Id` Header 模拟登录，自动创建 Session，无需真实 OAuth 流程
2. **GitHub OAuth 测试应用**：配置 `OAUTH2_GITHUB_CLIENT_ID` / `OAUTH2_GITHUB_CLIENT_SECRET` 后可走真实 OAuth 流程（GitHub 支持 `http://localhost` 回调）

MockAuthFilter 仅在 `local` profile 激活，通过 `@Profile("local")` 注解保证不会泄漏到其他环境。

### Docker Compose 一键启动

项目提供两套 Docker Compose 配置，分别用于本地开发和完整部署。

#### docker-compose.yml — 本地开发（仅依赖服务）

本地开发时前后端在宿主机运行，Docker Compose 只拉起依赖服务：

```yaml
# docker-compose.yml（项目根目录）
services:
  postgres:
    image: postgres:16-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: skillhub
      POSTGRES_USER: skillhub
      POSTGRES_PASSWORD: skillhub_dev
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"    # MinIO Console
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data

volumes:
  postgres_data:
  minio_data:
```

#### docker-compose.prod.yml — 完整部署（前后端 + 依赖服务）

开发完成后，通过 `docker compose -f docker-compose.prod.yml up -d` 一键打包并启动整个系统：

```yaml
# docker-compose.prod.yml（项目根目录）
services:
  postgres:
    image: postgres:16-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: skillhub
      POSTGRES_USER: skillhub
      POSTGRES_PASSWORD: ${DB_PASSWORD:-skillhub_prod}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U skillhub"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5

  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-minioadmin}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-minioadmin}
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 5s
      timeout: 5s
      retries: 5

  server:
    build:
      context: ./server
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://postgres:5432/skillhub
      DATABASE_USERNAME: skillhub
      DATABASE_PASSWORD: ${DB_PASSWORD:-skillhub_prod}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      S3_ENDPOINT: http://minio:9000
      S3_ACCESS_KEY: ${MINIO_ROOT_USER:-minioadmin}
      S3_SECRET_KEY: ${MINIO_ROOT_PASSWORD:-minioadmin}
      S3_BUCKET: skillhub
      OAUTH2_GITHUB_CLIENT_ID: ${OAUTH2_GITHUB_CLIENT_ID}
      OAUTH2_GITHUB_CLIENT_SECRET: ${OAUTH2_GITHUB_CLIENT_SECRET}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      minio:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  web:
    build:
      context: ./web
      dockerfile: Dockerfile
    ports:
      - "80:80"
    depends_on:
      server:
        condition: service_healthy

volumes:
  postgres_data:
  minio_data:
```

#### 前后端 Dockerfile

后端 Dockerfile（`server/Dockerfile`）：
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY skillhub-app/pom.xml skillhub-app/
COPY skillhub-domain/pom.xml skillhub-domain/
COPY skillhub-auth/pom.xml skillhub-auth/
COPY skillhub-search/pom.xml skillhub-search/
COPY skillhub-storage/pom.xml skillhub-storage/
COPY skillhub-infra/pom.xml skillhub-infra/
RUN mvn dependency:go-offline -B
COPY . .
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/skillhub-app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

前端 Dockerfile（`web/Dockerfile`）：
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

前端 Nginx 配置（`web/nginx.conf`）：
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # SPA 路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理到后端
    location /api/ {
        proxy_pass http://server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # OAuth2 回调反向代理
    location /oauth2/ {
        proxy_pass http://server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /login/oauth2/ {
        proxy_pass http://server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Well-known 发现端点
    location /.well-known/ {
        proxy_pass http://server:8080;
        proxy_set_header Host $host;
    }
}
```

### Spring Boot 配置文件分层

```
server/skillhub-app/src/main/resources/
├── application.yml              # 公共配置（所有 profile 共享）
├── application-local.yml        # 本地开发（Docker Compose 服务地址）
├── application-dev.yml          # 开发环境
├── application-staging.yml      # 预发布
└── application-prod.yml         # 生产
```

`application.yml`（公共配置）：
```yaml
spring:
  application:
    name: skillhub
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate    # 由 Flyway 管理 schema，Hibernate 仅校验
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  shutdown: graceful

spring.lifecycle.timeout-per-shutdown-phase: 30s
```

`application-local.yml`（本地开发，对应 Docker Compose）：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/skillhub
    username: skillhub
    password: skillhub_dev
  data:
    redis:
      host: localhost
      port: 6379
  jpa:
    show-sql: true

skillhub:
  storage:
    type: s3
    endpoint: http://localhost:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket: skillhub
    region: us-east-1
  access-policy:
    mode: OPEN    # 本地开发默认开放准入
```

`application-prod.yml`（生产环境，凭证从环境变量/K8s Secret 注入）：
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
  jpa:
    show-sql: false

skillhub:
  storage:
    type: s3
    endpoint: ${S3_ENDPOINT}
    access-key: ${S3_ACCESS_KEY}
    secret-key: ${S3_SECRET_KEY}
    bucket: ${S3_BUCKET:skillhub}
    region: ${S3_REGION:us-east-1}
```

### 本地开发启动流程

```bash
# 1. 启动依赖服务
docker compose up -d

# 2. 启动后端（自动执行 Flyway 迁移）
cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 3. 启动前端
cd web && pnpm dev
```

### 完整部署（一键打包 + 启动）

```bash
# 构建并启动所有服务（前后端 + 依赖）
docker compose -f docker-compose.prod.yml up -d --build

# 仅重新构建并重启应用服务（依赖服务不重启）
docker compose -f docker-compose.prod.yml up -d --build server web

# 停止所有服务
docker compose -f docker-compose.prod.yml down

# 停止并清除数据卷（慎用）
docker compose -f docker-compose.prod.yml down -v
```

### Makefile 命令

```bash
make dev              # docker compose up -d + 后端 + 前端（本地开发）
make dev-down         # docker compose down
make build            # 构建后端 JAR + 前端 dist
make docker           # 构建前后端 Docker 镜像
make deploy           # docker compose -f docker-compose.prod.yml up -d --build
make deploy-down      # docker compose -f docker-compose.prod.yml down
make generate-api     # 生成 OpenAPI 类型
```

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

### CI Pipeline 构建

```
代码提交 → CI Pipeline
    ├── server: mvn package → JAR
    └── web: pnpm build → dist/
         │
         ▼
    Docker 多阶段构建
    ├── server → eclipse-temurin:21-jre-alpine
    └── web → nginx:alpine
         │
         ▼
    推送镜像 → K8s 滚动更新
```

### Docker Compose 完整部署

```
make deploy
    │
    ▼
docker compose -f docker-compose.prod.yml up -d --build
    │
    ├── 构建 server 镜像（Maven 多阶段构建 → JRE 运行）
    ├── 构建 web 镜像（pnpm build → Nginx 静态服务 + 反向代理）
    ├── 拉起 PostgreSQL / Redis / MinIO
    ├── 等待依赖服务健康检查通过
    ├── 启动 server（自动执行 Flyway 迁移）
    └── 启动 web（Nginx 代理 API 到 server）
```

Makefile 顶层命令：`make dev`, `make dev-down`, `make build`, `make docker`, `make deploy`, `make deploy-down`, `make generate-api`

## 7 数据库迁移

Flyway 管理 schema 变更：
- 脚本路径：`server/skillhub-app/src/main/resources/db/migration/`
- 命名：`V{version}__{description}.sql`
- 多 Pod 安全：Flyway 自带数据库锁
