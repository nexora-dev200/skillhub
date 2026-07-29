#!/usr/bin/env bash
set -euo pipefail

CHART_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TEST_VALUES="$CHART_DIR/tests/test-values.yaml"
NAMESPACE=${HELM_SMOKE_NAMESPACE:-skillhub-helm-smoke}
RELEASE=${HELM_SMOKE_RELEASE:-skillhub-smoke}
TIMEOUT=${HELM_SMOKE_TIMEOUT:-15m}
KEEP_ENVIRONMENT=${KEEP_HELM_SMOKE:-false}
TMP_DIR=$(mktemp -d)
PORT_FORWARD_PID=""
OWNS_NAMESPACE=false

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

for command in helm kubectl curl jq sha256sum; do
  command -v "$command" >/dev/null 2>&1 || fail "$command is required"
done

if kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
  fail "namespace $NAMESPACE already exists; choose an unused HELM_SMOKE_NAMESPACE"
fi
OWNS_NAMESPACE=true

stop_port_forward() {
  if [[ -n "$PORT_FORWARD_PID" ]]; then
    kill "$PORT_FORWARD_PID" >/dev/null 2>&1 || true
    wait "$PORT_FORWARD_PID" >/dev/null 2>&1 || true
    PORT_FORWARD_PID=""
  fi
}

cleanup() {
  local exit_code=$?
  trap - EXIT
  stop_port_forward

  if (( exit_code != 0 )) && kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
    echo "Helm smoke failed; collecting non-secret diagnostics" >&2
    helm status "$RELEASE" --namespace "$NAMESPACE" >&2 || true
    kubectl get pods,pvc,deployments,statefulsets --namespace "$NAMESPACE" -o wide >&2 || true
    kubectl get events --namespace "$NAMESPACE" --sort-by=.lastTimestamp >&2 || true
  fi

  if [[ "$KEEP_ENVIRONMENT" != "true" && "$OWNS_NAMESPACE" == "true" ]]; then
    helm uninstall "$RELEASE" --namespace "$NAMESPACE" --wait >/dev/null 2>&1 || true
    kubectl delete namespace "$NAMESPACE" --wait --timeout=5m >/dev/null 2>&1 || true
  fi

  rm -rf "$TMP_DIR"
  exit "$exit_code"
}
trap cleanup EXIT

probe_service() {
  local service=$1
  local service_port=$2
  local local_port=$3
  local path=$4
  local log_file="$TMP_DIR/${service}.port-forward.log"

  stop_port_forward
  kubectl port-forward \
    --namespace "$NAMESPACE" \
    "service/$service" \
    "$local_port:$service_port" >"$log_file" 2>&1 &
  PORT_FORWARD_PID=$!

  for _ in $(seq 1 60); do
    if curl --fail --silent --show-error "http://127.0.0.1:$local_port$path" >/dev/null; then
      stop_port_forward
      return 0
    fi
    if ! kill -0 "$PORT_FORWARD_PID" >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  cat "$log_file" >&2
  fail "$service$path did not become healthy"
}

snapshot_secrets() {
  local output=$1
  : >"$output"
  for secret in "$RELEASE-secret" "$RELEASE-postgresql" "$RELEASE-redis"; do
    printf '%s ' "$secret" >>"$output"
    kubectl get secret "$secret" --namespace "$NAMESPACE" -o json \
      | jq -cS '.data' \
      | sha256sum \
      | awk '{print $1}' >>"$output"
  done
}

snapshot_pvcs() {
  local output=$1
  kubectl get pvc --namespace "$NAMESPACE" -o json \
    | jq -r '.items[] | [.metadata.name, .metadata.uid, .spec.volumeName] | @tsv' \
    | sort >"$output"
  [[ -s "$output" ]] || fail "Helm install did not create any PVCs"
}

assert_ready_and_healthy() {
  kubectl wait pod \
    --namespace "$NAMESPACE" \
    --all \
    --for=condition=Ready \
    --timeout="$TIMEOUT"

  probe_service "$RELEASE-server" 8080 18081 /actuator/health
  probe_service "$RELEASE-web" 80 18080 /nginx-health
  probe_service "$RELEASE-web" 80 18080 /api/v1/namespaces
  probe_service "$RELEASE-scanner" 8000 18082 /health

  local restarts
  restarts=$(kubectl get pods --namespace "$NAMESPACE" -o json \
    | jq '[.items[].status.containerStatuses[]?.restartCount] | add // 0')
  [[ "$restarts" == "0" ]] || fail "workloads restarted $restarts time(s)"
}

helm dependency build "$CHART_DIR"

helm install "$RELEASE" "$CHART_DIR" \
  --namespace "$NAMESPACE" \
  --create-namespace \
  --values "$TEST_VALUES" \
  --set-string fullnameOverride="$RELEASE" \
  --set-string publicBaseUrl=http://skillhub-smoke.local \
  --wait \
  --timeout "$TIMEOUT"

assert_ready_and_healthy
snapshot_secrets "$TMP_DIR/secrets-before"
snapshot_pvcs "$TMP_DIR/pvcs-before"
revision_before=$(helm history "$RELEASE" --namespace "$NAMESPACE" -o json \
  | jq -r '.[-1].revision')

helm upgrade "$RELEASE" "$CHART_DIR" \
  --namespace "$NAMESPACE" \
  --reuse-values \
  --set-string publicBaseUrl=https://skillhub-smoke.local \
  --set-string server.podAnnotations.helm-smoke-revision=revision-2 \
  --wait \
  --timeout "$TIMEOUT"

assert_ready_and_healthy
snapshot_secrets "$TMP_DIR/secrets-after"
snapshot_pvcs "$TMP_DIR/pvcs-after"
revision_after=$(helm history "$RELEASE" --namespace "$NAMESPACE" -o json \
  | jq -r '.[-1].revision')

(( revision_after == revision_before + 1 )) \
  || fail "Helm revision did not advance exactly once"
cmp "$TMP_DIR/secrets-before" "$TMP_DIR/secrets-after" \
  || fail "application or dependency Secret data changed during upgrade"
cmp "$TMP_DIR/pvcs-before" "$TMP_DIR/pvcs-after" \
  || fail "PVC identity or bound volume changed during upgrade"

public_base_url=$(kubectl get configmap "$RELEASE-config" \
  --namespace "$NAMESPACE" \
  -o json | jq -r '.data["public-base-url"]')
[[ "$public_base_url" == "https://skillhub-smoke.local" ]] \
  || fail "publicBaseUrl was not applied by the upgrade"

echo "Helm install/upgrade smoke passed"
