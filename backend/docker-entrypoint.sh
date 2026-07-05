#!/bin/sh
set -e

if [ -n "$FCM_SERVICE_ACCOUNT_JSON_BASE64" ]; then
  echo "$FCM_SERVICE_ACCOUNT_JSON_BASE64" | base64 -d > /app/firebase-service-account.json
  export FCM_SERVICE_ACCOUNT_PATH=/app/firebase-service-account.json
fi

exec node dist/app.js
