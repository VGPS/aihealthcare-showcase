#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# deploy.sh — Build and deploy AIHealthcare to EC2
#
# Builds the JAR locally, uploads it and the AWS config to the EC2 instance,
# restarts the systemd service, and tails the logs.
#
# Usage (from project root):
#   ./deploy/deploy.sh <ELASTIC_IP> [path/to/key.pem]
#
# Examples:
#   ./deploy/deploy.sh 54.123.45.67
#   ./deploy/deploy.sh 54.123.45.67 ~/.ssh/aihealthcare-key.pem
#
# Prerequisites:
#   - Maven installed locally
#   - SSH key with access to the EC2 instance
#   - EC2 already set up via setup-ec2.sh
# ---------------------------------------------------------------------------
set -euo pipefail

IP="${1:-100.61.13.237}"
KEY="${2:-C:/workspaces/SpringAIClaude/N_VaKeyPair.pem}"
JAR="target/ai-healthcare-1.0-SNAPSHOT.jar"
AWS_YML="application/src/main/resources/application-aws.yml"
REMOTE_DIR="/opt/aihealthcare"

echo "=== [1/4] Building JAR ==="
mvn clean package -DskipTests

if [ ! -f "$JAR" ]; then
    echo "ERROR: JAR not found at $JAR"
    exit 1
fi

echo "=== [2/4] Uploading JAR to $IP ==="
scp -i "$KEY" "$JAR" "ec2-user@${IP}:${REMOTE_DIR}/app.jar"

echo "=== [3/4] Uploading application-aws.yml ==="
scp -i "$KEY" "$AWS_YML" "ec2-user@${IP}:${REMOTE_DIR}/"

echo "=== [4/4] Restarting service ==="
ssh -i "$KEY" "ec2-user@${IP}" 'sudo systemctl restart aihealthcare'

echo ""
echo "Deploy complete. Tailing logs (Ctrl+C to stop)..."
echo ""
ssh -i "$KEY" "ec2-user@${IP}" 'sudo journalctl -u aihealthcare -f --no-pager -n 30'
