#!/usr/bin/env bash
set -euo pipefail

CHART_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TEST_VALUES="$CHART_DIR/tests/test-values.yaml"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

render() {
  helm template "$@" -f "$TEST_VALUES"
}

assert_rejected() {
  local name=$1
  shift
  if render "$name" "$CHART_DIR" "$@" >"$TMP_DIR/$name.yaml" 2>"$TMP_DIR/$name.err"; then
    fail "$name should have been rejected"
  fi
}

render verify "$CHART_DIR" >"$TMP_DIR/default.yaml"
grep -Fq 'name: POSTGRESQL_MAX_CONNECTIONS' "$TMP_DIR/default.yaml"
grep -Fq 'value: "verify-postgresql"' "$TMP_DIR/default.yaml"
grep -Fq 'value: "verify-redis-master"' "$TMP_DIR/default.yaml"

stable_args=(
  --set-string secrets.bootstrapAdminPassword=stable-bootstrap-password
  --set-string secrets.downloadAnonCookieSecret=stable-download-cookie-secret
  --set-string postgresql.auth.postgresPassword=stable-postgres-password
  --set-string postgresql.auth.password=stable-user-password
  --set-string redis.auth.password=stable-redis-password
)
render stable "$CHART_DIR" "${stable_args[@]}" >"$TMP_DIR/stable-a.yaml"
render stable "$CHART_DIR" "${stable_args[@]}" >"$TMP_DIR/stable-b.yaml"
cmp "$TMP_DIR/stable-a.yaml" "$TMP_DIR/stable-b.yaml"

render private-registry "$CHART_DIR" \
  --set server.dependencyWait.image.registry=registry.example.com \
  --set server.dependencyWait.image.repository=library/busybox \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/private-registry.yaml"
grep -Fq 'image: "registry.example.com/library/busybox:1.37"' "$TMP_DIR/private-registry.yaml"

render postgresql-replication "$CHART_DIR" \
  --set postgresql.architecture=replication >"$TMP_DIR/postgresql-replication.yaml"
if [[ $(grep -Fc 'name: POSTGRESQL_MAX_CONNECTIONS' "$TMP_DIR/postgresql-replication.yaml") -ne 2 ]]; then
  fail "PostgreSQL primary and read replica must use the same max_connections setting"
fi

render custom "$CHART_DIR" \
  --set postgresql.auth.existingSecret=custom-pg \
  --set postgresql.auth.secretKeys.userPasswordKey=custom-pg-key \
  --set redis.auth.existingSecret=custom-redis \
  --set redis.auth.existingSecretPasswordKey=custom-redis-key \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/custom.yaml"
grep -Fq 'name: custom-pg' "$TMP_DIR/custom.yaml"
grep -Fq 'key: custom-pg-key' "$TMP_DIR/custom.yaml"
grep -Fq 'name: custom-redis' "$TMP_DIR/custom.yaml"
grep -Fq 'key: custom-redis-key' "$TMP_DIR/custom.yaml"

render sentinel "$CHART_DIR" \
  --set redis.architecture=replication \
  --set redis.sentinel.enabled=true \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/sentinel.yaml"
grep -Fq 'value: "docker,redis-sentinel"' "$TMP_DIR/sentinel.yaml"
grep -Fq 'value: "mymaster"' "$TMP_DIR/sentinel.yaml"
grep -Fq '.svc.cluster.local:26379' "$TMP_DIR/sentinel.yaml"
grep -Fq 'name: SPRING_DATA_REDIS_PASSWORD' "$TMP_DIR/sentinel.yaml"
grep -Fq 'name: SPRING_DATA_REDIS_SENTINEL_PASSWORD' "$TMP_DIR/sentinel.yaml"

render external-sentinel "$CHART_DIR" \
  --set postgresql.enabled=false \
  --set externalDatabase.host=db.example.com \
  --set redis.enabled=false \
  --set externalRedis.password=redis-password \
  --set externalRedis.sentinel.enabled=true \
  --set externalRedis.sentinel.password=sentinel-password \
  --set-json 'externalRedis.sentinel.nodes=["sentinel-a:26379","sentinel-b:26379"]' \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/external-sentinel.yaml"
grep -Fq 'value: "sentinel-a"' "$TMP_DIR/external-sentinel.yaml"
grep -Fq 'name: SPRING_DATA_REDIS_PASSWORD' "$TMP_DIR/external-sentinel.yaml"
grep -Fq 'name: SPRING_DATA_REDIS_SENTINEL_PASSWORD' "$TMP_DIR/external-sentinel.yaml"

render special "$CHART_DIR" \
  --set-string 'bootstrapAdmin.displayName=Ops: Admin' \
  --show-only templates/configmap.yaml >"$TMP_DIR/special.yaml"
grep -Fq 'bootstrap-admin-display-name: "Ops: Admin"' "$TMP_DIR/special.yaml"

render device "$CHART_DIR" \
  --set publicBaseUrl=https://skills.example.com \
  --show-only templates/configmap.yaml >"$TMP_DIR/device.yaml"
grep -Fq 'device-auth-verification-uri: "https://skills.example.com/cli/auth"' "$TMP_DIR/device.yaml"

