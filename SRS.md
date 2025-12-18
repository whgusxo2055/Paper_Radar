# SRS v1 (Final)
## 프로젝트명: PAPER_RADAR
### 논문·기술 트렌드 레이더
Internal Web Service (≤ 5 CCU)

---

## 1. 확정된 전제 사항

- 내부 전용 서비스, 동시 접속자 최대 5명
- 회원가입 / 로그인 없음
- HTTPS 미적용
- UI는 웹(Web) 고정
- Frontend: **Spring MVC + Thymeleaf**
- Backend: Java Spring Boot
- 검색/분석 엔진: **Elasticsearch 필수**
- 트렌드 산정: 이동평균 기반(B안)
- 수집 범위: 키워드 + 기관 병행
- 수집 주기: 하루 1회
- 초기 데이터 수집 기간: 최근 **3년**
- 키워드 관리: 자동 후보 제안(A안) + 운영자 승인
- 기관 관리: OpenAlex 기관 ID(정규) + 이름 문자열 병행
- 외부 원문 링크 제공(DOI / landing / PDF 가능한 범위)
- 유사 논문 추천 기능은 2차 단계로 이관

---

## 2. 서비스 목적

**PAPER_RADAR**는 공개 학술 메타데이터 API(OpenAlex 등)에서 논문 정보를 주기적으로 수집하고,  
Elasticsearch 기반 검색 및 집계를 통해  
키워드 및 기관 중심의 연구·기술 트렌드를 내부에서 효율적으로 탐색하는 것을 목적으로 한다.

---

## 3. 범위 정의

### 포함
- 논문 메타데이터 수집(하루 1회)
- Elasticsearch 기반 검색
    - 전문 검색
    - 필터(기간/기관/키워드/저자)
    - 정렬(관련도/최신/인용수)
    - 자동완성(Ajax)
- 이동평균 기반 트렌드 분석
- 기관/저자 단위 요약
- 논문 상세 화면 + 외부 원문 링크
- 관리 화면
    - 키워드 후보 승인/비활성
    - 기관 등록/비활성
    - 수집 수동 실행 및 로그 확인

### 제외
- 회원 관리 / 인증
- HTTPS
- 논문 유사도 추천

---

## 4. 데이터 소스(API)

### 4.1 1차 소스 (확정)
**OpenAlex**
- 논문, 기관, 저자, 저널 구조 제공
- OpenAlex 기관 ID 사용 가능
- landing/pdf/OA 링크 제공 가능

### 4.2 병행 소스 (선택)
- Crossref: DOI 및 full-text 메타데이터 보강
- Semantic Scholar: OA PDF 링크 보강 (2차)

---

## 5. 외부 원문 링크 제공 정책

우선순위
1. DOI 링크
2. OpenAlex landing_page_url
3. OpenAlex pdf_url 또는 open_access.oa_url
4. Semantic Scholar openAccessPdf (병행 시)
5. Crossref full-text URL (병행 시)

- 가능한 링크만 노출
- 새 탭으로 열기
- 링크 유형(DOI / Landing / PDF) 배지 표시

---

## 6. 기술 스택

### Backend
- Java Spring Boot
- Spring MVC

### View
- Thymeleaf

### Scheduler
- Spring Scheduler (`@Scheduled`)
- 하루 1회 실행

### Search / Analytics
- Elasticsearch (Java API Client)

### Deployment
- Docker Compose
- 단일 서버/단일 ES 노드

---

## 7. 기능 요구사항

### FR-1 논문 검색
- 검색 대상: 제목, 초록, 키워드, 저자명, 기관명
- 필터: 기간, 기관(OpenAlex ID), 키워드, 저자
- 정렬: 관련도 / 최신 / 인용수

### FR-2 자동완성(Ajax)
- 키워드: works.keyword_candidates 기반(A안)
- 기관: institutions 인덱스
- 저자: works.authors nested 기반

### FR-3 트렌드 분석
- 일 단위 집계
- MA7 / MA30 이동평균
- trend_score = (MA7 - MA30) / max(MA30, 1)
- Top N 키워드 / 기관 제공

### FR-4 기관 분석
- 기관 ID(OpenAlex) 기준
- 최근 30/90일 논문 수
- 주요 키워드
- 논문 목록

### FR-5 키워드 자동 후보 제안(A안)
- 최근 N일 빈도 상위 키워드 자동 제안
- 운영자 승인 후 활성화

### FR-6 관리 기능
- 키워드/기관 활성·비활성
- 수동 수집 실행
- 수집 상태/로그 확인

---

## 8. Elasticsearch 인덱스

- works
- institutions
- keyword_configs
- ingest_jobs
- (authors: 선택)

---

## 9. 비기능 요구사항

- 검색 응답 p95 ≤ 1초
- 트렌드 집계 p95 ≤ 2초
- 단일 노드 장애 허용
- ES 스냅샷 주 1회 권장
- 내부망 접근 통제(IP allowlist) 권장

---

## 10. 확정 상태

본 문서는 **PAPER_RADAR 프로젝트의 SRS v1 최종 확정본**이며,  
본 문서를 기준으로 개발을 즉시 시작할 수 있다.
