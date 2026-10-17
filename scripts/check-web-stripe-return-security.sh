#!/usr/bin/env bash
set -euo pipefail

html="app/webApp/src/webMain/resources/index.html"
adapter="app/shared/src/jsMain/kotlin/com/kenlikdev/qmarket/payment/StripeClientConfirmation.js.kt"

if [[ ! -f "$html" || ! -f "$adapter" ]]; then
    echo "Stripe return security check: required source file is missing." >&2
    exit 1
fi

line_of() {
    local pattern="$1"
    local file="$2"
    local line
    line="$(grep -nF "$pattern" "$file" | head -n 1 | cut -d: -f1 || true)"
    if [[ -z "$line" ]]; then
        echo "Stripe return security check: missing '$pattern' in $file" >&2
        exit 1
    fi
    printf '%s' "$line"
}

referrer_line="$(line_of '<meta name="referrer" content="no-referrer">' "$html")"
scrub_line="$(line_of 'params.delete("payment_intent_client_secret")' "$html")"
stylesheet_line="$(line_of 'href="styles.css"' "$html")"
application_line="$(line_of 'src="webApp.js"' "$html")"

if ! (( referrer_line < scrub_line &&
         scrub_line < stylesheet_line &&
         scrub_line < application_line )); then
    echo "Stripe return security check: referrer policy and secret redaction must run before external assets." >&2
    exit 1
fi

line_of 'qmarketStripeReturn' "$html" >/dev/null
line_of 'params.has("qmarketStripeReturn")' "$adapter" >/dev/null
line_of 'params.delete("qmarketStripeReturn")' "$adapter" >/dev/null
line_of 'history.replaceState' "$html" >/dev/null

echo "Stripe return security check passed: the client secret is scrubbed before app assets load."
