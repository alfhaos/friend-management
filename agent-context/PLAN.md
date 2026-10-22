# 단계별 리팩토링 계획

기준: `BACKEND_QUALITY_REVIEW.md` 20절. 현재 체크포인트는 실행 체계 구성과 1번 구현
보존·재검증이다. **2~6번 구현은 아직 시작하지 않았다.** 아래 표는 앞으로 수행할 계약이며
완료 보고가 아니다. 상태의 기계 판독 기준은 `STATE.json`, 근거는 실제 검증/리뷰 기록이다.

경로는 `src/main/java/com/apr/aprbackendassignment/`를 기준으로 적는다. 소유권은 단계가
시작될 때 실제 파일 목록으로 확정하며 한 파일을 여러 구현 에이전트에 동시에 맡기지 않는다.

| 순서 | 원문 작업 / 선행 조건 | 역할·파일 경계 | 완료 기준 |
|---|---|---|---|
| 1 — P1 기반 | 테스트 소스 실행 가능 상태와 독립 fixture 확보 | test_engineer: `src/test/**`, `src/sampleData/**`; backend: PostgreSQL 조회 보정; 품질 담당: `build.gradle` | 개발 데이터 없이 `clean test sampleDataClasses` 성공. 테스트/개발 생성기 분리, 전용 인프라와 데이터 정리, 동시성 결과 수집 유지 |
| 2 — P1 | 현재 신청 제어 흐름의 중복·도달성 정리, 수락·거절 잠금 규약 적용 / 1의 검증 기반 | ddd_architect: 재신청·중복 처리·잠금 계약; backend: `service/impl/FriendServiceImpl.java`, `service/FriendService.java`, `repository/{Friendship,Users}JPARepository.java`, `util/{FriendRequestFacade,RedisLockUtil}.java`; test: `src/test/**` | 거절 후 같은/반대 방향 재신청, 신청 중복 방지, 동일 요청 수락/거절·중복 수락 경쟁, 공유 사용자의 서로 다른 요청 동시 수락 시 상한 보장. 실패 시 부분 반영 없음 |
| 3 — P2 | 엔티티 생성·상태 전이 메서드, 업무 시각 정리 / 2의 동시성·정책 계약 | ddd: 상태 전이표·식별·시각; backend: `model/entity/Friendship.java`, `model/constant/FRIENDSHIP_STATUS.java`, 서비스 행위 호출, 시각 조회·정렬·DTO, 필요 스키마; test: 도메인/통합 테스트 | 임의 상태/참여자 변경 대신 신청·수락·거절·재신청 행위가 규칙 보호. 자기 신청·수신자 권한·허용 상태 검사. 업무 시각과 감사 시각 구분 및 고정 시간 경계·실제 수락순서 검증 |
| 4 — P2 | 서비스와 HTTP 오류·입력 타입 분리 / 3의 업무 오류 계약 | backend: 서비스, `controller/FriendController.java`, `common/controllerAdivce/CommExceptionControllerAdvice.java`, `common/exception/**`, `common/response/**`, `common/request/PageRequestParam.java`, 관련 DTO/enum; test: API 경계 테스트 | 서비스가 HTTP 응답 상태를 선택하지 않음. UUID는 API 경계에서 검사. 입력/업무/충돌/시스템 오류 매핑 구분. 잘못된 UUID·누락 헤더·권한·상태 오류의 코드와 응답 필드 확인 |
| 5 — P2 | 중복 쿼리·미사용 의존성·설정 책임 정리 / 4의 소유 경계 | backend: repository/서비스 중복, `util/{LimitProperties,RedisRateLimiter}.java`, `common/interceptor/LimitInterceptor.java`, `config/WebConfig.java`, 미사용 DTO; test: 제한·설정·조회 회귀 | 규칙·조회에 명확한 소유자. 중복 조회 제거 시 존재 검사/잠금 목적 보존. 양수 설정 시작 시 검증. 사용자 간 격리·만료·경쟁 검증 후 전역 synchronized 제거 여부 결정 |
| 6 — P3 | 도메인별 패키지·이름·주석·문서 정리 / 1~5 검증 후 | backend: 프로덕션 패키지 이동/참조 단독 소유; test: 테스트 import/패키지 대응; docs: README/리뷰 실행 이력·설계 문서. 로그 설정/샘플 mainClass도 조정 | 실제 책임에 맞는 코드 위치·이름·주석·문서. 동작 변경과 대규모 이동을 한 패치에 섞지 않음. lint·전체 테스트·sampleDataClasses 성공 및 최종 독립 리뷰 수정 필요 0건 |

