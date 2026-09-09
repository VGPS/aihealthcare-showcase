#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# setup-ec2.sh — First-time EC2 setup for AIHealthcare
#
# Installs Java 17, Nginx, PostgreSQL client, creates swap space, creates
# the application directory structure, configures the systemd service, and
# sets up the Nginx reverse proxy (port 80 -> 8080).
#
# Usage (from your local machine):
#   ssh -i N_VaKeyPair.pem ec2-user@100.61.13.237 'bash -s' < deploy/setup-ec2.sh
#
# Idempotent — safe to re-run if interrupted.
# Target: Amazon Linux 2023 (AL2023) on t3.small
# ---------------------------------------------------------------------------
set -euo pipefail

echo "=== [1/7] Installing Java 17 (Amazon Corretto) ==="
sudo dnf install -y java-17-amazon-corretto-devel
java -version

echo "=== [2/7] Installing Nginx ==="
sudo dnf install -y nginx

echo "=== [3/7] Installing PostgreSQL 16 client (psql) ==="
sudo dnf install -y postgresql16

echo "=== [4/8] Creating application directories ==="
sudo mkdir -p /opt/aihealthcare/NotebookLMDirectory/summaries
sudo mkdir -p /var/lib/aihealthcare/data-exports/logs
sudo mkdir -p /var/log/aihealthcare
sudo chown -R ec2-user:ec2-user /opt/aihealthcare
sudo chown -R ec2-user:ec2-user /var/lib/aihealthcare
sudo chown -R ec2-user:ec2-user /var/log/aihealthcare

echo "=== [5/8] Creating 1 GB swap space ==="
if [ ! -f /swapfile ]; then
    sudo fallocate -l 1G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo "/swapfile swap swap defaults 0 0" | sudo tee -a /etc/fstab
    echo "Swap created"
else
    echo "Swap already exists — skipping"
fi

echo "=== [6/8] Creating systemd service ==="
sudo tee /etc/systemd/system/aihealthcare.service > /dev/null << 'SVCEOF'
[Unit]
Description=AIHealthcare Spring Boot Application
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/opt/aihealthcare
EnvironmentFile=/opt/aihealthcare/.env
ExecStart=/usr/bin/java -Xmx1024m -jar /opt/aihealthcare/app.jar \
    --spring.profiles.active=aws \
    --spring.config.additional-location=file:/opt/aihealthcare/application-aws.yml
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

# --- Filesystem hardening: read-only except two paths ---
ProtectSystem=strict
ReadWritePaths=/var/lib/aihealthcare/data-exports /var/log/aihealthcare /opt/aihealthcare
ProtectHome=yes
PrivateTmp=yes
PrivateDevices=yes
ProtectKernelTunables=yes
ProtectKernelModules=yes
ProtectControlGroups=yes
RestrictSUIDSGID=yes
LockPersonality=yes
NoNewPrivileges=yes
CapabilityBoundingSet=

# --- Network: deny private/link-local space (blocks IMDS, internal subnets) ---
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX
IPAddressDeny=169.254.0.0/16 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 ::1/128 fe80::/10 fc00::/7
# IPAddressAllow=<RDS subnet CIDR>  ← uncomment and set for your VPC

# --- Resource ceilings ---
MemoryMax=3G
TasksMax=256

[Install]
WantedBy=multi-user.target
SVCEOF

sudo systemctl daemon-reload
sudo systemctl enable aihealthcare

echo "=== [7/8] Configuring Nginx reverse proxy (port 80 -> 8080) ==="
sudo tee /etc/nginx/conf.d/aihealthcare.conf > /dev/null << 'NGXEOF'
server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
    }
}
NGXEOF

sudo rm -f /etc/nginx/conf.d/default.conf
sudo systemctl enable nginx
sudo systemctl start nginx

echo ""
echo "=== [8/8] IMDSv2 reminder ==="
echo "Run this command once (or set in your launch template):"
echo "  aws ec2 modify-instance-metadata-options \\"
echo "    --instance-id <INSTANCE_ID> \\"
echo "    --http-tokens required \\"
echo "    --http-put-response-hop-limit 1"
echo ""

echo "=========================================="
echo "  EC2 setup complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "  1. Create /opt/aihealthcare/.env with your API keys (chmod 600)"
echo "  2. Upload app.jar:  scp -i key.pem target/ai-healthcare-1.0-SNAPSHOT.jar ec2-user@IP:/opt/aihealthcare/app.jar"
echo "  3. Upload config:   scp -i key.pem application/src/main/resources/application-aws.yml ec2-user@IP:/opt/aihealthcare/"
echo "  4. Start the app:   sudo systemctl start aihealthcare"
echo "  5. Check logs:      sudo journalctl -u aihealthcare -f"
echo "  6. Verify hardening: systemd-analyze security aihealthcare"
echo "  7. Set IMDSv2 (see step 8/8 above)"
echo ""
