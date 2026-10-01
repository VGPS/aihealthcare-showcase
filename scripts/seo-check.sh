#!/usr/bin/env bash
# SEO regression check — run after each deploy.
# From docs/BigSkyLabs SEO Fixes — Claude Code Handoff.md, Section 4.
set -u
B=${1:-https://app.bigskylabs.ai}
pass=0; fail=0
check(){ if eval "$2"; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1"; fail=$((fail+1)); fi; }
code(){ curl -s -o /dev/null -w "%{http_code}" "$B$1"; }

check "404 directory"   '[ "$(code /directory/nope-xyz)" = 404 ]'
check "404 wiki"        '[ "$(code /wiki/nope-xyz)" = 404 ]'
check "404 legislation" '[ "$(code /legislation/nope-xyz)" = 404 ]'
check "https redirects" 'curl -sI $B/dashboard | grep -i "^location" | grep -q "https://"'
check "page2 canonical" 'curl -s "$B/wiki?page=2" | grep -q "rel=\"canonical\" href=\"$B/wiki?page=2\""'
check "filter noindex"  'curl -s "$B/wiki?query=test" | grep -qi "name=\"robots\" content=\"noindex"'
check "insights hub"    '[ "$(code /insights/)" = 200 ]'
check "sitemap index"   'curl -s $B/sitemap.xml | grep -q "<sitemapindex"'
check "legis title"     '! curl -s $B/legislation/az-hb-2175 | grep -q "<title>Legislation Detail"'
check "home title"      '! curl -s $B/ | grep -q "AIHealthcare — AI Healthcare Intelligence Platform — AIHealthcare"'
check "json-ld home"    'curl -s $B/ | grep -q "application/ld+json"'
check "wiki not thin"   '[ $(curl -s $B/wiki/ai-healthcare-roi | sed -e "s/<[^>]*>//g" | tr -s " \n" | wc -c) -gt 2500 ]'
check "og image card"   'curl -s $B/ | grep -q "og-default.png"'
check "twitter large"   'curl -s $B/ | grep -q "summary_large_image"'
echo "pass=$pass fail=$fail"; [ $fail -eq 0 ]
