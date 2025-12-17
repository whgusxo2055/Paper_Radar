# Repository Guidelines

## Agent-Specific Instructions
- 질의응답(대화)은 항상 한국어로 진행합니다.
- 개발 작업을 시작하기 전에 반드시 `SRS.md`와 `Plan.md`를 먼저 확인하고, 요구사항/범위/우선순위를 정렬한 뒤 진행합니다.
- 요구사항이 불명확하거나 선택지가 있는 경우(예: API 스펙, 화면 동작, 데이터 모델)는 임의로 확정하지 말고 질문을 통해 확인한 후 진행합니다.
- 하나의 테스크(작업 단위)를 완료하면, 완료 내용 요약과 함께 “다음 진행 계획(다음 테스크 후보/순서)”을 보고합니다.
- Lombok을 적극 활용합니다(예: `@Getter`, `@Builder`, `@RequiredArgsConstructor`).
- DTO는 가능하면 Java `record`를 우선 사용합니다(불변/간결). 단, Spring MVC 바인딩이 필요한 경우에는 record 대신 불변 객체(+Builder) 또는 폼 전용 DTO를 사용합니다.
- `setter` 사용은 지양하고, 생성자/정적 팩토리/Builder 패턴을 우선합니다.

## Project Structure & Module Organization
- `src/main/java/personal/paper_radar/`: Spring Boot 애플리케이션 코드(Java 21).
- `src/main/resources/`: 리소스(`templates/` Thymeleaf 뷰, `static/` 정적 파일, `application.properties` 기본 설정).
- `src/test/java/personal/paper_radar/`: JUnit 5 + Spring Boot 테스트.
- 기획/요구사항 문서: `Plan.md`, `SRS.md`.

## Build, Test, and Development Commands
Gradle Wrapper 사용을 권장합니다.
- `./gradlew bootRun`: 로컬 실행(일반적으로 `http://localhost:8080`).
- `./gradlew test`: 테스트 실행(JUnit Platform).
- `./gradlew build`: 컴파일 + 테스트 + 패키징(`build/`).
- `./gradlew clean`: 빌드 산출물 삭제.
- `./gradlew bootJar`: 실행 가능한 JAR 생성(`build/libs/`).

## Coding Style & Naming Conventions
- Java 들여쓰기 4칸, 과도하게 긴 메서드는 분리합니다.
- 패키지: 소문자(`personal.paper_radar`), 클래스: `UpperCamelCase`, 메서드/필드: `lowerCamelCase`.
- 규모가 커지면 책임별 패키지 분리 권장: `.../web`, `.../service`, `.../repository`, `.../domain`.

## Testing Guidelines
- JUnit 5 + Spring Boot 테스트 지원 사용.
- 테스트 이름: `*Test` 또는 `*Tests`(예: `PaperRadarApplicationTests`).
- `src/test/java/`에 패키지 구조를 맞추고, 테스트 메서드는 “하나의 행동/기대”에 집중합니다.

## Commit & Pull Request Guidelines
- 현재 Git 히스토리가 없으므로 커밋 메시지는 Conventional Commits 권장:
  - 예: `feat: add search endpoint`, `fix: handle empty query`, `docs: update SRS`.
- PR에는 변경 요약, 근거, 테스트 방법(`./gradlew test`), UI 변경 시 스크린샷을 포함합니다.
- PR은 작고 명확하게 유지하고, 동작 변경 시 문서/설정도 함께 업데이트합니다.

## Configuration & Security Tips
- 기본값은 `application.properties`에 둡니다.
- 로컬 전용 설정은 `application-local.properties` 같은 프로파일 파일로 분리하고, 시크릿은 커밋하지 않으며 환경변수를 우선 사용합니다.
