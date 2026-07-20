#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# setup-ses-domain.sh — Verify a domain in Amazon SES for sending email
#
# Creates an SES domain identity with DKIM signing, then prints the DNS
# records you need to add to Route 53 (or your registrar).
#
# Usage:
#   ./deploy/setup-ses-domain.sh aihealthcare.com
#   ./deploy/setup-ses-domain.sh newsletter.bigskylabs.ai
#
# Prerequisites:
#   - AWS CLI configured with credentials that have SES permissions
#   - The domain is registered and you control its DNS
#
# After running:
#   1. Add the DKIM CNAME records to Route 53 (printed by this script)
#   2. Wait 5-15 min for verification (check SES console)
#   3. Update application-aws.yml with the new from-address
# ---------------------------------------------------------------------------
set -euo pipefail

REGION="us-east-1"

if [ $# -lt 1 ]; then
    echo "Usage: $0 <domain>"
    echo "  Example: $0 aihealthcare.com"
    echo "  Example: $0 newsletter.bigskylabs.ai"
    exit 1
fi

DOMAIN="$1"

echo "=== [1/4] Creating SES domain identity: $DOMAIN ==="
# SES v2 API — creates identity and enables DKIM signing
aws sesv2 create-email-identity \
    --email-identity "$DOMAIN" \
    --region "$REGION" \
    2>/dev/null || echo "(Identity may already exist — continuing)"

echo "=== [2/4] Retrieving DKIM tokens ==="
DKIM_OUTPUT=$(aws sesv2 get-email-identity \
    --email-identity "$DOMAIN" \
    --region "$REGION" \
    --query 'DkimAttributes.Tokens' \
    --output text)

if [ -z "$DKIM_OUTPUT" ] || [ "$DKIM_OUTPUT" = "None" ]; then
    echo "ERROR: Could not retrieve DKIM tokens. Check SES console."
    exit 1
fi

echo ""
echo "=========================================="
echo "  DNS Records to Add for $DOMAIN"
echo "=========================================="
echo ""
echo "Add these 3 CNAME records to your DNS (Route 53 or registrar):"
echo ""

TOKEN_NUM=1
for TOKEN in $DKIM_OUTPUT; do
    echo "  Record $TOKEN_NUM:"
    echo "    Type:  CNAME"
    echo "    Name:  ${TOKEN}._domainkey.${DOMAIN}"
    echo "    Value: ${TOKEN}.dkim.amazonses.com"
    echo ""
    TOKEN_NUM=$((TOKEN_NUM + 1))
done

echo "=== [3/4] Checking verification status ==="
VERIFICATION=$(aws sesv2 get-email-identity \
    --email-identity "$DOMAIN" \
    --region "$REGION" \
    --query 'DkimAttributes.Status' \
    --output text)

echo "  Current DKIM status: $VERIFICATION"
if [ "$VERIFICATION" = "SUCCESS" ]; then
    echo "  Domain is already verified!"
elif [ "$VERIFICATION" = "PENDING" ]; then
    echo "  Domain is pending — add the DNS records above, then wait 5-15 minutes."
else
    echo "  Status: $VERIFICATION — check SES console for details."
fi

echo ""
echo "=== [4/4] Adding SPF record (optional but recommended) ==="
echo ""
echo "Also add this TXT record to your DNS for SPF alignment:"
echo ""
echo "  Type:  TXT"
echo "  Name:  $DOMAIN"
echo "  Value: \"v=spf1 include:amazonses.com ~all\""
echo ""
echo "=========================================="
echo "  Next Steps"
echo "=========================================="
echo ""
echo "  1. Add the 3 CNAME + 1 TXT records above to Route 53"
echo "  2. Wait for verification:  aws sesv2 get-email-identity --email-identity $DOMAIN --region $REGION --query 'DkimAttributes.Status'"
echo "  3. Once verified, update application-aws.yml:"
echo "       aihealthcare:"
echo "         newsletter:"
echo "           from-address: newsletter@$DOMAIN"
echo ""
echo "  4. (Optional) Set up custom MAIL FROM domain in SES console"
echo "  5. (Optional) Request production access if still in SES sandbox"
echo ""
echo "  Available sender addresses (no additional verification needed):"
echo "    support@$DOMAIN"
echo "    newsletter@$DOMAIN"
echo "    noreply@$DOMAIN"
echo "    admin@$DOMAIN"
echo "    help@$DOMAIN"
echo ""
