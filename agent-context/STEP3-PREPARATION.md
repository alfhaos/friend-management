# 3번 단계 사전 조사와 확정 계약

기준: `BACKEND_QUALITY_REVIEW.md` 20절 3번, `agent-context/PLAN.md`,
`DECISIONS.md`의 S2 결정. 조사 기준 HEAD는 `0d84c78`이다. 2번은 게시됐고 3번은
미완료다. S3-001, S3-002, S3-PROD-APPROVAL은 2026-10-26 사용자 답변으로
확정·승인됐다. 아래 계약은 구현과 검증이 완료됐다는 뜻이 아니다.

## 확인된 현행 동작

| 경계 | 코드 근거 | 확인 사항 |
|---|---|---|
| 관계 생성·변경 | `model/entity/Friendship.java` | 호출자가 임의 상태로 생성하고 상태·참여자·요청 시각을 별도 공개 메서드로 바꿀 수 있다. |
| 신청·재신청 | `service/impl/FriendServiceImpl.java` | 서비스가 자기 신청을 거부한다. 같은 방향 `REJECTED` 재신청은 UUID와 방향을 유지하고 `requestedAt`을 새 시각으로 바꾼다. |
| 수락·거절 | `service/impl/FriendServiceImpl.java` | 수신자와 `REQUESTED` 상태를 서비스가 검사한다. 두 사용자 잠금·친구 수 상한·트랜잭션 조정은 서비스 책임이다. |
| 친구 목록 시각 | `model/dto/response/FriendsResponse.java`, `model/constant/SORT_GROUP.java` | 응답 `approvedAt`은 `updatedTime`, 요청 정렬 키 `approvedAt`은 `createdTime`에 연결된다. 실제 수락 시각은 저장하지 않는다. |
| 요청 목록 시각 | `model/entity/Friendship.java`, `repository/FriendshipJPARepository.java` | `requestedAt` 업무 컬럼과 기존 행의 `createdTime` 읽기 fallback이 있다. |

로컬 OpenAPI·Postman 계약 파일은 발견되지 않았다. 현재 공개 계약은 컨트롤러,
DTO, Swagger 주석과 README를 기준으로 확인한다. 필드 이름을 유지하더라도 값과
정렬 의미 변경은 공개 API 결정 S3-001에 따라 승인됐다.

## 확정된 상태 전이 계약

| 행위 | 선행 조건 | 결과 |
|---|---|---|
| 최초 신청 | 두 사용자가 존재하고 서로 다름 | 새 UUID, `REQUESTED`, `requestedAt=now`, 승인 시각 없음 |
| 같은 방향 재신청 | 같은 요청자의 기존 `REJECTED` 행 | UUID·방향 유지, `REQUESTED`, `requestedAt=now`, 승인 시각 없음 |
| 수락 | 수신자가 `REQUESTED`를 처리 | `ACCEPTED`, 승인 업무 시각=`now` |
| 거절 | 수신자가 `REQUESTED`를 처리 | `REJECTED`, 승인 시각 없음 |
| 그 외 | 권한 또는 상태 불일치 | 기존 공개 오류 계약으로 실패, 필드 변경 없음 |

엔티티는 한 관계의 생성·권한·상태 전이·업무 시각을 보호한다. 서비스는 두 사용자
잠금, 여러 관계에 걸친 친구 수 상한, 트랜잭션과 조회를 유지한다. 기존 S2-001,
S2-002, S2-007의 요청 ID·방향·요청 시각 결정은 그대로 따른다. 4번의 HTTP 오류
계층 분리, 5번의 일반 쿼리 정리, 6번의 패키지 이동은 이 단계의 승인 제안에 없다.

## 사용자 결정과 승인

- S3-001: 공개 `approvedAt` 필드·정렬 키 이름은 유지하고 실제 수락 시각을 반환·정렬한다.
- S3-002: 사용자는 현재 DB가 비어 있다고 밝혔다. 기존 행의 `updated_time` fallback이나
  일괄 이관은 구현하지 않는다. 새 수락 시 `approved_at`을 기록하고 일반 수정 시각은
  감사 필드 `updatedTime`이 관리한다. DB 공백은 사용자 진술이며 이 문서의 실측 결과가 아니다.
- S3-PROD-APPROVAL: 아래 파일·예상 동작의 프로덕션 변경을 사용자가 승인했다.

`approved_at`은 신청·거절 상태에서 nullable이다. 수락 후에는 해당 수락 행위의
업무 시각을 저장한다. `updatedTime`을 승인 시각으로 읽지 않는다.

## 승인된 파일 소유권

- 프로덕션: `model/entity/Friendship.java`, `service/impl/FriendServiceImpl.java`,
  `model/dto/response/FriendsResponse.java`, `model/constant/SORT_GROUP.java`,
  `model/constant/WINDOW_SLIDING.java`, 신규 `config/TimeConfig.java`,
  `src/main/resources/schema.sql`, `src/sampleData/java/com/apr/aprbackendassignment/sampleData/SampleDataInsert.java`.
  구현 담당 에이전트의 소유 경로로 고정했다.
- 테스트·fixture: 신규 순수 도메인 테스트, `support/FriendshipFixture.java`,
  `service/impl/FriendServiceImplTest.java` 및 영향을 받는 기존 회귀 테스트.
  프로덕션과 같은 Java 파일을 병렬 수정하지 않는다.

## 검증 시나리오

- 자기 신청·수신자 권한·불법 전이와 실패 시 무변경을 순수 도메인 테스트로 검출한다.
- 같은 방향 재신청의 UUID·방향 유지와 새 `requestedAt`을 고정 시각으로 검증한다.
- 신청 순서와 수락 순서를 반대로 배치해 `approvedAt,desc` 응답 시각과 정렬을
  PostgreSQL 통합 테스트로 검증한다. 과거 행 fallback은 요구하지 않는다.
- 요청 목록의 기간 시작 직전·정확한 경계·직후와 기존 친구 수·동시성 회귀를 유지한다.
- 2026-10-26 작업 트리와 3번 구현 staged tree에서 각각
  `.\gradlew.bat lint test sampleDataClasses --rerun-tasks --no-build-cache`를 실행해
  exit 0, 63건 실패·오류·건너뜀 0건을 확인했다. 코드·보안 독립 리뷰에서 필수 수정
  0건이었다. 게시 게이트 문서를 포함한 최종 tree는 별도 독립 리뷰를 받는다.

읽기 전용 사전 조사에서 관련 기존 테스트 19건은 exit 0으로 통과했다고
`step3_orchestration` 에이전트가 보고했다. 이는 3번 구현의 통과 근거가 아니다.
