#!/usr/bin/env bash
set -euo pipefail

# App 이미지를 "클린 빌드(캐시 무시)"로 재생성한 뒤 재기동합니다.
# - Elasticsearch 데이터 볼륨은 유지됩니다.

docker compose build --no-cache app
docker compose up -d --force-recreate app

