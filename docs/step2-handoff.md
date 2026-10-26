# 2번 작업 인수인계 — 2026-10-23 WIP 체크포인트

브랜치: `refactor/backend-quality`. 이 커밋은 다른 컴퓨터에서 이어서 작업하기 위한 **미완료 상태 저장**이다. 2번 완료, 최종 staged tree 독립 승인, `full-refactor` 게시 승인을 뜻하지 않는다. 저장소의 최종 게시 스크립트는 완료 단계 전용이므로 이 WIP 체크포인트에는 사용하지 않았다.

## 확정 계약과 현재 구현

- Redis는 친구 신청의 분산 잠금과 요청 횟수 제한에만 사용한다. 장애 우회 요청을 저장하는 별도 테이블은 만들지 않는다. Redis 장애·TTL 만료 시 친구 관계 정합성은 사용자 행 잠금, 트랜잭션, PostgreSQL 활성 양방향 유니크 인덱스로 지킨다.
- 같은 방향의 `REJECTED` 요청을 다시 신청하면 UUID를 재사용하고 `requestedAt`을 현재 신청 시각으로 바꿔 오늘 목록에 표시한다. 반대 방향 신청은 새 행을 만든다.
- 친구 관계 유니크 충돌은 409, DB 자체 장애는 503으로 구분한다. 모든 5xx 공개 메시지는 `서버에 문제가 생겼습니다. 잠시 후 다시 시도해주세요.`로 통일했다.
- 개발 DB의 `friendship`은 확인 당시 0행이었고 방향별 유니크 인덱스는 있었다. 활성 양방향 인덱스는 아직 로컬 개발 DB에 적용되지 않았다. 다른 DB에서 제약 적용을 막는 중복이 있으면 자동 삭제·병합하지 않고 사용자 결정을 받는다. [읽기 전용 확인 쿼리](step2-db-preflight.md)를 참조한다.

## 결정과 수정 상태

1. `S2-004`는 2026-10-25 확정했다. Redis 정상 시 Redis 제한기, 장애 시 기존 사용자 ID별 JVM 메모리 제한기를 사용하고 `HttpSession`은 도입하지 않는다. 만료 기록 정리 후에도 10,000 사용자 기록 슬롯이 가득 차면 신규 ID는 HTTP 429로 차단하고 기존 ID의 제한 창을 유지한다. 코드와 단위 테스트는 반영했으며 전체 통합 검증은 남아 있다.
2. 독립 예비 리뷰의 세 지적은 2026-10-25 작업 트리에서 수정했고 분리된 검증 트리에서 회귀 테스트가 통과했다. 최종 staged tree 독립 리뷰는 남아 있다.
   - `RedisRateLimiter.java`: 복구 직후에도 같은 사용자의 활성 로컬 제한 창을 유지한다.
   - `LimitInterceptor.java`: 설정한 신청 POST 경로와 정확히 일치할 때만 횟수를 제한한다.
   - `CommExceptionControllerAdvice.java`: 확인된 친구 관계 제약의 SQLSTATE `23505`만 409로 매핑한다.
3. 개발 DB와 테스트 DB의 방향별 유니크 인덱스 이름이 각각 `uk8pnm9rovglqfddxo47d2afm35`, `friendship_requester_id_receiver_id_key`로 다르다. 제약 이름 판별 변경 시 두 환경과 활성 인덱스 `ux_friendship_active_pair`를 검증한다.

## 실행 증거

| 시점 | 명령 | 결과 |
|---|---|---|
| 2026-10-23 15:30 KST, 리뷰 회귀 테스트 추가 전 | `.\gradlew.bat lint test sampleDataClasses --rerun-tasks --no-build-cache` | exit 0, 9개 작업 성공 |
| 2026-10-23 15:34 KST, 리뷰 지적 재현 | `.\gradlew.bat test --tests com.apr.aprbackendassignment.util.RedisRateLimiterFallbackTest --tests com.apr.aprbackendassignment.controller.FriendFailureApiTest --tests com.apr.aprbackendassignment.controller.FriendRequestRedisOutageApiTest --rerun-tasks --no-build-cache` | exit 1, 13건 중 알려진 결함 3건 실패 |
| 2026-10-23 15:40 KST | `.\gradlew.bat test --tests com.apr.aprbackendassignment.repository.FriendshipConstraintTest --rerun-tasks --no-build-cache` | exit 0, 테스트 DB 인덱스 확인 포함 |
| 2026-10-23, 최신 테스트 소스 | `.\gradlew.bat compileTestJava --rerun-tasks --no-build-cache`, `.\gradlew.bat lint --rerun-tasks` | 각각 exit 0 |

