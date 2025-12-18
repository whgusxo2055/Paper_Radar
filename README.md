# PAPER_RADAR

내부용 논문·기술 트렌드 레이더 (Spring MVC + Thymeleaf + Elasticsearch).

## 주요 기능(현 상태)
- 논문 검색: `/search` (쿼리/필터/정렬/페이지네이션)
- 자동완성: `/api/suggest/*` (keyword/institution/author)
- 트렌드: 메인(`/`)에서 키워드/기관 Top 20 표시
- 관리: 키워드/기관 활성화, 수집 실행/로그, maintenance 백필 실행
- 논문 상세: `/work/{encodedWorkId}` (외부 원문 링크 정책 적용)
- 기관 분석: `/institution/{encodedInstitutionId}` (Top Keywords 30/90일 토글)

## 빠른 시작(권장: Docker Compose)
1) 실행
```bash
docker compose up -d --build
```

- (선택) 캐시를 무시하고 “클린 빌드”로 재배포하고 싶다면
```bash
./scripts/compose-up-clean.sh
```

- App: `http://localhost:8080`
- Elasticsearch: `http://localhost:9200`

환경 변수 예시는 `.envexample`을 참고하고, `.env`로 복사해 사용합니다.

## 운영/마이그레이션 문서
- 운영 가이드: `docs/ops.md`
  - 인덱스/매핑 변경(reindex + alias) 절차
  - 수집 실행(`/admin/ingest`) 및 상태 확인
  - `best_link_url/type` 재계산(`/admin/maintenance`) 가이드

## 프로젝트 구조
- `src/main/java/com/paperradar/`: 애플리케이션 코드
- `src/main/resources/templates/`: Thymeleaf 템플릿
- `src/main/resources/static/`: 정적 리소스(CSS/JS)
- `src/test/java/`: 테스트
- `scripts/es-init/`: ES 수동 초기화 스크립트(옵션)

## 개발 명령어
- 테스트: `./gradlew test`
- 로컬 실행(IDE/호스트): `./gradlew bootRun` (필요 시 `ELASTICSEARCH_URL=http://localhost:9200`)

## 참고 문서
- 요구사항: `SRS.md`
- 개발 계획: `Plan.md`
- 협업 규칙: `AGENTS.md`
