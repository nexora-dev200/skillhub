# 常见问题

## Q: SkillHub 和 ClawHub 有什么区别？

A: SkillHub 是企业级的自托管方案，提供了更强的权限控制、审核机制和治理能力。ClawHub 是公共注册中心，类似 npm。

**主要区别**：

| 特性 | SkillHub | ClawHub |
|------|----------|---------|
| **部署方式** | 自托管 | 公共云 |
| **权限控制** | 命名空间 RBAC | 基础权限 |
| **审核机制** | 多级审核 | 无 |
| **安全扫描** | 内置 Skill Scanner | 无 |
| **数据主权** | 完全自主 | 托管在云端 |
| **适用场景** | 企业内部 | 公开分享 |

## Q: 如何备份数据？

A: SkillHub 的数据存储在 PostgreSQL 和对象存储中。定期备份这两部分即可。

**备份 PostgreSQL**：
```bash
pg_dump -h localhost -U postgres skillhub > backup.sql
```

**备份对象存储**：
- 如果使用 MinIO，备份 MinIO 数据目录
- 如果使用 S3，使用 AWS CLI 或 S3 备份工具

## Q: 支持哪些认证方式？

A: SkillHub 支持多种认证方式：

- **OAuth2**：GitHub、Google、GitLab 等
- **本地账号**：用户名密码登录（内置管理员：admin / ChangeMe!2026）
- **企业 SSO**：可以集成 LDAP、SAML 等

配置方式参考项目 README 中的认证配置章节。

## Q: 技能包大小有限制吗？

A: 默认限制为 **100MB**。可以通过配置调整：

```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

## Q: 如何使用 CLI 工具管理技能包？

A: SkillHub 兼容 OpenClaw CLI，使用 `npx clawhub` 命令即可操作：

```bash
# 配置注册中心地址
export CLAWHUB_REGISTRY=http://your-skillhub-host:8080

# 搜索技能包
npx clawhub search email

# 安装技能包
npx clawhub install my-skill

# 发布技能包
npx clawhub publish ./my-skill
```

## Q: 如何配置 HTTPS？

A: 生产环境建议使用 Nginx 或 Traefik 作为反向代理，配置 SSL 证书。

**Nginx 配置示例**：
```nginx
server {
    listen 443 ssl;
    server_name skillhub.example.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location / {
        proxy_pass http://localhost:3000;
    }
    
    location /api {
        proxy_pass http://localhost:8080;
    }
}
```

## Q: 如何监控 SkillHub？

A: SkillHub 提供了多种监控方式：

- **健康检查**：`GET /actuator/health`
- **Scanner 健康检查**：`GET http://localhost:8000/health`
- **指标监控**：`GET /actuator/metrics`（Prometheus 格式）
- **审计日志**：所有关键操作都会记录到审计日志
- **应用日志**：使用 ELK 或 Loki 收集日志

## Q: 支持多租户吗？

A: SkillHub 通过命名空间实现了逻辑上的多租户隔离。每个命名空间相当于一个租户，拥有独立的成员、权限和技能包。

如果需要物理隔离，可以为每个租户部署独立的 SkillHub 实例。

## Q: 如何升级 SkillHub？

A: 使用 curl 命令升级：

```bash
# 拉取最新镜像并重启
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- pull
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- down
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up

# 或直接指定版本升级
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --version v0.2.0
```

> **注意**：升级前建议先备份数据库和对象存储。数据库迁移由 Flyway 自动执行。升级不会清空数据库，已录入的技能包不会丢失。

## Q: 为什么管理员（admin）和普通用户都无法创建命名空间？

A: 较旧版本的 SkillHub 不支持创建命名空间。该功能是在后续版本迭代中添加的。请将您的 SkillHub 升级到最新版本（latest）。
升级命令示例：
```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --version latest
```

## Q: 如何搜索或操作指定命名空间中的技能包（Skill）？

A: 使用 OpenClaw CLI 命令行工具时，可以通过 `<namespace>--<skill-name>` 的格式来指定命名空间进行操作（例如搜索、安装）。如果在网页端搜索遇到问题，也可以尝试通过先导出技能、再导入到目标命名空间的方式来完成跨空间操作。

## Q: 推荐的部署方式是什么？可以自己拉镜像手动部署吗？

