# 6번 단계 조사와 변경 경계

기준: `BACKEND_QUALITY_REVIEW.md` 20절 6번. 시작 HEAD는 `579d346`이며 1~5번은
`refactor/backend-quality`에 게시됐다. 이 문서는 조사와 문서 정리 기록이며 6번 완료 증거가 아니다.

## 목표와 불변 계약

6번은 파일 위치·Java 이름·주석·문서를 실제 책임과 맞추는 P3 단계다. 다음은 유지한다.

- HTTP 경로, JSON 필드·상태·오류 코드와 Spring 설정 키
- PostgreSQL 테이블·컬럼·인덱스와 enum 저장 literal
- 친구 신청·수락·거절의 트랜잭션·잠금 규약, Redis 키 예산 및 장애 fallback 정책
- 개발 데이터를 만드는 `seedSampleData`의 명시적 실행 성격

패키지 또는 공개 모델 이름 변경이 OpenAPI 스키마나 외부 Java 소비자에 미치는 영향은 구현 전에
확인한다. 이 저장소 안의 import 수정만으로 외부 소비자 호환성을 주장하지 않는다.

## 확정된 이동·이름 정리 계약

다음은 6번에서 적용할 내부 Java 구조다. 공개 HTTP/OpenAPI, 설정 키, DB 스키마·enum literal,
Redis 정책과 트랜잭션은 바꾸지 않는다. `FRIENDSHIP_STATUS`와 `Users`의 Java 이름은 유지해
OpenAPI 구성 요소와 JPA 모델 이름에 불필요한 변경을 만들지 않는다.

| 현재 | 6번 후 위치·이름 |
|---|---|
| `controller/FriendController`, `common/request/PageRequestParam`, `model/dto/request/FriendRequest`, `model/constant/SORT_GROUP` | `friendship.presentation`; `SORT_GROUP`은 `FriendSort` |
| `service/FriendService`, `service/impl/FriendServiceImpl`, `util/FriendRequestFacade`, `util/FriendLimitProperties`, `model/dto/response/FriendsResponse`, `model/dto/response/FriendsRequestsResponse`, `model/constant/WINDOW_SLIDING` | `friendship.application`; `WINDOW_SLIDING`은 `RequestWindow` |
| `model/entity/Friendship`, `model/constant/FRIENDSHIP_STATUS`, `common/exception/FriendshipError`, `common/exception/FriendshipException` | `friendship.domain`; `FRIENDSHIP_STATUS` 이름 유지 |
| `repository/FriendshipJPARepository`, `util/RedisLockUtil` | `friendship.infrastructure`; `RedisLockUtil`은 `FriendshipLockProvider` |
| `model/entity/Users`, `repository/UsersJPARepository` | 각각 `user.domain`, `user.infrastructure`; `Users` 이름 유지 |
| `common/interceptor/LimitInterceptor`, `util/LimitProperties`, `util/RedisRateLimiter` | `ratelimit`; 앞의 두 타입은 `RateLimitInterceptor`, `RateLimitProperties` |
| `common/controllerAdivce/CommExceptionControllerAdvice` | `common.advice/CommExceptionControllerAdvice` |

연결 설정도 같은 작업에 갱신한다. `WebConfig` import, `application.yaml`의 Springdoc scan,
`logback-spring.xml`의 무효 `com.example` logger, `src/sampleData/.../sampleData`의
`sampledata` 패키지와 `build.gradle`의 `seedSampleData.mainClass`가 대상이다.

## 확인된 정리 후보

| 영역 | 현재 확인된 항목 | 연결된 파일·설정 |
|---|---|---|
| 친구 기능 | controller, DTO, 서비스, 관계 엔티티·상태, repository, facade·분산 락이 `common`, `model`, `service`, `util`에 나뉘어 있다. | `src/main/java/com/apr/aprbackendassignment/**`, `src/test/**` import |
| 요청 제한 | `LimitInterceptor`, `LimitProperties`, `RedisRateLimiter`가 친구 규칙과 분리된 기술 책임이다. | `WebConfig`, `application.yaml`, controller API tests |
| 사용자 | `Users`, `UsersJPARepository`가 친구 패키지와 다른 사용자 조회 경계다. | 관계 엔티티·서비스·fixture·sampleData |
| 설정·도구 | Swagger controller scan, Logback logger, sample-data main class가 현재 패키지명을 직접 참조한다. | `application.yaml`, `logback-spring.xml`, `build.gradle` |
| 설명 | `CommTimeEntity`, `FriendRequest`, `RedisLockUtil`, interceptor 등의 주석에 실제와 다른 설명이 있다. | 해당 JavaDoc과 README |

이동표는 구현 전 확정한 계약이다. 실제 이동·참조·README 링크는 6번 실행 이력과 검증 결과로
확인한다.

## 현재 문서·운영 사실

- `579d346`은 5번 게시 commit이며, 게시 전 전체 검증은 20개 suite·84개 test,
  실패·오류·건너뜀 0으로 통과했다. 이 결과는 6번 변경의 증거가 아니다.
- `architecture.png`는 H2와 이전 계층명을 보여 현재 코드 설명으로 사용하지 않는다. 파일은
  보존하고 README는 현재 코드 흐름을 Mermaid로 표시한다.
- `seedSampleData`는 개발 DB에 데이터를 추가하므로 6번의 클래스 경로 검증에서는 실행하지 않는다.
  `sampleDataClasses` 컴파일과 `build.gradle`의 `mainClass` 대조로 확인한다.

## 완료 전 검증

```powershell
.\gradlew.bat lint test sampleDataClasses --rerun-tasks --no-build-cache
```

Docker가 실행 중이어야 Testcontainers 기반 전체 테스트가 수행된다. 문서·코드·테스트·설정이
확정된 뒤 하나의 staged tree를 독립 코드·보안 리뷰에 제공하고, 승인 뒤에만
`full-refactor` 범위로 게시한다.

## 구현 및 작업 트리 검증 (2026-10-28)

- 표의 Java 패키지·타입 이동, 테스트·fixture import, `sampledata` 패키지와 Gradle mainClass,
  Springdoc 스캔 경로, Logback의 무효 logger 제거, README 링크를 반영했다.
- 사용자 요청에 따라 `FriendServiceImpl.requestFriend`의 기존 관계·재신청 후보 탐색을
  `stream().filter().findFirst()`로 바꿨다. 후보 순서와 상태 변경 시각은 유지한다.
  예외 원인 순회, Redis 동시성 정리, 샘플 데이터 저장 순회는 제어 흐름과 부작용을 보존했다.
- 실제 `.\\gradlew.bat lint test sampleDataClasses --rerun-tasks --no-build-cache` exit 0:
  20개 suite·85개 test, 실패·오류·건너뜀 0. 추가한 API 테스트는 OpenAPI 문서에
  `/api/friends/request`가 남는 것을 확인한다.
- 이 기록은 작업 트리 검증이다. 최종 staged tree의 독립 리뷰와 게이트 검증·게시 결과는
  별도로 기록한다. `seedSampleData`는 실행하지 않았다.
