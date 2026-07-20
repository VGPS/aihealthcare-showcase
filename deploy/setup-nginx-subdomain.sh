#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# setup-nginx-subdomain.sh — Migrate Nginx from path-based to subdomain routing
#
# Replaces the single-server-block config (bigskylabs.ai/aihealthcare) with
# separate server blocks:
#   - bigskylabs.ai        → Static corporate homepage
#   - app.bigskylabs.ai    → Spring Boot AIHealthcare app (proxy to 8080)
#
# PREREQUISITES (complete these BEFORE running this script):
#   1. DNS A record for app.bigskylabs.ai → 100.61.13.237 (verify with: dig app.bigskylabs.ai +short)
#   2. Spring Boot redeployed WITHOUT /aihealthcare context path
#   3. Corporate homepage exists at /opt/bigskylabs/static/index.html
#
# Usage (from your local machine):
#   ssh -i N_VaKeyPair.pem ec2-user@100.61.13.237 'bash -s' < deploy/setup-nginx-subdomain.sh
#
# Idempotent — safe to re-run.
# ---------------------------------------------------------------------------
set -euo pipefail

DOMAIN="bigskylabs.ai"
SUBDOMAIN="app.bigskylabs.ai"
EMAIL="wgblackmonall@gmail.com"

echo "=== [1/6] Backing up current Nginx config ==="
if [ -f /etc/nginx/conf.d/aihealthcare.conf ]; then
    sudo cp /etc/nginx/conf.d/aihealthcare.conf /etc/nginx/conf.d/aihealthcare.conf.bak
    echo "Backed up to aihealthcare.conf.bak"
else
    echo "No existing aihealthcare.conf found — fresh install"
fi

echo "=== [2/6] Creating corporate homepage directory ==="
sudo mkdir -p /opt/bigskylabs/static
sudo chown -R ec2-user:ec2-user /opt/bigskylabs

# Copy existing landing page if it exists and hasn't been moved yet
if [ -f /opt/aihealthcare/static/index.html ] && [ ! -f /opt/bigskylabs/static/index.html ]; then
    cp /opt/aihealthcare/static/index.html /opt/bigskylabs/static/index.html
    echo "Copied landing page to /opt/bigskylabs/static/"
elif [ -f /opt/bigskylabs/static/index.html ]; then
    echo "Landing page already at /opt/bigskylabs/static/ — skipping"
else
    echo "WARNING: No landing page found. Create /opt/bigskylabs/static/index.html manually."
fi

echo "=== [3/6] Writing new Nginx config (pre-SSL) ==="
sudo tee /etc/nginx/conf.d/bigskylabs.conf > /dev/null << 'NGXEOF'
# ---------------------------------------------------------------
# bigskylabs.ai — Corporate homepage (static)
# ---------------------------------------------------------------
server {
    listen 80;
    server_name bigskylabs.ai;

    # Redirect old /aihealthcare/* URLs to app.bigskylabs.ai/*
    location /aihealthcare/ {
        rewrite ^/aihealthcare/(.*)$ https://app.bigskylabs.ai/$1 permanent;
    }
    location = /aihealthcare {
        return 301 https://app.bigskylabs.ai/;
    }

    location / {
        root /opt/bigskylabs/static;
        index index.html;
        try_files $uri $uri/ =404;
    }
}

# ---------------------------------------------------------------
# app.bigskylabs.ai — AIHealthcare Spring Boot app
# ---------------------------------------------------------------
server {
    listen 80;
    server_name app.bigskylabs.ai;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_connect_timeout 30s;
        proxy_send_timeout 60s;
        proxy_buffering off;
    }
}
NGXEOF

echo "=== [4/6] Removing old path-based config ==="
sudo rm -f /etc/nginx/conf.d/aihealthcare.conf
sudo rm -f /etc/nginx/conf.d/default.conf

echo "=== [5/6] Testing and reloading Nginx (HTTP-only) ==="
sudo nginx -t
sudo systemctl reload nginx
echo "Nginx reloaded with subdomain routing (HTTP)"

echo "=== [6/6] Obtaining/expanding SSL certificate ==="
sudo certbot --nginx \
    -d "$DOMAIN" \
    -d "$SUBDOMAIN" \
    --non-interactive \
    --agree-tos \
    -m "$EMAIL" \
    --redirect \
    --expand

echo ""
echo "=== Verifying auto-renewal ==="
sudo certbot renew --dry-run

echo ""
echo "=========================================="
echo "  Subdomain migration complete!"
echo "=========================================="
echo ""
echo "  https://$DOMAIN/            -> Corporate homepage"
echo "  https://$SUBDOMAIN/         -> AIHealthcare app"
echo ""
echo "  Old URL https://$DOMAIN/aihealthcare/* redirects to https://$SUBDOMAIN/*"
echo ""
echo "  Certificate covers both domains and auto-renews."
echo ""
echo "  ROLLBACK: sudo cp /etc/nginx/conf.d/aihealthcare.conf.bak /etc/nginx/conf.d/aihealthcare.conf"
echo "            sudo rm /etc/nginx/conf.d/bigskylabs.conf"
echo "            sudo nginx -t && sudo systemctl reload nginx"
echo ""
