#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# setup-nginx-ssl.sh — Configure Nginx with SSL for bigskylabs.ai
#
# Installs Certbot, obtains a Let's Encrypt certificate, and configures
# Nginx to:
#   - Serve the static landing page at https://bigskylabs.ai/
#   - Proxy /aihealthcare/* to Spring Boot on port 8080
#   - Redirect HTTP to HTTPS
#
# Usage (from your local machine):
#   ssh -i N_VaKeyPair.pem ec2-user@100.61.13.237 'bash -s' < deploy/setup-nginx-ssl.sh
#
# Prerequisites:
#   - EC2 already set up via setup-ec2.sh
#   - DNS A record for bigskylabs.ai pointing to this EC2 IP
#   - Static landing page at /opt/aihealthcare/static/index.html
#
# Idempotent — safe to re-run.
# ---------------------------------------------------------------------------
set -euo pipefail

DOMAIN="bigskylabs.ai"
EMAIL="wgblackmonall@gmail.com"

echo "=== [1/4] Installing Certbot ==="
sudo dnf install -y certbot python3-certbot-nginx

echo "=== [2/4] Writing Nginx config (pre-SSL) ==="
sudo tee /etc/nginx/conf.d/aihealthcare.conf > /dev/null << 'NGXEOF'
server {
    listen 80;
    server_name bigskylabs.ai;

    # Landing page (static files)
    location = / {
        root /opt/aihealthcare/static;
        index index.html;
    }
    location = /index.html {
        root /opt/aihealthcare/static;
    }

    # AIHealthcare Spring Boot app
    location /aihealthcare {
        proxy_pass http://localhost:8080/aihealthcare;
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

# Remove old default config if present
sudo rm -f /etc/nginx/conf.d/default.conf

echo "=== [3/4] Reloading Nginx (HTTP-only for Certbot validation) ==="
sudo nginx -t
sudo systemctl reload nginx

echo "=== [4/4] Obtaining SSL certificate via Certbot ==="
sudo certbot --nginx \
    -d "$DOMAIN" \
    --non-interactive \
    --agree-tos \
    -m "$EMAIL" \
    --redirect

echo ""
echo "=== Verifying auto-renewal ==="
sudo certbot renew --dry-run

echo ""
echo "=========================================="
echo "  SSL setup complete for $DOMAIN"
echo "=========================================="
echo ""
echo "  https://$DOMAIN/              -> Landing page"
echo "  https://$DOMAIN/aihealthcare  -> Spring Boot app"
echo ""
echo "  Certificate auto-renews via certbot timer."
echo "  Change email later: certbot update_account --email new@example.com"
echo ""
