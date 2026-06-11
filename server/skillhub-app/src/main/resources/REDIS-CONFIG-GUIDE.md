# Redis Configuration Guide

SkillHub supports both **standalone** and **cluster** Redis deployment modes.

## Configuration Modes

### Standalone Mode (Default)

This is the default mode for local development and simple deployments.

**Configuration in `application.yml`:**
```yaml
spring:
  data:
    redis:
      mode: standalone  # or omit this line (default)
      host: localhost
      port: 6379
      password: ""
      database: 0
```

**Environment Variables:**
```bash
SPRING_DATA_REDIS_MODE=standalone
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=""
SPRING_DATA_REDIS_DATABASE=0
```

### Cluster Mode

For production environments requiring high availability and scalability.

**Configuration in `application.yml`:**
```yaml
spring:
  data:
    redis:
      mode: cluster
      password: "your-password"  # optional
      cluster:
        nodes:
          - redis-node1:6379
          - redis-node2:6379
          - redis-node3:6379
        max-redirects: 3
```

**Environment Variables:**
```bash
SPRING_DATA_REDIS_MODE=cluster
SPRING_DATA_REDIS_CLUSTER_NODES=redis-node1:6379,redis-node2:6379,redis-node3:6379
SPRING_DATA_REDIS_CLUSTER_MAX_REDIRECTS=3
SPRING_DATA_REDIS_PASSWORD=your-password
```

## Switching Between Modes

To switch from standalone to cluster mode:

1. Set `SPRING_DATA_REDIS_MODE=cluster`
2. Configure cluster nodes via `SPRING_DATA_REDIS_CLUSTER_NODES`
3. Restart the application

To switch back to standalone mode:

1. Set `SPRING_DATA_REDIS_MODE=standalone` (or unset it)
2. Configure single node via `SPRING_DATA_REDIS_HOST` and `SPRING_DATA_REDIS_PORT`
3. Restart the application

## Example Configurations

See `application-cluster-example.yml` for a complete cluster configuration example.

## Notes

- The default mode is `standalone` if `spring.data.redis.mode` is not specified
- Cluster mode requires at least one node to be configured
- Both modes use Lettuce as the Redis client
- Session storage and all Redis-dependent features work with both modes
