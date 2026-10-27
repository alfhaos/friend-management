# 4번 단계 조사와 확정 계약

기준: `BACKEND_QUALITY_REVIEW.md` 20절 4번, `PLAN.md`, 사용자 결정 `DECISIONS.md`.
조사 기준 HEAD는 `8064337`이다. 3번은 게시됐고, 4번의 S4-001·
S4-PROD-APPROVAL·S4-002는 2026-10-27 사용자 답변 “마저 진행해”로 앞서 제시한
권장안대로 진행한다. 이 문서의 조사·기준선은 4번 구현 완료 증거가 아니다.

## 확인된 경계

- `FriendService`는 웹 요청 DTO `FriendRequest`와 문자열 `requestId`를 받는다.
- `FriendServiceImpl`과 `FriendRequestFacade`는 HTTP 상태를 가진 `CommResponseStatus`를
  직접 골라 `CommException`을 던진다.
- `UUID.fromString`은 서비스에서 실행된다. 잘못된 UUID는 현재
  `IllegalArgumentException` 경로로 `400 BAD_REQUEST`가 된다.
- Advice는 누락 헤더와 Bean Validation 실패에 대한 전용 처리가 없다. 추가 MockMvc
  관찰에서 `X-user-Id` 누락·0, `targetUserId` 누락·0, `window` 누락, `page=-1`은
  모두 `500 INTERNAL_SERVER_ERROR`였다. `X-user-Id=abc`와 `window=bad`는
  `400 BAD_REQUEST`였다. 이는 현재 동작의 관찰이며 향후 계약 승인은 아니다.
- 기존 컨트롤러 테스트 15건은
  `.\gradlew.bat test --tests "com.apr.aprbackendassignment.controller.*" --rerun-tasks --no-build-cache`
  에서 exit 0, 실패·오류·건너뜀 0건이었다. 이 테스트는 위 입력 실패 사례를 검증하지 않는다.
- 로컬 Postman/OpenAPI 계약 파일은 발견되지 않았다. 현재 공개 계약 근거는 컨트롤러,
  DTO, Advice, Swagger 주석, README 및 기존 API 테스트다.

## 제안 계약

1. 컨트롤러가 `FriendRequest.targetUserId`를 추출하고 `requestId`를 UUID로 확정한다.
   서비스와 Facade는 각각 `(Long actorId, Long targetUserId)`,
   `(Long actorId, UUID requestId)`를 받는다.
2. HTTP 비의존 내부 오류 코드·예외를 사용해 서비스와 Facade의 업무 실패를 표현한다.
   Advice만 이를 기존 `CommResponseStatus`에 1:1로 매핑한다.
3. 자기 신청, 중복, 권한, 상태, 친구 수 상한, Redis 락 경쟁·중단, DB 제약 409와 장애 503의
   공개 상태·코드·메시지·응답 구조를 유지한다. 공개 오타 코드
   `ALREADY_RREQUESTED_FRIENDSHIP`도 이번 단계에서 바꾸지 않는다.
4. 입력 실패는 공통 `400 BAD_REQUEST`로 통일한다. 데이터 이관,
   스키마 수정, 엔티티 전이·잠금·트랜잭션 변경은 이번 범위에 없다.

## 승인된 프로덕션 변경 범위

- 수정: `src/main/java/com/apr/aprbackendassignment/controller/FriendController.java`
- 수정: `src/main/java/com/apr/aprbackendassignment/service/FriendService.java`
- 수정: `src/main/java/com/apr/aprbackendassignment/service/impl/FriendServiceImpl.java`
- 수정: `src/main/java/com/apr/aprbackendassignment/util/FriendRequestFacade.java`
- 수정: `src/main/java/com/apr/aprbackendassignment/common/controllerAdivce/CommExceptionControllerAdvice.java`
- 수정: `src/main/java/com/apr/aprbackendassignment/common/request/PageRequestParam.java`
- 수정: `src/main/java/com/apr/aprbackendassignment/model/constant/SORT_GROUP.java`
- 수정: `src/main/java/com/apr/aprbackendassignment/model/constant/WINDOW_SLIDING.java`
- 신규: `src/main/java/com/apr/aprbackendassignment/common/exception/FriendshipError.java`
- 신규: `src/main/java/com/apr/aprbackendassignment/common/exception/FriendshipException.java`
- 신규: `src/main/java/com/apr/aprbackendassignment/common/exception/InvalidRequestException.java`
- 추가 승인: `src/main/java/com/apr/aprbackendassignment/common/interceptor/LimitInterceptor.java`

