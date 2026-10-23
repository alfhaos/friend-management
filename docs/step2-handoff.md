# 2번 작업 인수인계 — 2026-10-23 WIP 체크포인트

브랜치: `refactor/backend-quality`. 이 커밋은 다른 컴퓨터에서 이어서 작업하기 위한 **미완료 상태 저장**이다. 2번 완료, 최종 staged tree 독립 승인, `full-refactor` 게시 승인을 뜻하지 않는다. 저장소의 최종 게시 스크립트는 완료 단계 전용이므로 이 WIP 체크포인트에는 사용하지 않았다.

## 확정 계약과 현재 구현

- Redis는 친구 신청의 분산 잠금과 요청 횟수 제한에만 사용한다. 장애 우회 요청을 저장하는 별도 테이블은 만들지 않는다. Redis 장애·TTL 만료 시 친구 관계 정합성은 사용자 행 잠금, 트랜잭션, PostgreSQL 활성 양방향 유니크 인덱스로 지킨다.
- 같은 방향의 `REJECTED` 요청을 다시 신청하면 UUID를 재사용하고 `requestedAt`을 현재 신청 시각으로 바꿔 오늘 목록에 표시한다. 반대 방향 신청은 새 행을 만든다.
- 친구 관계 유니크 충돌은 409, DB 자체 장애는 503으로 구분한다. 모든 5xx 공개 메시지는 `서버에 문제가 생겼습니다. 잠시 후 다시 시도해주세요.`로 통일했다.
- 개발 DB의 `friendship`은 확인 당시 0행이었고 방향별 유니크 인덱스는 있었다. 활성 양방향 인덱스는 아직 로컬 개발 DB에 적용되지 않았다. 다른 DB에서 제약 적용을 막는 중복이 있으면 자동 삭제·병합하지 않고 사용자 결정을 받는다. [읽기 전용 확인 쿼리](step2-db-preflight.md)를 참조한다.

## 미해결 결정과 수정 승인

1. Redis 장애 중 로컬 제한기 10,000명 슬롯이 모두 찼을 때 신규 사용자 ID를 HTTP 429로 차단할지 DB 처리로 통과시킬지 사용자 답변 대기. 현재 코드는 미등록 ID의 반복 요청을 추적하지 않고 통과시킨다. `agent-context/STATE.json`의 `S2-004`를 참조한다.
2. 독립 예비 리뷰에서 다음 3건의 수정 필요가 나왔다. 사용자에게 [프로덕션 수정 계획](step2-revised-plan.md)의 사전 승인을 요청한 상태다. 승인 전 해당 파일을 수정하지 않는다.
   - `RedisRateLimiter.java`: 장애 중 사용한 로컬 횟수가 Redis 복구 직후 같은 사용자에게 이어지지 않아 한 창에서 초과 요청이 허용된다.
   - `LimitInterceptor.java`: `/api/friends/request` 접두어 검사로 `GET /api/friends/requests`가 신청 횟수를 소모한다.
   - `CommExceptionControllerAdvice.java`: 모든 SQLSTATE `23505`를 친구 신청 충돌로 취급해 다른 테이블의 충돌도 잘못 표시한다.
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

1. 사용자에게 요청한 두 답변을 `agent-context/DECISIONS.md`, `STATE.json`에 반영한다.
2. 승인된 프로덕션 변경을 구현하고 회귀 테스트 3건 및 포화 정책 테스트를 통과시킨다.
3. 전체 lint·test·sampleDataClasses, 독립 리뷰, staged tree 검사를 다시 수행한다.
4. 2번 완료 기준이 충족된 뒤에만 상태를 `complete`로 바꾸고 게시 절차를 판단한다.

로컬 개발 DB, Redis 데이터, Gradle 산출물, IDE 설정, 로그, 사용자 제외 요청이 있는 `기능요구사항.pdf`는 이 체크포인트에 포함하지 않는다. Git hook과 역할·기준 문서는 이미 추적 중이다.
