# SkillHub Helm Chart

企业级 AI 技能中心私有化部署方案，基于 Kubernetes 和 Helm。

## 特性

- **微服务架构**：Server（Spring Boot）、Web（Nginx）、Scanner 分离部署
- **高可用**：支持 HPA 自动扩缩容、PDB Pod 中断预算
- **数据层**：使用 Bitnami PostgreSQL/Redis，支持主从复制、哨兵模式
- **安全**：TLS 证书管理、Secret 密码保护、NetworkPolicy
- **可观测性**：内置 Prometheus metrics exporter

## 快速开始

### 前置要求

- Kubernetes 1.24+
- Helm 3.8+
- kubectl configured

### 安装

```bash
kubectl create namespace skillhub

helm -n skillhub upgrade -i skillhub ./charts/skillhub \
  --set bootstrapAdmin.password=your-secure-password
```

### 高可用模式

```bash
helm -n skillhub upgrade -i skillhub ./charts/skillhub \
  --set bootstrapAdmin.password=your-secure-password \
  --set postgresql.architecture=replication \
  --set redis.architecture=replication
```

### 外部数据库模式

```bash
helm -n skillhub upgrade -i skillhub ./charts/skillhub \
  --set bootstrapAdmin.password=your-secure-password \
  --set postgresql.enabled=false \
  --set redis.enabled=false \
  --set externalDatabase.host=postgres.example.com \
  --set externalDatabase.port=5432 \
  --set externalDatabase.database=skillhub \
  --set externalDatabase.username=skillhub \
  --set externalDatabase.password=your-db-password \
  --set externalRedis.host=redis.example.com \
  --set externalRedis.port=6379 \
  --set externalRedis.password=your-redis-password
```

## 配置参考

### 副本数配置

| 参数 | 描述 | 默认值 |
|------|------|--------|
| `server.replicaCount` | Server 副本数 | `1` |
| `web.replicaCount` | Web 副本数 | `1` |
| `scanner.replicaCount` | Scanner 副本数 | `1` |

```bash
# 差异化副本配置
helm -n skillhub upgrade -i skillhub ./charts/skillhub \
  --set server.replicaCount=3 \
  --set web.replicaCount=2 \
  --set scanner.replicaCount=1
```

### 服务配置

| 参数 | 描述 | 默认值 |
|------|------|--------|
| `server.service.type` | Server Service 类型 | `ClusterIP` |
| `server.service.port` | Server 端口 | `8080` |
| `web.service.type` | Web Service 类型 | `ClusterIP` |
| `web.service.port` | Web 端口 | `80` |
| `scanner.service.port` | Scanner 端口 | `8000` |

### 数据库配置

| 参数 | 描述 | 默认值 |
|------|------|--------|
| `postgresql.enabled` | 启用内置 PostgreSQL | `true` |
| `postgresql.architecture` | 架构模式 | `standalone` |
| `redis.enabled` | 启用内置 Redis | `true` |
| `redis.architecture` | 架构模式 | `standalone` |

### 存储配置

| 参数 | 描述 | 默认值 |
|------|------|--------|
| `server.storage.accessMode` | 访问模式：ReadWriteOnce（单副本）或 ReadWriteMany（多副本） | `""` |
| `server.storage.size` | PVC 大小 | `10Gi` |
| `server.storage.storageClassName` | StorageClass | `""` |

```bash
# 默认使用本地 PVC
helm -n skillhub upgrade -i skillhub ./charts/skillhub \
  --set bootstrapAdmin.password=your-secure-password
```

### S3 对象存储

`s3.enabled=true` 时，不创建 PVC，应用使用 S3 作为存储后端。

| 参数 | 描述 | 默认值 |
|------|------|--------|
| `s3.enabled` | 启用 S3 | `false` |
| `s3.bucket` | Bucket 名称 | `skillhub-storage` |
| `s3.endpoint` | S3 端点 | `""` |
| `s3.region` | 区域 | `us-east-1` |
| `s3.accessKey` | Access Key | `""` |
| `s3.secretKey` | Secret Key | `""` |

```bash
helm -n skillhub upgrade -i skillhub ./charts/skillhub \
  --set bootstrapAdmin.password=your-secure-password \
  --set s3.enabled=true \
  --set s3.bucket=your-bucket \
  --set s3.endpoint=s3.amazonaws.com \
  --set s3.region=us-east-1 \
  --set s3.accessKey=your-access-key \
  --set s3.secretKey=your-secret-key
```

### Ingress + TLS

```bash
helm -n skillhub upgrade -i skillhub ./charts/skillhub \
  --set ingress.enabled=true \
  --set ingress.host=skills.example.com \
  --set ingress.tls.enabled=true \
  --set ingress.certManager.enabled=true
```

### 自动扩缩容

```bash
helm -n skillhub upgrade -i skillhub ./charts/skillhub \
  --set server.autoscaling.enabled=true \
  --set server.autoscaling.minReplicas=2 \
  --set server.autoscaling.maxReplicas=10
```

## 卸载

```bash
helm -n skillhub uninstall skillhub
```

## 依赖

| 依赖 | 版本 |
|------|------|
| postgresql | 18.6.10 |
| redis | 25.5.3 |
