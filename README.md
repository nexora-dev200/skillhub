# SkillHub

AI Skill 共享平台 — 发布、发现、管理 AI 技能包。

## 快速开始

### 前置条件

- Docker & Docker Compose
- JDK 21+（开发模式）
- Node.js 20+ / pnpm（前端开发）

### 一键启动（生产模式）

```bash
make prod-up
```

访问:
- 前端: http://localhost
- API: http://localhost:8080
- 健康检查: http://localhost:8080/actuator/health

### 开发模式

```bash
# 启动基础设施（PostgreSQL + Redis + MinIO）
make dev

# 启动后端
make dev-server

# 启动前端（另一个终端）
make dev-web
```

## 项目结构

```
skillhub/
├── server/                  # Spring Boot 后端
│   ├── skillhub-app/        # 应用层 (Controller, DTO, Config)
│   ├── skillhub-domain/     # 领域层 (Entity, Service, Repository)
│   ├── skillhub-auth/       # 认证授权模块
│   ├── skillhub-search/     # 搜索模块 (PostgreSQL 全文检索)
│   ├── skillhub-storage/    # 存储模块 (本地/S3)
│   └── skillhub-infra/      # 基础设施层 (JPA 实现)
├── web/                     # React 前端
├── docker-compose.yml       # 开发环境（仅基础设施）
├── docker-compose.prod.yml  # 生产环境（全栈）
└── Makefile                 # 常用命令
```

## 常用命令

```bash
make help          # 查看所有命令
make dev           # 启动开发基础设施
make dev-server    # 启动后端开发服务器
make dev-web       # 启动前端开发服务器
make test          # 运行后端测试
make test-web      # 运行前端测试
make build         # 构建后端
make build-web     # 构建前端
make prod-up       # 一键启动全部服务
make prod-down     # 停止全部服务
make logs          # 查看后端日志
make db-reset      # 重置数据库
make clean         # 清理构建产物
```

## 技术栈

- **后端:** Spring Boot 3, JDK 21, PostgreSQL 16, Redis 7, Flyway, Spring Security (OAuth2 + 本地认证)
- **前端:** React 19, TypeScript, Vite, TanStack Router/Query, shadcn/ui, Tailwind CSS
- **存储:** MinIO (S3 兼容) / 本地文件系统
- **运维:** Docker, Nginx

## License

MIT
