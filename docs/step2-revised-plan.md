# 2번 작업 완료 계획 — 2026-10-22 사용자 답변 반영

이 계획은 `BACKEND_QUALITY_REVIEW.md` 20절, `agent-context/PLAN.md`의 2번, 기존 S2-001/002/003/003-ERROR 결정과 2026-10-22 사용자 답변을 따른다. 프로덕션 변경 전 승인용이며, 기존 작업 트리의 변경은 보존한다.

## 확정된 동작

- A→B의 같은 방향 `REJECTED` 행을 재신청하면 UUID를 유지하고 **새 신청 업무시각은 오늘**로 기록한다. 받은 신청 DAY 목록의 기간 필터와 `requestedAt` 응답은 이 업무시각을 사용한다. 기존 행은 `createdTime`으로 대체해 읽는다.
- DB 유니크 제약 충돌은 HTTP 409, DB 사용 불능은 HTTP 503으로 구분한다. 503을 포함한 모든 5xx 오류의 사용자 표시 메시지는 `서버에 문제가 생겼습니다. 잠시 후 다시 시도해주세요.`로 통일한다. 오류 코드와 HTTP 상태는 구분한다.
- Redis는 친구 신청 내용을 저장하지 않고 잠금과 요청 횟수 제한에만 사용한다. 사용자가 제안했다가 폐기한 Redis 우회 요청용 별도 DB 테이블은 만들지 않는다. Redis TTL 만료·장애 중에도 친구 관계의 DB 제약과 트랜잭션은 유지한다.
- 현재 로컬 개발 DB의 `friendship`은 0행이고 `(requester_id, receiver_id)` 방향별 유니크 인덱스가 설치돼 있다. 따라서 같은 방향 중복 이력 문제는 이 DB에서는 발생하지 않는다. 활성 양방향 인덱스는 아직 설치되지 않았으므로 새 코드 기동 시 설치·검증한다. 다른 DB에 충돌 데이터가 있으면 삭제·이관하지 않고 별도 사용자 결정을 받는다.

## 승인된 변경과 예상 동작

1. `src/main/java/com/apr/aprbackendassignment/model/entity/Friendship.java`, `repository/FriendshipJPARepository.java`, `model/dto/response/FriendsRequestsResponse.java`, `model/constant/SORT_GROUP.java`, `service/impl/FriendServiceImpl.java`: `requestedAt` 업무시각을 추가해 신규·재신청 때 설정한다. 목록 필터·응답·정렬은 이 시각을 쓰고 기존 행은 생성 시각으로 대체한다. 기존 ID 재사용과 사용자 행 잠금·친구 수 상한은 유지한다.
2. `src/main/java/com/apr/aprbackendassignment/util/RedisRateLimiter.java` 및 필요할 때만 `util/FriendRequestFacade.java`: Redis가 불능이어도 DB 제약과 사용자 행 잠금으로 신청을 처리한다. 로컬 요청 제한기의 10,000 사용자 슬롯이 찼을 때 신규 ID를 무제한 통과시키는 현 동작은 별도 정책 결정에 따라 조정한다. 우회 기록 테이블·관리자 기능은 만들지 않는다.
3. `src/main/java/com/apr/aprbackendassignment/common/controllerAdivce/CommExceptionControllerAdvice.java`, `common/response/CommResponseStatus.java`: 방향별/활성 양방향 friendship 유니크 제약 충돌을 409로 매핑한다. DB 장애는 503과 별도 코드로 매핑한다. 5xx 응답 메시지는 모두 위 일반 문구로 맞추고 SQL/스키마 정보는 노출하지 않는다.
4. `src/main/resources/schema.sql`, `src/main/resources/application.yaml`: 현재 빈 개발 DB에 양방향 활성 유니크 인덱스가 설치되는지 검증한다. 다른 DB에서 충돌 데이터가 있으면 자동 삭제·이관하지 않고 별도 결정을 받는다.

## 승인 없이 진행 가능한 테스트와 완료 게이트

