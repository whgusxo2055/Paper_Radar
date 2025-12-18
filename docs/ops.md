# 운영/마이그레이션 가이드 (Paper_Radar)

## 1) 사전 준비
- Java 21 + Gradle Wrapper 사용
- Docker / Docker Compose 설치
- (권장) `OPENALEX_EMAIL` 설정(Polite pool)

## 2) JAR 빌드 (Compose 실행 전 필수)
Docker Compose는 **로컬에서 빌드된 JAR**을 컨테이너에 포함해 실행합니다.

```bash
./gradlew clean bootJar
```

빌드 산출물: `build/libs/*.jar`

## 3) Docker Compose로 실행
Elasticsearch는 이름 지정 볼륨(`paperradar-esdata`)에 데이터가 저장됩니다.

```bash
docker compose up -d --build
```

- App: `http://localhost:80`
- Elasticsearch: `http://localhost:9200`

### 환경 변수 예시
`.env` 파일 또는 쉘 환경변수로 설정합니다. 예시는 `.envexample` 참고.

```bash
APP_TIMEZONE=Asia/Seoul
INGEST_SCHEDULE_CRON=0 0 3 * * *
INGEST_LOOKBACK_YEARS=5
OPENALEX_EMAIL=you@example.com
PAPERRADAR_ENRICH_CROSSREF_ENABLED=false
```

## 4) 인덱스 초기화/매핑 변경 주의
앱 기동 시 기본 인덱스를 자동 생성합니다(없을 때만).

### 4.1 자동 생성의 한계
- 기존 인덱스의 **매핑/세팅은 자동으로 갱신되지 않습니다.**
- ES는 문자열 필드를 동적으로 추가할 수는 있지만(dynamic mapping), 의도한 타입(예: `keyword`)과 다르게 생성될 수 있습니다.
  - 예: `best_link_url`을 `keyword`로 강제하고 싶은데, 기존 인덱스에 동적으로 들어가면 `text`로 생성될 수 있음

### 4.2 개발/테스트에서 “완전 초기화”
데이터를 지워도 되는 환경에서만 사용합니다.

```bash
docker compose down -v
docker compose up -d --build
```

`-v`는 `paperradar-esdata` 볼륨까지 삭제합니다.

### 4.3 운영에서 “필드 추가/매핑 변경” 반영(권장: Reindex + alias)
앱은 `works` 같은 **고정 인덱스 이름**을 사용하지만, ES에서는 인덱스 이름 대신 **alias**도 사용할 수 있습니다.
운영에서 안전하게 매핑을 바꾸려면 아래처럼 새 인덱스를 만든 뒤 alias를 교체합니다.

예: `works` 매핑 변경 반영

1) 새 인덱스 생성(매핑 적용)
```bash
ES_URL=${ES_URL:-http://localhost:9200}
curl -sS -X PUT "$ES_URL/works_v2" -H 'Content-Type: application/json' --data-binary @scripts/es-init/works.json
```

2) 데이터 복사(reindex)
```bash
curl -sS -X POST "$ES_URL/_reindex?wait_for_completion=true" -H 'Content-Type: application/json' -d '{
  "source": { "index": "works" },
  "dest":   { "index": "works_v2" }
}'
```

3) 기존 `works`를 alias로 전환(다운타임 최소화)
```bash
curl -sS -X POST "$ES_URL/_aliases" -H 'Content-Type: application/json' -d '{
  "actions": [
    { "remove_index": { "index": "works" } },
    { "add": { "index": "works_v2", "alias": "works" } }
  ]
}'
```

주의:
- `remove_index`는 실제 인덱스를 삭제합니다. 운영에서 삭제 전 백업/스냅샷 정책을 확인하세요.
- 동일 방식으로 `institutions`, `keyword_configs`, `ingest_jobs`, `maintenance_jobs`도 적용 가능합니다.

## 5) 수집 실행/확인
- 관리 화면: `/admin/ingest`
  - Incremental / Full 수동 실행
  - 최근 `ingest_jobs` 상태 확인

## 6) 원문 링크 보강(옵션)
Crossref 보강은 기본 비활성입니다.

- 활성화:
  ```bash
  PAPERRADAR_ENRICH_CROSSREF_ENABLED=true
  ```

## 7) 데이터 마이그레이션(백필)
### 7.1 best_link 재계산(기존 works 문서)
works 문서에 `best_link_url/type`가 비어있거나 정책 변경 후 갱신이 필요할 때 사용합니다.

- 관리 화면: `/admin/maintenance`
  - Batch size / Max docs 설정 후 실행
  - 결과는 `maintenance_jobs`에 요약 저장됩니다(실패 문서 ID는 최대 100개 저장).
  - 상세 에러/스택트레이스는 애플리케이션 로그에서 확인합니다(ES에는 저장하지 않음).

권장 파라미터(예시):
- 소규모 검증: `batch=200`, `maxDocs=1000`
- 운영 점진 적용: `batch=200`, `maxDocs=5000`씩 반복(모니터링하면서)

## 8) 트러블슈팅
- 검색/트렌드가 비어있음: 먼저 `/admin/ingest`로 수집 실행
- 인덱스가 없거나 깨짐(개발/테스트): `docker compose down -v`로 초기화 후 재기동
- 외부 API 호출 실패(OpenAlex/Crossref): 네트워크/방화벽 설정 및 `OPENALEX_EMAIL` 확인
- 운영에서 매핑/필드가 반영되지 않음: 4.3의 reindex + alias 절차로 반영
