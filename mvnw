#!/bin/sh
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/backend/mvnw" ]; then
    exec "$SCRIPT_DIR/backend/mvnw" -f "$SCRIPT_DIR/backend/pom.xml" "$@"
else
    echo "[ERROR] backend/mvnw not found at $SCRIPT_DIR/backend" >&2
    exit 1
fi
