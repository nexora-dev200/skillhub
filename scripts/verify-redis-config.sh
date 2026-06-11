#!/bin/bash
# Redis Configuration Verification Script
# This script helps verify Redis configuration is working correctly

set -e

echo "=== Redis Configuration Verification ==="
echo ""

# Check if running in standalone or cluster mode
REDIS_MODE=${SPRING_DATA_REDIS_MODE:-standalone}
echo "Redis Mode: $REDIS_MODE"

if [ "$REDIS_MODE" = "standalone" ]; then
    REDIS_HOST=${SPRING_DATA_REDIS_HOST:-${REDIS_HOST:-localhost}}
    REDIS_PORT=${SPRING_DATA_REDIS_PORT:-${REDIS_PORT:-6379}}
    echo "Host: $REDIS_HOST"
    echo "Port: $REDIS_PORT"
    
    # Test connection
    echo "Testing Redis connection..."
    if command -v redis-cli &> /dev/null; then
        if redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping | grep -q PONG; then
            echo "✓ Redis connection successful"
        else
            echo "✗ Redis connection failed"
            exit 1
        fi
    else
        echo "⚠ redis-cli not found, skipping connection test"
    fi
    
elif [ "$REDIS_MODE" = "cluster" ]; then
    REDIS_NODES=${SPRING_DATA_REDIS_CLUSTER_NODES:-}
    if [ -z "$REDIS_NODES" ]; then
        echo "✗ Cluster nodes not configured"
        exit 1
    fi
    
    echo "Cluster Nodes: $REDIS_NODES"
    
    # Test each node
    IFS=',' read -ra NODES <<< "$REDIS_NODES"
    for node in "${NODES[@]}"; do
        HOST=$(echo "$node" | cut -d: -f1)
        PORT=$(echo "$node" | cut -d: -f2)
        
        echo "Testing node: $HOST:$PORT"
        if command -v redis-cli &> /dev/null; then
            if redis-cli -h "$HOST" -p "$PORT" ping | grep -q PONG; then
                echo "✓ Node $HOST:$PORT is reachable"
            else
                echo "✗ Node $HOST:$PORT is not reachable"
                exit 1
            fi
        else
            echo "⚠ redis-cli not found, skipping connection test for $HOST:$PORT"
        fi
    done
else
    echo "✗ Invalid Redis mode: $REDIS_MODE"
    echo "Valid modes: standalone, cluster"
    exit 1
fi

echo ""
echo "=== Configuration Summary ==="
echo "Mode: $REDIS_MODE"
if [ "$REDIS_MODE" = "standalone" ]; then
    echo "Database: ${SPRING_DATA_REDIS_DATABASE:-0}"
elif [ "$REDIS_MODE" = "cluster" ]; then
    echo "Max Redirects: ${SPRING_DATA_REDIS_CLUSTER_MAX_REDIRECTS:-3}"
fi

echo ""
echo "✓ Redis configuration verification completed successfully"
