#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# run.sh — Start the AIHealthcare Spring Boot application with .env loaded.
#
# Automatically sources .env (if present) so API keys are available without
# manually exporting them. Logs to logs/aihealthcare.log (tailable).
#
# Usage:
#   ./run.sh              # foreground
#   ./run.sh &            # background
#   tail -f logs/aihealthcare.log   # watch logs
#
# @author  Bill Blackmon
# @since   2026-07-30
# -----------------------------------------------------------------------------

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Source .env if it exists
if [ -f .env ]; then
    echo "[run.sh] Loading .env ..."
    set -a
    source .env
    set +a
fi

mkdir -p logs

echo "[run.sh] Starting AIHealthcare (mvn spring-boot:run) ..."
exec mvn spring-boot:run -DskipTests