- 오래된 거절 행의 같은 UUID 재신청과 오늘 DAY 목록·응답 시각, Redis 장애·TTL 만료 시 DB 일관성, 실제 PostgreSQL 유니크 409 및 DB 장애 503을 검증한다.
- 전체 `./gradlew.bat lint test sampleDataClasses --rerun-tasks --no-build-cache` 성공과 최종 staged tree의 독립 코드·보안 리뷰 수정 필요 0건을 확인한 뒤에만 2번 완료를 기록한다.
- 추가한 방향별 유니크 충돌 409 테스트는 현 프로덕션에서 실패해 수정 필요를 재현했다. 테스트 실패를 기준 변경으로 숨기지 않는다.
- `./gradlew.bat test --tests com.apr.aprbackendassignment.service.impl.FriendServiceImplTest --tests com.apr.aprbackendassignment.controller.FriendFailureApiTest --rerun-tasks --no-build-cache`는 2026-10-22 현재 exit code 1이었다(22건 중 6건 실패). 방향별 유니크 충돌, 5xx 일반 메시지 네 경로, 오래된 거절 행의 오늘 DAY 목록 노출이 각각 red test로 재현됐다.
- 409/5xx 수정 후 `FriendFailureApiTest` 단독, 업무시각 수정 후 `FriendServiceImplTest` 단독이 각각 exit code 0으로 통과했다. 이후 전체 `./gradlew.bat lint test sampleDataClasses --rerun-tasks --no-build-cache`도 2026-10-22 17:01 KST 현재 작업 트리에서 exit code 0으로 통과했다. 최종 수정·staging 뒤에는 다시 검증한다.
- 재신청 응답의 오늘 날짜와 정렬 순서 검증을 추가한 뒤 2026-10-23 15:30 KST에 같은 전체 명령을 다시 실행했다. exit code 0, Gradle 9개 작업 실행 및 `BUILD SUCCESSFUL`을 확인했다. 최종 staged tree 검증은 별도로 남아 있다.

## 남은 결정

Redis 장애 중 로컬 요청 제한기의 10,000 사용자 슬롯이 찼을 때 미등록 사용자 ID를 HTTP 429로 막을지, DB 처리로 넘길지 결정해야 한다. 현재는 해당 ID를 기록하지 않고 반복 요청을 무제한 허용한다. 이 정책은 친구 관계의 DB 일관성과 별개인 요청 횟수 제한 계약이다.

## 2026-10-23 독립 예비 리뷰 지적과 수정 계획

- `RedisRateLimiter.java`: Redis 장애 중 같은 사용자에게 로컬 창에서 허용한 횟수가 복구 직후 Redis 창에서 다시 시작된다. 활성 로컬 창을 해당 사용자에게 창 만료까지 적용하고, 만료 후 Redis 경로로 복귀한다. 기존 2초 재시도·단일 복구 probe는 유지한다.
- `LimitInterceptor.java`: 경로 접두어 비교가 `GET /api/friends/requests`를 `POST /api/friends/request`의 횟수 제한 대상으로 오인한다. 설정된 신청 경로와 HTTP 메서드를 정확히 확인해 신청 요청에만 제한을 적용한다.
- `CommExceptionControllerAdvice.java`: 모든 SQLSTATE `23505`를 친구 신청 충돌로 반환한다. Hibernate 예외의 구조화된 제약 이름을 확인해 `friendship`의 방향별 유니크 제약과 활성 양방향 인덱스 충돌만 `FRIEND_REQUEST_CONFLICT` 409로 매핑하고, 다른 테이블의 충돌은 일반 500으로 처리한다. 공개 5xx 메시지는 승인된 일반 문구를 유지한다. 로컬 DB 방향별 인덱스는 `uk8pnm9rovglqfddxo47d2afm35`, 테스트 DB에서는 `friendship_requester_id_receiver_id_key`로 확인돼 두 환경의 이름을 함께 처리한다. PostgreSQL 드라이버는 현재 런타임 의존성이므로 해당 클래스 직접 참조는 추가하지 않는다.
- 위 세 경로의 회귀 테스트를 먼저 추가했다. `./gradlew.bat test --tests com.apr.aprbackendassignment.util.RedisRateLimiterFallbackTest --tests com.apr.aprbackendassignment.controller.FriendFailureApiTest --tests com.apr.aprbackendassignment.controller.FriendRequestRedisOutageApiTest --rerun-tasks --no-build-cache`는 2026-10-23 15:34 KST에 exit code 1이었다(13건 중 위 결함을 가리키는 3건 실패). 프로덕션 변경 승인 후 수정하고 전체 검사를 다시 실행한다.
