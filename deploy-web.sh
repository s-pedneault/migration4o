#!/bin/bash
# Deploy web/public to Firebase Hosting
#
# Project-specific Firebase auth:
#   Run once to generate a token tied to this project:
#     firebase login:ci
#   Save the printed token to: local/.firebase-token
#   The deploy will then use that token instead of your global login.
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOKEN_FILE="$SCRIPT_DIR/local/.firebase-token"

if [ -f "$TOKEN_FILE" ]; then
    export FIREBASE_TOKEN="$(cat "$TOKEN_FILE")"
fi

cd "$SCRIPT_DIR/web"
firebase deploy --only hosting