최종 테스트가 현재 모두 통과한다는 뜻은 아니다. 위 3개 실패는 결함 재현을 위해 유지했다. 프로덕션 수정 후 전체 `lint test sampleDataClasses`를 다시 실행하고, 독립 코드·보안 리뷰와 정확한 staged tree 검토를 받아야 2번 완료로 기록할 수 있다.

## 다음 작업 순서

1. Docker daemon을 복구한 뒤 새 DB 유니크 회귀 테스트와 전체 통합 테스트를 실행한다. `S2-004` 포화 시 429 코드와 단위 테스트, 세 예비 리뷰 결함 수정은 반영했다.
2. 전체 lint·test·sampleDataClasses, 독립 리뷰, staged tree 검사를 다시 수행한다.
3. 2번 완료 기준이 충족된 뒤에만 상태를 `complete`로 바꾸고 게시 절차를 판단한다.

로컬 개발 DB, Redis 데이터, Gradle 산출물, IDE 설정, 로그, 사용자 제외 요청이 있는 `기능요구사항.pdf`는 이 체크포인트에 포함하지 않는다. Git hook과 역할·기준 문서는 이미 추적 중이다.

## 2026-10-25 진행 상황

- 예비 리뷰의 세 결함을 `RedisRateLimiter.java`, `LimitInterceptor.java`, `CommExceptionControllerAdvice.java`에서 수정했다. 친구 관계 제약 이름의 409 매핑 테스트 2건을 보강했고, 조회 API 회귀 테스트에 필수 `sort` 입력을 추가했다. 코드·보안 독립 예비 리뷰에서 이 수정의 새 결함은 발견되지 않았다.
- 원래 작업 트리에 있던 범위 밖 `FriendRequestFacade.java`의 `transferPoint` 추가 코드는 Java 문법에 맞지 않아 주 작업 트리의 `./gradlew.bat compileJava --rerun-tasks --no-build-cache`가 문법 오류 9건으로 exit 1이었다. 사용자의 `PUB-002` 지시에 따라 해당 미커밋 추가분과 빈 미추적 `package-lock.json`을 제거했고, 같은 컴파일 명령과 `./gradlew.bat lint --rerun-tasks`가 각각 exit 0으로 통과했다.
- 사용자 변경을 보존하기 위해 HEAD와 위 다섯 변경 파일만으로 분리된 검증 작업 트리를 만들었다. 이 검증 트리의 코드·테스트 Git tree는 `7adf6a4f97abb2f83d256074f22619a397b765ba`다. `./gradlew.bat lint test sampleDataClasses --rerun-tasks --no-build-cache`는 exit 0, 테스트 50건 성공·실패 0건·건너뜀 0건이었다. 이는 현재 주 작업 트리 또는 최종 staged tree의 승인 증거는 아니다.
- `S2-004`에 따라 포화 시 신규 ID를 429로 차단하고 기존 ID의 제한 창을 유지하도록 `RedisRateLimiter`를 수정했다. 가득 찬 맵의 만료 기록은 가장 이른 창 만료 시각에 다시 정리한다. 이전의 통과 동작을 기대한 테스트 2건을 429 계약으로 고쳤고, 정확한 만료 경계와 서로 다른 시작 시각의 창이 차례로 만료되는 사례를 추가했다. 이 클래스의 단위 테스트 6건은 통과했지만 전체 통합 테스트와 최종 staged tree 리뷰는 남아 있다.
- 2026-10-25 사용자는 Redis 장애 시 첫 신청을 DB에 저장하고 이후 중복을 유니크 제약으로 막는 방향을 검토했다. 실제 제약은 같은 두 사용자 간 중복만 막고 다른 상대 신청 횟수는 제한하지 않음을 확인한 뒤, 기존 JVM 메모리 제한기와 포화 시 429 방식을 선택했다. 이미 확인된 같은 방향 유니크 계약을 위해 `FriendshipConstraintTest`에 직접 SQL 중복 삽입 테스트를 추가했다. 주 작업 트리의 `./gradlew.bat compileTestJava --rerun-tasks --no-build-cache`와 `./gradlew.bat lint --rerun-tasks`는 각각 exit 0이었다. Docker daemon 오류로 새 통합 테스트 실행은 남아 있다.
- 최신 주 작업 트리에서 `./gradlew.bat test --tests com.apr.aprbackendassignment.util.RedisRateLimiterFallbackTest --rerun-tasks --no-build-cache`는 exit 0, 6건 성공이었다. `./gradlew.bat lint --rerun-tasks`도 exit 0이었다. 그보다 앞선 전체 `./gradlew.bat lint test sampleDataClasses --rerun-tasks --no-build-cache`는 Docker daemon 연결 불능으로 exit 1이었다. 당시 51건 중 Testcontainers 초기화 실패가 47건이며, 이후 단위 테스트 2건을 추가했으므로 51건을 현재 전체 테스트 수로 간주하지 않는다.
- 현재 신청 API의 제한기 키는 `X-user-Id` 헤더 값이다. 인증된 사용자 ID와 결합하는 보장은 이 범위에서 확인되지 않았으므로, 이 제한기를 신뢰할 수 없는 클라이언트에 대한 보안 통제로 설명하지 않는다.

