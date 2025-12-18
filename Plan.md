# PAPER_RADAR 개발 계획 (Plan.md) - v1 상세 확정본

프로젝트명  
PAPER_RADAR

기준 문서  
SRS v1 Final (확정)

기술 스택 확정  
Java 21 (확정)  
Spring Boot + Spring MVC + Thymeleaf (확정)  
Elasticsearch 7.17.x 단일 노드 (확정)  
Lombok 사용 (확정)  
Spring Validation 사용 (확정)  
배포는 Docker Compose (권장사항을 v1 범위에서 적용)

운영 전제  
내부 전용, 동시 접속자 ≤ 5  
로그인/HTTPS 없음  
외부 접근은 네트워크/방화벽/IP allowlist로 통제

---

## 0. 완료 정의(Done Definition)

기능  
검색(/search) 동작  
자동완성 3종(/api/suggest/keyword, institution, author) 동작  
관리(키워드 후보 승인/비활성, 기관 추가/비활성, 수집 수동 실행, 수집 로그 조회) 동작  
트렌드(키워드/기관) Top N 표시 및 검색 페이지로 연결  
하루 1회 스케줄 수집 동작(증분 기본)  
초기 3년 수집은 “운영자가 수동 실행(full 모드)”로 수행 가능

품질  
ES 문서 중복 없음(upsert)  
검색/트렌드 집계는 내부 환경에서 체감 지연 없이 동작(검색 p95 1초 목표, 트렌드 p95 2초 목표)  
예외 발생 시 사용자 화면이 깨지지 않고 오류 메시지 또는 빈 상태를 표시  
로그에 원인 기록

문서  
SRS.md, Plan.md 최신 유지  
환경 변수/실행 방법 README 수준 안내(간단)

---

## 1. 개발 전 단계(Preparation)