A: 推荐使用官方一键部署脚本，不建议自己拉取镜像手动部署（手动部署容易出现登录后跳回登录页等初始化问题）：

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --aliyun --public-url https://skillhub.your-company.com --version latest
```

脚本会执行一系列初始化操作，生成的运行时配置默认位于 `/tmp/skillhub-runtime/`（包含 `.env.release` 和 docker-compose 文件）。

## Q: 部署后输入正确的账号密码，却又跳回登录页？

A: 该现象多见于「手动部署」场景（接口异常或初始化未完成导致）。建议：

1. 改用上面的一键脚本部署。
2. 必要时清空 PostgreSQL 数据卷后重建再登录。
3. 若前置了反向代理，检查代理配置是否正确转发。

## Q: 如何修改 admin 密码？修改配置后不生效？

A: 环境变量在容器启动时读取，修改后必须重启容器才会生效。

1. 修改运行时目录下的 `/tmp/skillhub-runtime/.env.release`（参考仓库 [.env.release.example](https://github.com/iflytek/skillhub/blob/main/.env.release.example)）。
2. 重启相关容器。
3. 若此前密码已写入数据库导致仍不生效，可能需要清理对应数据后重新初始化。

## Q: 修改 / 找回密码必须使用邮箱验证码吗？

A: 是的，默认通过邮箱验证码修改或找回密码，因此需要先配置 SMTP。配置方法参考 [docs/19-smtp-password-reset-email-setup.md](https://github.com/iflytek/skillhub/blob/main/docs/19-smtp-password-reset-email-setup.md)。管理员也可在 `.env.release` 中进行重置。

## Q: skill 可以起中文名吗？

A: skill name 一般使用英文，目前不支持中文名（在 OpenClaw 中使用中文 skill 名会报错）。

## Q: 未审核的 skill 可以下载吗？

A: 只要拥有可查看的权限，一般都可以下载。

## Q: 如何隐藏或删除登录页的 GitHub / GitLab SSO 登录方式？

A: 修改 `application.yml`，注释或删除 `spring.security.oauth2.client.registration` 下的 `github` 和 `gitlab` 两块，并删除对应的 `provider` 段。Spring Boot 启动时便不会创建这两个注册，登录页也不会再显示对应入口。

## Q: SkillHub 的安全扫描（Skill Scanner）是讯飞自研的吗？使用什么协议？

A: SkillHub 内置安全扫描能力。其中扫描接入、任务编排、审计落库和部署集成由讯飞团队实现；底层扫描服务使用 Cisco 的 [cisco-ai-skill-scanner](https://github.com/cisco-ai-defense/skill-scanner)（Apache License 2.0，版权归 Cisco）。

## Q: SkillHub 使用的 cisco-ai-skill-scanner 是哪个版本？

A: `scanner/Dockerfile` 中直接执行 `pip install cisco-ai-skill-scanner`，未锁定版本，因此构建镜像时会拉取 PyPI 上的最新版本。如需固定版本，可在二次开发时自行锁定。

## Q: 遇到问题怎么办？

A: 可以通过以下方式获取帮助：

- **GitHub Issues**: https://github.com/iflytek/skillhub/issues
- **在线文档**: https://www.astron-skillhub.org/
- **文档**: 参考项目 README.md
- **社区讨论**: https://github.com/iflytek/skillhub/discussions

## Q: 本地开发启动失败怎么办？

A: `make dev-all` 后端启动失败时，会显示详细的错误提示。常见问题：

### 1. Maven 依赖下载失败（网络超时）

**症状**：后端日志显示 `Could not transfer artifact` 或连接超时

**解决方案**：配置阿里云镜像

```bash
# 复制项目内置的镜像配置到用户目录
mkdir -p ~/.m2
cp server/.mvn/settings.xml ~/.m2/settings.xml
```

或手动创建 `~/.m2/settings.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

参考：[阿里云 Maven 镜像配置指南](https://maven.aliyun.com/mvn/guide)

### 2. Java 版本不匹配

**症状**：`Unsupported class file major version` 或 `java.lang.NoSuchMethodError`

**解决方案**：安装 Java 21+

```bash
# macOS
brew install openjdk@21

# 验证版本
java -version
```

### 3. 端口被占用

**症状**：`Port 8080 already in use`

**解决方案**：

```bash
# 查看占用端口的进程
lsof -i :8080

# 终止进程
kill -9 <PID>
```

### 4. 查看详细日志

如果以上方案无法解决，查看后端日志：

```bash
make dev-logs SERVICE=backend
# 或直接查看
cat .dev/server.log
```