## 2026-10-26 최종 검증 전 발견 사항

- Docker daemon을 실행한 뒤 현재 작업 트리에서 `.\gradlew.bat lint test sampleDataClasses --rerun-tasks --no-build-cache`가 exit 0으로 통과했다. JUnit XML 기준 53건 성공, 실패·건너뜀 0건이다. 이는 아래 새 회귀 테스트를 추가하기 전 결과다.
- 독립 보안 예비 리뷰가 `POST /api/friends/request;x=1`도 Spring MVC의 신청 핸들러에 매핑되지만 원시 URI 완전 일치 검사에서 요청 제한을 건너뛰는 문제를 발견했다. `FriendRequestRedisOutageApiTest.semicolonPathAliasStillConsumesApplicationRateLimit`를 추가해 실행한 결과 exit 1, 세 번째 요청이 기대한 429 대신 200을 반환했다.
- 사용자가 “진행해”라고 승인한 뒤 `LimitInterceptor.java`의 경로 판별을 Spring MVC가 실제로 매핑한 신청 경로 기준으로 바꿨다. 수정 후 `FriendRequestRedisOutageApiTest` 3건은 exit 0으로 통과했다. 새 테스트와 기존 장애 테스트가 로컬 제한 기록을 공유하지 않도록 테스트 시작 시 해당 상태를 초기화한다. 전체 검증과 정확한 staged tree 독립 리뷰가 최종 게시 조건이다.

## 2026-10-26 전체 검증 결과와 게시 범위

- 테스트 컨텍스트를 테스트마다 재생성하면 공유 PostgreSQL 스키마가 삭제되어 전체 54건 중 6건이 실패했다. 이 테스트 전용 설정을 제거하고 각 테스트 시작 시 로컬 제한기 상태만 초기화했다.
- 수정 후 `.\gradlew.bat lint test sampleDataClasses --rerun-tasks --no-build-cache`가 exit 0으로 통과했다. JUnit XML 기준 54건 성공, 실패·건너뜀 0건이다. `STATE.json`은 1~2번 구현·전체 검증 완료와 3~6번 미완료를 구분한다.
- `step-2-checkpoint` 게시에는 정확한 staged tree의 독립 리뷰 승인과 게시 게이트의 재검증이 추가로 필요하다. 이 문서의 초기 WIP 기록은 이전 인수인계 시점의 역사이며 현재 완료 범위를 대신하지 않는다.
