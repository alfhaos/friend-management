# 5번 단계 조사와 변경 승인안

기준: `BACKEND_QUALITY_REVIEW.md` 20절 5번, `PLAN.md`, 현재 HEAD `2b0af40`.
이 문서는 DDD·테스트 조사, 승인된 변경 범위와 구현·검증 증거를 함께 기록한다. 최종
staged tree 독립 리뷰와 게시 증거는 아직 아니다.

## 확인한 현상과 유지할 계약

- 신청 관계 중복 방지·친구 수 집계와 사용자 잠금은 2번에서 정리됐다. 현재
  `RedisRateLimiter.allow()`에는 전역 `synchronized`가 없다. 로컬 슬롯 생성·회수의
  `ReentrantLock`은 10,000 슬롯 상한을 원자적으로 지키므로 유지한다.
- `findParticipantIds → 사용자 행 잠금 → findById`는 잠금 전 참여자 식별과 잠금 후
  현재 관계 재조회에 각각 필요하다. 선행 사용자 존재 조회도 오류 우선순위를 지킨다.
  목록 조회·친구 수 집계 역시 목적이 달라 단순 쿼리 중복으로 삭제하지 않는다.
- `LimitProperties`는 친구 수 상한과 요청 횟수·창·경로를 한 클래스에 묶고,
  0·음수 설정을 시작 시 거절하지 않는다. `WebConfig`는 모든 경로에 인터셉터를
  등록하고, 인터셉터가 다시 신청 경로를 고른다.
- `UsersDto`와 `WindowResponse`는 저장소의 프로덕션·테스트·샘플 코드에 호출자가 없다.
  `FriendsResponse.status`는 Java/OpenAPI 모델에 있으나 현재 런타임 JSON에는 빠진다.
  공개 스키마 변경의 별도 결정 없이 이 필드는 유지한다.
- 기존 신청·수락·거절, UUID, 잠금·트랜잭션, DB 409/503, 요청 제한 429 및
  누락·잘못된 입력 400 계약을 유지한다. 데이터·스키마 이관은 없다.
- 2026-10-27 실제 테스트 Redis의 특성화 테스트에서 같은 제한 키에 최초 rate 1을
  설정한 뒤 rate 2로 `trySetRate`를 다시 호출하면 `false`였고 허용 횟수도 1회로
  유지됐다. 현재 Redis 제한 키는 설정 변경 후 자동 갱신되지 않는다.

## 승인된 프로덕션 변경: S5-PROD-APPROVAL

목적은 설정의 소유자를 분리하고 잘못된 설정을 시작 시 차단하며, 웹 경로 선택을
한곳에서 수행하는 것이다. 대상과 예상 동작은 다음과 같다.

| 대상 파일 | 변경 목적과 예상 동작 |
|---|---|
| `src/main/java/com/apr/aprbackendassignment/util/LimitProperties.java` | 요청 횟수·창·적용/제외 경로만 보유하고 필수 경로와 양수 설정을 시작 시 검증한다. 기존 `limit.*` 키를 유지한다. |
| `src/main/java/com/apr/aprbackendassignment/util/FriendLimitProperties.java` (신규) | 기존 `limit.max-friend`를 별도 빈으로 바인딩하고 양수 값을 시작 시 검증한다. |
| `src/main/java/com/apr/aprbackendassignment/service/impl/FriendServiceImpl.java` | 친구 수 상한 조회만 새 친구 정책 설정에 의존한다. 상한 판단과 오류는 그대로다. |
| `src/main/java/com/apr/aprbackendassignment/config/WebConfig.java` | 설정된 신청 경로에만 인터셉터를 등록하고 기존 제외 경로 설정은 유지한다. |
| `src/main/java/com/apr/aprbackendassignment/common/interceptor/LimitInterceptor.java` | 중복 경로 비교를 제거하고 POST·헤더 검증·제한 및 429 응답만 담당한다. 세미콜론 별칭과 정상 경로 동작은 회귀 테스트로 확인한다. |
| `src/main/java/com/apr/aprbackendassignment/model/dto/UsersDto.java`, `src/main/java/com/apr/aprbackendassignment/common/response/WindowResponse.java` (삭제) | 저장소에 호출자가 없는 타입만 제거한다. HTTP 응답 형식은 바꾸지 않는다. |

`RedisRateLimiter`, repository의 쿼리, `FriendsResponse.status`, 설정 파일의 기존 키는
변경하지 않는다. S5-REDIS-POLICY 답변 “그대로둬”에 따라 Redis 기존 키/예산 정책도
유지한다.

## 검증과 결과

- `LimitPropertiesBindingTest`는 기존 `limit.*` 키의 분리 바인딩과 `max-friend`,
  `max-requests`, `window-milli-seconds`의 0/음수 및 빈 `paths`에서 컨텍스트 시작이
  실패함을 확인했다. 단독 실행은 exit 0, 8건 통과였다.
- `FriendRequestApiTest`, `FriendRequestRedisOutageApiTest`,
  `FriendInputAndBusinessApiTest`는 정상 429, 세미콜론 경로 별칭, 잘못된 헤더 400을
  확인했다. 단독 실행은 exit 0, 3개 suite·16건 통과였다.
- `RedisRateLimiterConfigurationTest`는 Redis 기존 키의 최초 예산 유지,
  `RedisRateLimiterConcurrencyTest`는 서로 다른 사용자 Redis 호출의 동시 진입을
  각각 1건으로 확인했다.
- 2026-10-28 전체 작업 트리에서
  `.\\gradlew.bat lint test sampleDataClasses --rerun-tasks --no-build-cache`가 exit 0으로
  성공했다. 20개 suite·84건에 실패·오류·건너뜀이 없었고 9개 task를 실행했다.
  Docker 기반 통합 테스트를 포함한 이 결과는 아직 staged tree 승인 증거는 아니다.

## 사용자 결정

- S5-DTO-DELETE: 사용자 답변 “삭제 승인”으로 두 DTO 삭제 확정.
- S5-PUBLISH: 사용자 답변 “바로 게시”로 5번 검증·독립 리뷰 후
  `step-5-checkpoint` 추가·게시 확정.
- S5-REDIS-POLICY: 사용자 답변 “그대로둬”로 기존 Redis 키 정책 유지 확정.
- S5-PROD-APPROVAL: 첫 답변 “삭제 승인”의 범위를 다시 질문한 뒤 사용자 답변
  “다 승인한다고”로 나머지 5개 프로덕션 파일 생성·수정도 승인됐다.

승인된 두 미사용 DTO는 삭제했고, 설정·인터셉터 변경도 구현했다. `compileJava`가 통과했다. 5번 게시 게이트는
`step-5-checkpoint`와 `[1,2,3,4,5]`를 검사하며 smoke 테스트가 통과했다. 실제 Redis
키 설정 보존 테스트 1건과 서로 다른 사용자 요청의 Redis 동시 진입 테스트 1건이 각각
통과했다. 이전 부분 작업 트리의 `lint test sampleDataClasses --rerun-tasks --no-build-cache`도
exit 0, 19개 suite·76건 실패·오류·건너뜀 0건이었다. 이후 전체 변경을 포함한 2026-10-28
실행은 20개 suite·84건에서 실패·오류·건너뜀 0건으로 다시 검증했다.

정확한 staged tree의 독립 코드·보안 리뷰와 `step-5-checkpoint` 게시가 남아 있다.