render tls "$CHART_DIR" \
  --set ingress.enabled=true \
  --set-json 'ingress.tls=[{"hosts":["skills.example.com"],"secretName":"skills-tls"}]' \
  --show-only templates/configmap.yaml >"$TMP_DIR/tls.yaml"
grep -Fq 'session-cookie-secure: "true"' "$TMP_DIR/tls.yaml"

render legacy-ingress "$CHART_DIR" \
  --set ingress.enabled=true \
  --set-string ingress.className= \
  --set-json 'ingress.annotations={"kubernetes.io/ingress.class":"alb","alb.ingress.kubernetes.io/listen-ports":"[{\"HTTPS\":6443}]"}' \
  --show-only templates/ingress.yaml >"$TMP_DIR/legacy-ingress.yaml"
grep -Fq 'kubernetes.io/ingress.class: alb' "$TMP_DIR/legacy-ingress.yaml"
grep -Fq 'alb.ingress.kubernetes.io/listen-ports:' "$TMP_DIR/legacy-ingress.yaml"
if grep -Fq 'ingressClassName:' "$TMP_DIR/legacy-ingress.yaml"; then
  fail "empty ingress.className must omit spec.ingressClassName"
fi

render multi-host-ingress "$CHART_DIR" \
  --set ingress.enabled=true \
  --set ingress.certManager.enabled=true \
  --set-json 'ingress.hosts=[{"host":"skills-a.example.com","paths":[{"path":"/","pathType":"Prefix"}]},{"host":"skills-b.example.com","paths":[{"path":"/portal","pathType":"Prefix"}]}]' \
  --set-json 'ingress.tls=[{"hosts":["skills-a.example.com","skills-b.example.com"],"secretName":"skills-tls"}]' \
  --show-only templates/ingress.yaml \
  --show-only templates/certificate.yaml >"$TMP_DIR/multi-host-ingress.yaml"
if [[ $(grep -Fc 'skills-a.example.com' "$TMP_DIR/multi-host-ingress.yaml") -ne 3 ]]; then
  fail "first ingress host must be rendered in rule, TLS and Certificate"
fi
if [[ $(grep -Fc 'skills-b.example.com' "$TMP_DIR/multi-host-ingress.yaml") -ne 3 ]]; then
  fail "second ingress host must be rendered in rule, TLS and Certificate"
fi

render scanner-off "$CHART_DIR" \
  --set scanner.enabled=false \
  --set scanner.autoscaling.enabled=true \
  --set scanner.podDisruptionBudget.enabled=true >"$TMP_DIR/scanner-off.yaml"
if awk '
  $1 == "kind:" { kind=$2 }
  kind ~ /^(Deployment|Service|HorizontalPodAutoscaler|PodDisruptionBudget)$/ &&
    $1 == "name:" && $2 == "scanner-off-skillhub-scanner" { found=1 }
  END { exit found ? 0 : 1 }
' "$TMP_DIR/scanner-off.yaml"; then
  fail "disabled scanner rendered workload resources"
fi

render multi-rwx "$CHART_DIR" \
  --set server.replicaCount=2 \
  --set server.storage.accessMode=ReadWriteMany >"$TMP_DIR/multi-rwx.yaml"
grep -Fq -- '- ReadWriteMany' "$TMP_DIR/multi-rwx.yaml"

assert_rejected server-off --set server.enabled=false
assert_rejected direct-auth-without-provider \
  --set auth.direct.enabled=true \
  --set-string auth.direct.provider=
assert_rejected ingress-without-server-service --set ingress.enabled=true --set server.service.enabled=false
assert_rejected ingress-without-web-service --set ingress.enabled=true --set web.service.enabled=false
assert_rejected multi-without-rwx --set server.replicaCount=2
assert_rejected hpa-without-metrics \
  --set server.autoscaling.enabled=true \
  --set server.autoscaling.targetCPUUtilizationPercentage=0 \
  --set server.autoscaling.targetMemoryUtilizationPercentage=0
assert_rejected old-postgres-env --set-json 'postgresql.primary.extraEnv=[{"name":"X","value":"Y"}]'
assert_rejected old-sentinel-password --set redis.auth.sentinelPassword=unused
assert_rejected old-sentinel-nodes --set redis.sentinel.nodes=unused
assert_rejected old-sentinel-service-switch --set redis.sentinel.service.enabled=false
assert_rejected invalid-fullname --set fullnameOverride=INVALID_NAME
assert_rejected old-ingress-host --set ingress.host=old.example.com
assert_rejected old-ingress-tls-object --set ingress.tls.enabled=true
assert_rejected empty-ingress-hosts --set-json 'ingress.hosts=[]'
assert_rejected cert-manager-without-tls \
  --set ingress.enabled=true \
  --set ingress.certManager.enabled=true \
  --set-json 'ingress.tls=[]'
if helm template missing-credentials "$CHART_DIR" >"$TMP_DIR/missing-credentials.yaml" 2>"$TMP_DIR/missing-credentials.err"; then
  fail "default rendering without stable credentials should have been rejected"
fi

echo "Helm configuration contract tests passed"