`CommResponseStatus`, `CommException`, 응답 DTO, 엔티티, 저장소, 스키마는
유지한다. `CommException`의 미사용 여부를 포함한 일반 정리는 5번으로 미룬다.

## 검증과 게시

- 테스트는 입력 오류의 상태·코드·문구·응답 필드, UUID 경계, 업무 오류 1:1 매핑,
  실패 시 DB 무변경, 기존 동시성·Redis 장애·409/503 회귀를 검증한다.
- 기준선에서는 추가 입력·업무 오류 테스트 7건이 기존 프로덕션 HEAD `8064337`에서
  통과했다. 이 중 1건은 기존 입력 오류 500/400을 관찰한 테스트이며 400 계약에 맞게
  갱신해야 한다.
- 2026-10-27 최종 입력 파싱·테스트 보완 뒤 작업 트리에서
  `.\gradlew.bat lint test sampleDataClasses --rerun-tasks --no-build-cache`는 exit 0,
  17개 suite·70건 실패·오류·건너뜀 0건이었다. 정확한 staged tree의 독립 코드·보안
  리뷰와 게시 게이트 검증은 별도로 수행한다.
- 최종 수정 필요 0건과 품질 검사 성공 후 `step-4-checkpoint`를 추가해 전용 브랜치에
  commit/push한다. 5~6번은 별개 미완료 단계다.

## 독립 리뷰 지적과 재현

- S4-REVIEW-01: 신청 경로의 `LimitInterceptor.java`가 컨트롤러 이전에 헤더를
  `Long.valueOf`로 파싱한다. 이번 Advice의 광범위한 `IllegalArgumentException` 400
  처리를 제거한 뒤 문자열·Long 범위 초과 `X-user-Id`가 500이 됐다. 기대값 400인
  회귀 테스트 두 건이 실패했다. 이 파일은 처음 승인 범위 밖이었으며 2026-10-27
  사용자 답변 “승인할게”로 좁은 헤더 검증·파싱 실패 수정이 승인됐다.
- S4-REVIEW-02: 기존 0 헤더 테스트는 잘못된 UUID와 함께 보내 검증 경로를 가렸다.
  유효한 신청 본문으로 바꾼 뒤 0 헤더만 400으로 처리됨을 확인했다. 테스트 수정은 완료했다.

2026-10-27 추가 승인에 따라 `LimitInterceptor`의 숫자 파싱 실패를
`InvalidRequestException`으로 변환했다. 잘못된 헤더 회귀 테스트와 정상 신청·429 테스트가
통과했고, 작업 트리의 `lint test sampleDataClasses`는 exit 0, 17개 suite·72건
실패·오류·건너뜀 0건이었다. 정확한 staged tree의 독립 재리뷰는 남아 있다.

예비 코드·보안 리뷰 S4-REVIEW-03은 숫자로 파싱 가능한 `0`과 음수가 제한기에 먼저
전달되는 경계를 지적했다. 반복 요청 테스트는 두 값 모두 11번째 신청에서 400 대신
429가 됨을 재현했다(11건 중 2건 실패). 양수 검증을 제한기 호출 전으로 옮긴 뒤
전체 `lint test sampleDataClasses`는 exit 0, 17개 suite·74건 실패·오류·건너뜀 0건이었다.
독립 코드·보안 재리뷰에서 S4-REVIEW-01·03을 포함한 수정 필요 항목은 0건이다.
정확한 staged tree의 최종 리뷰와 게시 게이트는 별도다.
