#!/bin/bash
# Deploy web/public to Firebase Hosting
set -e
cd "$(dirname "$0")/web"
firebase deploy --only hosting