### 1.1 리포지토리/브랜치 전략(확정)
main: 릴리스 가능한 상태 유지  
dev: 통합 개발 브랜치  
feature/*: 기능 단위 브랜치(검색, 자동완성, 트렌드, 수집 등)

### 1.2 프로젝트 생성(확정)
Spring Boot(Java 21) 기반으로 생성  
패키지 기본: `com.paperradar`

### 1.3 의존성 확정(Gradle 기준 예시)
필수  
spring-boot-starter-web  
spring-boot-starter-thymeleaf  
spring-boot-starter-validation (Validation 확정)  
elasticsearch-java  
jackson-databind  
lombok (compileOnly + annotationProcessor)

권장  
spring-boot-starter-actuator (내부 관측)  
logback + JSON 로깅(선택, 내부면 기본도 가능)

### 1.4 환경 변수(.env) 확정
ELASTICSEARCH_URL=http://elasticsearch:9200  
APP_TIMEZONE=Asia/Seoul  
INGEST_SCHEDULE_CRON=0 0 3 * * * (매일 03:00 예시, 필요 시 변경)  
OPENALEX_EMAIL=(권장, polite pool/연락용)  
INGEST_LOOKBACK_YEARS=3 (확정)

---

## 2. 개발 단계(Implementation Phases)

각 단계는 “완료 기준”을 만족해야 다음 단계로 진행  
각 단계 완료 시 dev 브랜치로 merge

---

## 2.1 1단계: 프로젝트 뼈대 구축(서버 렌더링 확인)

목표  
서버 기동  
Thymeleaf 레이아웃/기본 페이지 렌더링

작업  
Spring Boot 기본 설정(Java 21)  
패키지 구조 생성  
컨트롤러 4개(페이지): `/`, `/search`, `/admin`, `/admin/keywords` 최소 라우팅  
templates/layout.html + index.html + search.html + admin/keywords.html 스캐폴딩  
정적 리소스(js/css) 폴더 구성

완료 기준  
`/` 접속 시 index 화면 렌더링  
`/search` 접속 시 search 화면 렌더링  
`/admin/keywords` 접속 시 admin 화면 렌더링  
서버 로그에 에러 없음

---

## 2.2 2단계: Elasticsearch 7.17.x 인프라 구성

목표  
Docker Compose로 ES 7.17.x 기동  
필수 인덱스 생성 및 매핑 반영

작업  
docker-compose.yml에 Elasticsearch 7.17.x 단일 노드 구성  
인덱스 생성 스크립트 준비  
works / institutions / keyword_configs / ingest_jobs 생성  
keyword_configs 초기 문서(active_config) 1건 생성

인덱스 생성 방식 확정(권장)  
운영 편의상 두 가지 중 1개 선택해 진행  
A) `scripts/es-init/*.json` 스크립트를 제공하고 수동 실행  
B) 앱 기동 시 InitRunner가 존재 확인 후 생성(내부 서비스라 편함)

v1 권장: B로 진행(자동 생성)  
단, “운영자가 원하면 수동 스크립트로도 실행 가능”하게 scripts도 같이 둠

완료 기준  
Compose로 ES 7.17.x 기동  
인덱스 4개 생성 확인  
keyword_configs/active_config 문서 존재 확인

---

## 2.3 3단계: 검색 기능 구현(E2E)

목표  
검색 페이지에서 쿼리/필터/정렬/페이지네이션 동작

작업  
DTO 생성(Validation 적용)  
SearchRequest  
q, from, to, keyword, instId, author, sort, page, size  
Validation 예시  
page >= 1  
size 1~50  
sort는 허용값만  
from/to 형식 검증(가능하면 LocalDate로 바인딩)

SearchService 구현  
QueryFactory에서 ES DSL 생성(operator=and 확정)  
빈 q면 filter-only 모드  
정렬(relevance/newest/cited) 확정  
ResponseMapper로 hits -> SearchResult 변환

SearchController 구현  
`GET /search`에서 SearchRequest 바인딩  
SearchResult를 Model에 담아 search.html 렌더링

완료 기준  
q만으로 검색 가능  
기간 필터(from/to) 동작  
기관/키워드/저자 필터 동작  
정렬 옵션 3개 동작  
페이지네이션 동작  
잘못된 파라미터는 400 대신 “입력 오류 안내”로 처리(내부 서비스라도 UX 유지)

---

## 2.4 4단계: 자동완성(Ajax) 구현

목표  
검색창 자동완성(키워드/기관/저자) 동작

작업  
SuggestApiController 구현  
GET /api/suggest/keyword (A안: works.keyword_candidates terms agg + include prefix regex)  
GET /api/suggest/institution (institutions 검색, display_name.ac 중심)  
GET /api/suggest/author (works nested authors.name.keyword terms agg + include)

RegexEscapeUtil 구현(필수)  
정규식 메타 문자 이스케이프  
prefix_regex = "^<escaped>.*"

프론트 JS 구현  
debounce 200ms  
최소 입력 길이 2(권장)  
드롭다운 UI 단순 구현(ul/li)  
선택 시 input 채우고 검색 실행

완료 기준  
키워드 자동완성 결과 반환  
기관 자동완성 결과 반환(기관 ID 포함)  
저자 자동완성 결과 반환  
특수문자 입력해도 서버 오류 없이 안전 처리  
타이핑 시 과도한 요청이 발생하지 않음(debounce 적용 확인)

---

## 2.5 5단계: 관리 화면(키워드/기관) 구현

목표  
운영자가 키워드 후보 승인/비활성, 기관 추가/비활성 제어

작업  
ConfigService 구현(keyword_configs/active_config read/write)  
enable/disable keyword 토글  
enable/disable institution 토글  
KeywordNormalizeUtil 적용(소문자, 공백 정리)

키워드 후보 제안(A안) 구현  
최근 N일(기본 30일) works.keyword_candidates terms agg 상위 100  
enabled/disabled에 없는 값만 “제안”으로 표시

AdminPageController / AdminApiController 구현  
/admin/keywords  
제안/활성/비활성 리스트 렌더링  
enable/disable 버튼(Ajax)  
/admin/institutions  
기관 자동완성으로 OpenAlex 기관 선택  
alias 입력 가능  
add/remove(Ajax)

InstitutionService 구현  
institutions 인덱스 upsert  
display_name 저장  
name_aliases에 별칭 저장

완료 기준  
제안 키워드 목록 표시  
키워드 활성/비활성 토글 즉시 반영  
기관 추가/비활성 토글 즉시 반영  
keyword_configs가 단일 문서로 일관되게 유지

---

## 2.6 6단계: 트렌드 구현(키워드/기관)

목표  
메인 페이지에서 급상승 키워드/기관 Top N 제공  
클릭 시 검색으로 이동

작업  
TrendService 구현  
enabled_keywords/enabled_institutions 로드  
최근 90일 date_histogram + moving_fn + bucket_script 쿼리 수행  
최종 점수 선택 규칙 적용  
마지막 버킷(now/d) trend_score  
null이면 마지막 유효 값 fallback  
없으면 0 처리  
Top N 정렬(trend_score desc, MA7 desc, 최근 30일 총량 desc)

TrendController 구현  
GET /  
Model에 keywordTrends, institutionTrends 넣고 index.html 렌더링

기관 상세 URL 확정 사항 반영  
A안 확정  
/institution/{encodedInstitutionId}

완료 기준  
메인에서 Top N 표시  
키워드 클릭 시 /search?keyword=... 이동  
기관 클릭 시 /search?instId=... 이동  
트렌드 쿼리 실패 시 페이지는 깨지지 않고 빈 상태 표시 + 로그 기록

---

## 2.7 7단계: 수집 파이프라인(OpenAlex) + ingest_jobs

목표  
데이터 자동 축적  
하루 1회 증분 수집  
수동 full 수집 가능  
수집 이력/로그 관리 화면 제공

작업  
IngestService 구현  
mode=incremental  
마지막 성공 job 시각 기반으로 증분(가능하면 updated/changed 필터 활용)  
mode=full  
최근 3년 기준으로 전체 수집(운영자 수동 실행용)

수집 저장 전략 확정  
works 문서 ID: source_platform + ":" + source_work_id(OpenAlex work id)  
upsert로 중복 제거  
기관/저자/키워드 필드 정규화(최소한 중복 배열 제거)

ingest_jobs 기록  
job 시작/종료/상태/카운트/에러 요약 저장

Scheduler 연결  
@Scheduled(cron = INGEST_SCHEDULE_CRON)  
기본 incremental 수행  
실패 시 재시도(3회) + 백오프

관리 화면 추가  
/admin/ingest  
수동 실행 버튼(incremental/full)  
최근 job 리스트

완료 기준  
증분 수집이 하루 1회 수행  
수동 실행 가능  
ingest_jobs로 상태 확인 가능  
실패해도 다음 날 재실행 가능(상태/락 처리)

---

## 3. 개발 후 단계(Post-Development)

### 3.1 안정화/테스트
단위 테스트  
KeywordNormalizeUtil, RegexEscapeUtil  
QueryFactory(검색/자동완성/트렌드) JSON 생성 검증

통합 테스트(최소)  
ES가 떠있는 상태에서 검색/자동완성 API 호출이 200 응답  
관리 토글 후 keyword_configs 변경 반영

수동 시나리오 테스트  
키워드 후보 승인 → 트렌드/검색에 반영  
기관 추가 → 기관 트렌드/검색에 반영  
수동 full 수집 → 검색 결과가 늘어나는지 확인

### 3.2 운영 세팅
ES 스냅샷(주 1회) 계획 수립(내부 스토리지)  
로그 로테이션 설정  
애플리케이션 헬스 체크(/actuator/health)

### 3.3 문서화
실행 방법  
Docker Compose 실행  
환경 변수 설정  
초기 full 수집 수행 방법  
장애 시 확인 포인트(ES 상태, ingest_jobs)

---

## 4. 일정 예시(작업 단위 기준)

1주차  
1단계(뼈대) + 2단계(ES) + 3단계(검색)

2주차  
4단계(자동완성) + 5단계(관리)

3주차  
6단계(트렌드) + 7단계(수집/로그)

4주차  
안정화/테스트/문서화/운영 세팅

---

## 5. 남은 확정 필요 사항(없음)

본 Plan.md는 사용자 요청에 따라  
Java 21, Elasticsearch 7.17.x, Lombok, Validation을 모두 확정 반영했으며  
추가적인 미확정 항목 없이 실행 가능한 수준으로 고정되었다.
