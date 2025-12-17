# Elasticsearch 초기화 스크립트(수동)

자동 생성(B안)이 기본이지만, 운영자가 수동으로 초기화하고 싶을 때 사용합니다.

기본 대상: `http://localhost:9200`

```bash
ES_URL="${ES_URL:-http://localhost:9200}"

curl -sS -X PUT "$ES_URL/works" -H 'Content-Type: application/json' --data-binary @scripts/es-init/works.json
curl -sS -X PUT "$ES_URL/institutions" -H 'Content-Type: application/json' --data-binary @scripts/es-init/institutions.json
curl -sS -X PUT "$ES_URL/keyword_configs" -H 'Content-Type: application/json' --data-binary @scripts/es-init/keyword_configs.json
curl -sS -X PUT "$ES_URL/ingest_jobs" -H 'Content-Type: application/json' --data-binary @scripts/es-init/ingest_jobs.json
curl -sS -X PUT "$ES_URL/maintenance_jobs" -H 'Content-Type: application/json' --data-binary @scripts/es-init/maintenance_jobs.json

curl -sS -X PUT "$ES_URL/keyword_configs/_doc/active_config" -H 'Content-Type: application/json' --data-binary @scripts/es-init/seed-active-config.json
```