단계 2와 3은 필요한 작은 계약 변경만 묶을 수 있다. 이 경우 두 단계의 완료 기준을
각각 증명한다. 패키지 이동부터 시작하지 않는다. API 공개 계약 변경은 별도 근거와
명시적인 계약 합의가 있을 때만 수행하고, 단순 내부 리팩토링으로 응답을 바꾸지 않는다.

## 1번 기존 증거와 아직 검증하지 않은 규칙

리뷰 문서 21절은 독립 PostgreSQL 16·Redis 7.4.2 Testcontainers, `IntegrationTestSupport`,
`FriendshipFixture`, 고정 개발 ID/`@Rollback(false)` 의존 제거와 샘플 생성기 분리를 기록한다.
이전 실행에서 `test sampleDataClasses`, `clean test sampleDataClasses` 각각 17건 통과와
요청 제한 메서드 단독 실행이 성공했다. 이번 설정 변경 이후 검증은 별도로 실행한다.

수락/거절 경쟁, 친구 수 상한 경쟁, 재신청, 시간 경계·기본 1초 제한 창의 만료는 후속 범위다.
현재 `listsOnlyCurrentUsersAcceptedFriendsInCreatedTimeOrder`는 `createdTime` 기반 동작을
검증하므로 실제 수락시각 정렬 완료의 근거가 아니다. 3번에서는 신청순서와 수락순서가
반대인 fixture로 업무 시각 기준을 검증해야 한다.

## DDD 책임과 과설계 방지

- `Friendship`: 자기 신청·수신자 권한·허용 상태 전이와 관계 수명주기.
- 작은 정책/규칙: 두 사용자의 친구 수 상한. 여러 관계에 걸친 규칙이라 관계 한 행의
  잠금만으로 동시 상한을 보장했다고 판단하지 않는다.
- 애플리케이션 서비스: 사용자/관계 조회, 잠금 대상과 순서, 커밋 범위, 유스케이스 조정.
- API 경계: 파싱·입력 검증·업무 오류의 HTTP 표현. repository가 HTTP 상태를 정하지 않는다.
- JPA 엔티티를 도메인 모델로 유지해도 된다. 순수 모델 일대일 복제·단순 전달 저장소 래퍼·
  이벤트 소싱·마이크로서비스·별도 읽기 DB를 추가할 근거는 현재 없다.
- 사용자 행 잠금은 일관성 전략이며 모든 관계를 Users 애그리거트 안으로 옮길 근거가 아니다.
- 성능·SQL 수·운영 보장 수치는 실제 측정과 실행 근거 없이 주장하지 않는다.

## 단계별 반환물

오케스트레이터는 단계마다 확정 계약, 소유 파일, 변경 요약, 테스트 명령/exit code,
리뷰 지적과 해소 증거, 남은 범위를 기록한다. 리뷰 지적의 기본 필드는
`ID / 심각도 / 파일·위치 / 재현·근거 / 기대 규칙 / 담당 / 재검증 / 상태`다.
설계 불명확은 DDD, 구현 결함은 backend, 검증 부족은 test, 검사 도구는 품질 담당으로
돌려보내고 독립 리뷰를 다시 받는다. 전체 게시 규칙은 `WORKFLOW.md`를 따른다.
