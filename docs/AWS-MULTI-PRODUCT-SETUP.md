# BigSkyLabs.ai — Multi-Product AWS Setup Guide

> **Decision date:** 2026-07-20
> **Architecture:** Subdomain-per-product (Option A)
> **Current state:** AIHealthcare running at `bigskylabs.ai/aihealthcare` (path-based)
> **Target state:** AIHealthcare at `app.bigskylabs.ai`, corporate homepage at `bigskylabs.ai`

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Phase 1 — DNS Records](#2-phase-1--dns-records)
3. [Phase 2 — SSL Certificate (Let's Encrypt)](#3-phase-2--ssl-certificate-lets-encrypt)
4. [Phase 3 — Nginx Subdomain Configuration](#4-phase-3--nginx-subdomain-configuration)
5. [Phase 4 — Spring Boot Context Path Removal](#5-phase-4--spring-boot-context-path-removal)
6. [Phase 5 — SES Multi-Domain Email Setup](#6-phase-5--ses-multi-domain-email-setup)
7. [Phase 6 — Corporate Homepage (S3 + CloudFront)](#7-phase-6--corporate-homepage-s3--cloudfront)
8. [Phase 7 — Future Product Onboarding Checklist](#8-phase-7--future-product-onboarding-checklist)
9. [Rollback Plan](#9-rollback-plan)

---

## 1. Architecture Overview

```
                          ┌─────────────────────────┐
                          │     Route 53 DNS         │
                          └──────┬──────┬───────┬────┘
                                 │      │       │
                    A record     │      │       │  A record
                 bigskylabs.ai   │      │       │  app.bigskylabs.ai
                                 │      │       │
                    ┌────────────▼──┐   │   ┌───▼────────────────┐
                    │  CloudFront   │   │   │  EC2 (100.61.13.237)│
                    │  (corporate)  │   │   │  Nginx → Spring Boot│
                    │  S3 origin    │   │   │  port 443 → 8080    │
                    └───────────────┘   │   └────────────────────┘
                                        │
                              (future products
                               get their own
                               A/CNAME records)
```

**Email domains (SES):**
- `bigskylabs.ai` — corporate: `bill@bigskylabs.ai`
- `aihealthcare.com` (or subdomain) — product: `support@aihealthcare.com`
- Future products get their own verified domains

---

## 2. Phase 1 — DNS Records

### Where to do this
**AWS Console → Route 53 → Hosted zones → bigskylabs.ai**

If `bigskylabs.ai` is registered via Route 53, you already have a hosted zone. If registered
elsewhere (Namecheap, GoDaddy, etc.), either transfer DNS to Route 53 or add these records at
your registrar.

### Records to create

| Type | Name | Value | TTL | Purpose |
|------|------|-------|-----|---------|
| A | `bigskylabs.ai` | (keep existing — CloudFront alias later) | 300 | Corporate homepage |
| A | `app.bigskylabs.ai` | `100.61.13.237` | 300 | AIHealthcare app |

### Step-by-step (Route 53 Console)

1. Open [Route 53 Console](https://console.aws.amazon.com/route53/v2/hostedzones)
2. Click your `bigskylabs.ai` hosted zone
3. Click **"Create record"**
4. Record name: `app` (this creates `app.bigskylabs.ai`)
5. Record type: **A**
6. Value: `100.61.13.237` (your EC2 Elastic IP)
7. TTL: `300`
8. Click **"Create records"**

### Step-by-step (AWS CLI)

```bash
# Create the A record for app.bigskylabs.ai
# First, find your hosted zone ID:
aws route53 list-hosted-zones-by-name --dns-name bigskylabs.ai

# Then create the record (replace Z0123456789 with your zone ID):
aws route53 change-resource-record-sets \
  --hosted-zone-id Z0123456789 \
  --change-batch '{
    "Changes": [{
      "Action": "CREATE",
      "ResourceRecordSet": {
        "Name": "app.bigskylabs.ai",
        "Type": "A",
        "TTL": 300,
        "ResourceRecords": [{"Value": "100.61.13.237"}]
      }
    }]
  }'
```

### Verification

```bash
# Wait 1-2 minutes for DNS propagation, then:
dig app.bigskylabs.ai +short
# Should return: 100.61.13.237

nslookup app.bigskylabs.ai
# Should resolve to your EC2 IP
```

---

## 3. Phase 2 — SSL Certificate (Let's Encrypt)

The existing certificate covers `bigskylabs.ai` only. You need to **expand** it to also
cover `app.bigskylabs.ai`.

### Option A: Expand existing certificate (recommended)

SSH into your EC2 instance and run:

```bash
ssh -i N_VaKeyPair.pem ec2-user@100.61.13.237
```

Then:

```bash
# Expand the certificate to include the subdomain
# Certbot will automatically update the Nginx config
sudo certbot --nginx \
  -d bigskylabs.ai \
  -d app.bigskylabs.ai \
  --non-interactive \
  --agree-tos \
  -m wgblackmonall@gmail.com \
  --redirect \
  --expand

# Verify the certificate covers both domains
sudo certbot certificates
# Should show:
#   Domains: bigskylabs.ai app.bigskylabs.ai
#   Expiry Date: ... (90 days from now)
```

**IMPORTANT:** The `--expand` flag tells Certbot to add `app.bigskylabs.ai` to the
existing `bigskylabs.ai` certificate rather than creating a new one.

### Option B: Separate certificates per domain

Only needed if you plan to move AIHealthcare to a different server later:

```bash
sudo certbot --nginx \
  -d app.bigskylabs.ai \
  --non-interactive \
  --agree-tos \
  -m wgblackmonall@gmail.com \
  --redirect
```

### Verification

```bash
# Test SSL handshake
curl -vI https://app.bigskylabs.ai 2>&1 | grep "subject:"
# Should show: subject: CN=bigskylabs.ai (covers app.bigskylabs.ai as SAN)

# Or use openssl
echo | openssl s_client -servername app.bigskylabs.ai -connect app.bigskylabs.ai:443 2>/dev/null | openssl x509 -noout -text | grep DNS
# Should list: DNS:bigskylabs.ai, DNS:app.bigskylabs.ai
```

---

## 4. Phase 3 — Nginx Subdomain Configuration

Replace the current single-server-block config with separate blocks for the corporate
site and the app subdomain.

### The new config

SSH into EC2, then:

```bash
sudo tee /etc/nginx/conf.d/bigskylabs.conf > /dev/null << 'NGXEOF'
# ---------------------------------------------------------------
# bigskylabs.ai — Corporate homepage (static)
# ---------------------------------------------------------------
server {
    listen 80;
    server_name bigskylabs.ai;

    # Certbot will add SSL directives here on next renewal
    # For now, redirect HTTP → HTTPS if cert already exists

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
```

### Important: migrate the static landing page

```bash
# Create the corporate site directory (separate from /opt/aihealthcare)
sudo mkdir -p /opt/bigskylabs/static
sudo chown -R ec2-user:ec2-user /opt/bigskylabs

# Move (or copy) the existing landing page
cp /opt/aihealthcare/static/index.html /opt/bigskylabs/static/index.html
```

### Remove old config and reload

```bash
# Remove the old path-based config
sudo rm -f /etc/nginx/conf.d/aihealthcare.conf

# Test the new config
sudo nginx -t

# Reload
sudo systemctl reload nginx
```

### Re-run Certbot to wire SSL into the new server blocks

```bash
sudo certbot --nginx \
  -d bigskylabs.ai \
  -d app.bigskylabs.ai \
  --non-interactive \
  --agree-tos \
  -m wgblackmonall@gmail.com \
  --redirect \
  --expand
```

Certbot will detect the two server blocks, add `listen 443 ssl` directives, and add
HTTP→HTTPS redirects automatically.

### Verification

```bash
# Corporate homepage
curl -sI https://bigskylabs.ai | head -5
# Should return 200 OK, serving index.html

# AIHealthcare app (no /aihealthcare prefix!)
curl -sI https://app.bigskylabs.ai/dashboard | head -5
# Should return 200 OK (or 302 to login)

# Old URL should 404 (no longer routed)
curl -sI https://bigskylabs.ai/aihealthcare | head -5
# Should return 404
```

---

## 5. Phase 4 — Spring Boot Context Path Removal

The app currently runs under `/aihealthcare` context path. With subdomain routing, it
runs at the root `/`.

### Changes to `application-aws.yml`

```yaml
# BEFORE (path-based):
aihealthcare:
  base-url: https://bigskylabs.ai/aihealthcare

server:
  servlet:
    context-path: /aihealthcare

# AFTER (subdomain):
aihealthcare:
  base-url: https://app.bigskylabs.ai

# Remove the server.servlet.context-path entirely
# (or set to /)
```

### What this affects

| Area | Before | After | Action needed? |
|------|--------|-------|---------------|
| All Thymeleaf `th:href` | Relative (e.g. `/dashboard`) | Same | No — Spring resolves relative to context path automatically |
| `th:action` on forms | Relative | Same | No |
| JavaScript `fetch()` calls | Check for hardcoded `/aihealthcare` prefix | Remove prefix | **Yes — grep and fix** |
| Email links (newsletter) | Uses `aihealthcare.base-url` | Picks up new value | No — already templated |
| Stripe webhook URL | Must update in Stripe Dashboard | `https://app.bigskylabs.ai/api/v1/stripe/webhook` | **Yes — update in Stripe** |
| API key consumers | Any external callers using the old URL | New base URL | **Yes — notify if any** |

### Check for hardcoded paths

```bash
# On your local machine, from project root:
grep -rn "/aihealthcare" application/src/main/resources/templates/ --include="*.html"
grep -rn "/aihealthcare" application/src/main/java/ --include="*.java"
grep -rn "bigskylabs.ai/aihealthcare" application/src/ --include="*.yml" --include="*.yaml" --include="*.properties"
```

Fix any hardcoded references before deploying.

### Deploy the change

```bash
# Rebuild and deploy
./deploy/deploy.sh 100.61.13.237
```

---

## 6. Phase 5 — SES Multi-Domain Email Setup

### Understanding SES identity types

SES has two identity levels:
1. **Email address identity** — verifies a single address (e.g., `wgblackmonall@gmail.com`)
2. **Domain identity** — verifies an entire domain (e.g., `aihealthcare.com`), allowing you to send from ANY address at that domain

You want **domain identity** for each product.

### Current state

Your account currently has `wgblackmonall@gmail.com` verified as an email identity.
This is fine for testing but not for branded product emails.

### Step 1: Verify the product email domain

**Prerequisite:** You must own the domain you want to send from. If you want
`support@aihealthcare.com`, you need to own `aihealthcare.com`. Alternatively, you can
use a subdomain of `bigskylabs.ai` like `mail.aihealthcare.bigskylabs.ai`.

**Option A: Use a dedicated product domain (e.g., `aihealthcare.com`)**

If you purchase `aihealthcare.com`:

```
AWS Console → SES → Verified identities → Create identity
```

1. Identity type: **Domain**
2. Domain: `aihealthcare.com`
3. Click **"Create identity"**

**Option B: Use a subdomain of bigskylabs.ai (free, no purchase needed)**

```
AWS Console → SES → Verified identities → Create identity
```

1. Identity type: **Domain**
2. Domain: `newsletter.bigskylabs.ai` (or `aihealthcare.bigskylabs.ai`)
3. Click **"Create identity"**

This lets you send from `support@newsletter.bigskylabs.ai`, `noreply@newsletter.bigskylabs.ai`, etc.

### Step 2: Add DNS records for DKIM authentication

After creating the identity, SES shows you **3 CNAME records** for DKIM. These look like:

| Type | Name | Value |
|------|------|-------|
| CNAME | `abc123._domainkey.aihealthcare.com` | `abc123.dkim.amazonses.com` |
| CNAME | `def456._domainkey.aihealthcare.com` | `def456.dkim.amazonses.com` |
| CNAME | `ghi789._domainkey.aihealthcare.com` | `ghi789.dkim.amazonses.com` |

**Add these in Route 53:**

```
AWS Console → Route 53 → Hosted zones → aihealthcare.com (or bigskylabs.ai) → Create record
```

For each of the 3 CNAME records:
1. Record type: **CNAME**
2. Record name: paste the name (e.g., `abc123._domainkey`)
3. Value: paste the value (e.g., `abc123.dkim.amazonses.com`)
4. TTL: `300`
5. Click **"Create records"**

**AWS CLI alternative:**

```bash
# Replace values with actual DKIM tokens from SES console
ZONE_ID="Z0123456789"  # Your Route 53 hosted zone ID
DOMAIN="aihealthcare.com"

aws route53 change-resource-record-sets \
  --hosted-zone-id $ZONE_ID \
  --change-batch '{
    "Changes": [
      {
        "Action": "CREATE",
        "ResourceRecordSet": {
          "Name": "TOKEN1._domainkey.'$DOMAIN'",
          "Type": "CNAME",
          "TTL": 300,
          "ResourceRecords": [{"Value": "TOKEN1.dkim.amazonses.com"}]
        }
      },
      {
        "Action": "CREATE",
        "ResourceRecordSet": {
          "Name": "TOKEN2._domainkey.'$DOMAIN'",
          "Type": "CNAME",
          "TTL": 300,
          "ResourceRecords": [{"Value": "TOKEN2.dkim.amazonses.com"}]
        }
      },
      {
        "Action": "CREATE",
        "ResourceRecordSet": {
          "Name": "TOKEN3._domainkey.'$DOMAIN'",
          "Type": "CNAME",
          "TTL": 300,
          "ResourceRecords": [{"Value": "TOKEN3.dkim.amazonses.com"}]
        }
      }
    ]
  }'
```

### Step 3: Wait for verification (5 min – 72 hours)

```bash
# Check status via CLI
aws ses get-identity-verification-attributes \
  --identities aihealthcare.com \
  --region us-east-1

# Look for: "VerificationStatus": "Success"
```

Or check the SES console — the identity status will change from "Pending" to "Verified".
Usually takes 5-15 minutes if DNS is on Route 53.

### Step 4: (Optional) Add custom MAIL FROM domain

This makes the `Return-Path` header match your domain instead of showing `amazonses.com`:

```
SES Console → Verified identities → aihealthcare.com → Custom MAIL FROM domain
```

1. MAIL FROM domain: `mail.aihealthcare.com`
2. MX fallback: `Reject` (strict) or `UseDefaultValue` (lenient)
3. Add the MX and TXT records SES shows you to Route 53:

| Type | Name | Value | Priority |
|------|------|-------|----------|
| MX | `mail.aihealthcare.com` | `feedback-smtp.us-east-1.amazonses.com` | 10 |
| TXT | `mail.aihealthcare.com` | `"v=spf1 include:amazonses.com ~all"` | — |

### Step 5: Request production access (if still in sandbox)

New SES accounts are in **sandbox mode** — you can only send to verified email addresses.
To send to real subscribers:

```
SES Console → Account dashboard → "Request production access"
```

Fill out:
- **Mail type:** Transactional (newsletters are technically transactional for opted-in subscribers)
- **Website URL:** `https://app.bigskylabs.ai`
- **Use case description:** "We send a weekly AI healthcare newsletter to opted-in subscribers. Subscribers sign up via our website and can unsubscribe at any time. We do not send unsolicited email."
- **Daily sending volume:** Start with 1,000
- **Contact email:** `wgblackmonall@gmail.com`

AWS typically approves within 24 hours.

### Step 6: Update Spring Boot config

In `application-aws.yml`:

```yaml
aihealthcare:
  newsletter:
    from-address: newsletter@aihealthcare.com   # or newsletter@newsletter.bigskylabs.ai
```

The SMTP credentials (`SES_USERNAME` / `SES_PASSWORD`) stay the same — they're
IAM-scoped to your AWS account, not to a specific domain.

### Step 7: Per-product email addresses

Once the domain is verified, you can send from **any** address at that domain without
additional verification:

- `support@aihealthcare.com` — customer support
- `newsletter@aihealthcare.com` — newsletter delivery
- `noreply@aihealthcare.com` — transactional emails

To **receive** email at these addresses, you need SES **receiving rules** or a separate
email provider (Google Workspace, Zoho Mail, etc.). SES sending verification alone does
NOT set up inbound email.

### Receiving email (optional — adds complexity)

**Option A: SES Receiving (complex, cheap)**
```
SES Console → Email receiving → Create rule set → Create rule
```
- Stores inbound email in S3 or triggers a Lambda function
- Only works in us-east-1, us-west-2, or eu-west-1
- Requires MX record pointing to SES

**Option B: Google Workspace ($7/user/month) or Zoho Mail (free for 1 domain)**
- Full inbox with webmail, calendar, contacts
- Add MX records pointing to Google/Zoho instead of SES
- You can still **send** via SES while **receiving** via Google/Zoho

**Recommendation:** Use Google Workspace or Zoho for receiving. Use SES for bulk sending
(newsletters, transactional). They coexist fine — different MX vs DKIM/SPF records.

---

## 7. Phase 6 — Corporate Homepage (S3 + CloudFront)

Once AIHealthcare moves to `app.bigskylabs.ai`, the corporate homepage at `bigskylabs.ai`
should be a separate static site. Two approaches:

### Option A: Keep it on EC2 Nginx (simplest for now)

The current `/opt/bigskylabs/static/index.html` served by Nginx works fine while you
only have one product. Zero additional AWS cost.

**When to move to S3 + CloudFront:** When you add a second product or want the corporate
site on a separate server from any product.

### Option B: S3 + CloudFront (fully decoupled)

#### Step 1: Create S3 bucket

```bash
aws s3 mb s3://bigskylabs-corporate-site --region us-east-1
```

#### Step 2: Upload site files

```bash
aws s3 sync /path/to/corporate-site/ s3://bigskylabs-corporate-site/ \
  --exclude ".git/*"
```

#### Step 3: Create CloudFront distribution

```
AWS Console → CloudFront → Create distribution
```

- **Origin domain:** `bigskylabs-corporate-site.s3.us-east-1.amazonaws.com`
- **Origin access:** Origin Access Control (OAC) — don't make the bucket public
- **Viewer protocol policy:** Redirect HTTP to HTTPS
- **Alternate domain name (CNAME):** `bigskylabs.ai`
- **Custom SSL certificate:** Request one via ACM (see below)
- **Default root object:** `index.html`

#### Step 4: Request ACM certificate for CloudFront

**IMPORTANT:** CloudFront certificates MUST be in `us-east-1` regardless of your region.

```
AWS Console → Certificate Manager → us-east-1 region → Request certificate
```

1. Certificate type: **Public**
2. Domain name: `bigskylabs.ai`
3. Validation method: **DNS validation**
4. Click **"Request"**
5. Click into the certificate → click **"Create records in Route 53"** (one click if using Route 53)
6. Wait 5-15 minutes for validation

Then go back to CloudFront and select this certificate.

#### Step 5: Update Route 53 to point to CloudFront

Change the `bigskylabs.ai` A record from pointing at EC2 to an **Alias** to CloudFront:

```
Route 53 → bigskylabs.ai hosted zone → Edit the A record for bigskylabs.ai
```

1. Toggle **Alias** ON
2. Route traffic to: **Alias to CloudFront distribution**
3. Choose your distribution from the dropdown
4. Save

#### Step 6: Remove `bigskylabs.ai` from EC2 Nginx

Update the Nginx config to only handle `app.bigskylabs.ai`:

```bash
# Remove the bigskylabs.ai server block from /etc/nginx/conf.d/bigskylabs.conf
# Keep only the app.bigskylabs.ai block
sudo nginx -t && sudo systemctl reload nginx
```

---

## 8. Phase 7 — Future Product Onboarding Checklist

When adding a new product (e.g., "AI Finance"):

| # | Task | Where |
|---|------|-------|
| 1 | Register domain (e.g., `aifinance.com`) or pick subdomain (`aifinance.bigskylabs.ai`) | Route 53 / registrar |
| 2 | Create DNS A/CNAME record pointing to product's server | Route 53 |
| 3 | Get SSL certificate (Certbot on EC2 or ACM for CloudFront) | EC2 / ACM |
| 4 | Add Nginx server block for the new subdomain | EC2 |
| 5 | Verify email domain in SES (3 DKIM CNAMEs) | SES + Route 53 |
| 6 | (Optional) Custom MAIL FROM domain (MX + TXT records) | SES + Route 53 |
| 7 | Update corporate homepage to link to new product | S3 or Nginx static |
| 8 | Configure product's `application.yml` with new `from-address` and `base-url` | Product code |

---

## 9. Rollback Plan

If something goes wrong during migration:

### Revert Nginx to path-based

```bash
# Restore the old config
sudo cp /etc/nginx/conf.d/aihealthcare.conf.bak /etc/nginx/conf.d/aihealthcare.conf
sudo rm /etc/nginx/conf.d/bigskylabs.conf
sudo nginx -t && sudo systemctl reload nginx
```

### Revert Spring Boot context path

Restore `application-aws.yml` to include:

```yaml
server:
  servlet:
    context-path: /aihealthcare

aihealthcare:
  base-url: https://bigskylabs.ai/aihealthcare
```

Redeploy with `./deploy/deploy.sh`.

### DNS rollback

DNS changes propagate within TTL (300 seconds = 5 minutes). Delete the `app.bigskylabs.ai`
A record in Route 53 to revert.

---

## Execution Order (recommended)

Do these in order, verifying each step before proceeding:

1. **Create DNS record** for `app.bigskylabs.ai` (Phase 1) — verify with `dig`
2. **Expand SSL cert** to cover `app.bigskylabs.ai` (Phase 2) — verify with `curl -vI`
3. **Back up current Nginx config** — `sudo cp /etc/nginx/conf.d/aihealthcare.conf /etc/nginx/conf.d/aihealthcare.conf.bak`
4. **Deploy new Nginx config** with subdomain routing (Phase 3) — verify both URLs
5. **Update `application-aws.yml`** to remove context path (Phase 4) — redeploy
6. **Verify end-to-end** — all pages, forms, API endpoints, Stripe webhook
7. **Set up SES domain** when ready for branded email (Phase 5)
8. **Move corporate homepage to S3/CloudFront** when adding second product (Phase 6)

**Total estimated downtime:** ~30 seconds (Nginx reload + Spring Boot restart)
