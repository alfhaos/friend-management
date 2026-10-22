# 목표 시스템
친구 관리 시스템 구축

사용자 인증 기능이 없기에 목록 조회 API는 기본적으로 사용자 아이디 (1L)을 기준으로 조회합니다.

## 🛠 기술 스택
- **언어**: Java 21
- **프레임워크**: Spring Boot 3.5.9
- **데이터베이스**: PostgreSQL 16 (Docker Compose)
- **동시성/캐시**: Redis 7.4.2 (Redisson)
- **빌드 도구**: Gradle 8.6
- **ORM**: Spring Data JPA / Hibernate


## 🚀 아키텍처 구성도
![architecture.png](architecture.png)
## 🚀 핵심 설계 전략 및 문제 해결

### 1. 동시성 제어: Redis 분산 락 & Facade 패턴
* **문제 배경**: 현재 DB 유일 제약은 요청자·수신자 순서가 같은 관계만 막습니다. A→B와 B→A는 서로 다른 키라 동시 신청 시 양방향 중복을 막을 별도 제어가 필요합니다.
* **해결 전략**: Redisson의 분산 락을 도입하여 동일 사용자 쌍의 요청을 직렬화했습니다.
* **Facade 패턴 적용**: `@Transactional`은 메서드 종료 후 커밋을 수행하므로, 락 해제가 커밋보다 먼저 일어나 정합성이 깨지는 것을 방지하기 위해 락의 범위를 트랜잭션 외부로 감싸는 Facade 레이어를 별도로 구축했습니다.


### 2. 요청 제한(Rate Limit): Redis 기반 Token Bucket
* **해결 전략**: Redis의 `RRateLimiter`를 사용하여 Token Bucket 기반 요청 제한을 구현했습니다.
* **설계**: 단일 서버에 종속적인 인메모리 방식과 달리, 분산 서버 환경에서도 모든 인스턴스가 동일한 요청 카운트를 공유하여 일관된 정책 적용이 가능합니다.


### 3. 데이터 모델링 및 조회 최적화
* **단일 레코드 양방향 구조**: 친구 관계를 (A, B) 단일 레코드로 저장하여 저장 공간을 효율화하고, 조회 시 `requester`와 `receiver`를 동시에 탐색하여 논리적인 양방향 관계를 구현했습니다.
* **상태 관리**: 친구 관계의 상태를 `REQUESTED`, `ACCEPTED`, `REJECTED`로 구분하여 다양한 비즈니스 로직을 명확히 처리할 수 있도록 설계했습니다.

## 📋 API 명세 요약
| 기능 | 메서드 | 엔드포인트 | 특징 |
|:--- |:---:|:--- |:--- |
| 친구 목록 조회 | `GET` | `/api/friends` | ACCEPTED 상태인 관계만 페이징 조회 |
| 받은 요청 목록 | `GET` | `/api/friends/requests` | 기간 필터(1d, 7d, 30d 등) 지원 |
| 친구 신청 | `POST` | `/api/friends/request` | 분산 락 및 Rate Limit 적용 |
| 요청 수락 | `POST` | `/api/friends/accept/{requestId}` | 수락 전 최대 친구 수 검증 |
| 요청 거절 | `POST` | `/api/friends/reject/{requestId}` | REQUESTED 관계를 REJECTED로 변경 |


## 📋 API 명세 상세 정리
- 친구 목록 조회:
  - 친구 관계 상태가 ACCEPTED인 항목만 페이징 처리하여 반환.
  - response의 'user_id' 필드에는 친구의 사용자 ID 응답, from_user_id 및 to_user_id는 각각 발신자, 수신자로 구분하였습니다.
  - approved_at 응답 필드는 updated_time과 동일한 값입니다.
  - 현재 `approvedAt` 정렬 키는 관계의 생성 시각을 사용합니다. 실제 수락 시각 정렬은 리뷰 3번 단계의 개선 대상입니다.
  
- 받은 요청 목록:
  - `window` 쿼리 파라미터로 기간 필터 적용(1d, 7d, 30d, 90d, over).
  - 페이징 파라미터는 page(0-based), maxSize(1~100), sort(field,dir) 형식으로 받습니다.
  - response의 `request_id`는 Friendship의 UUID, `request_user_id`는 요청자 ID, `requestedAt`은 created_time 값입니다.
  - `window=over`일 때는 기간 제한 없이 전체 요청을 조회합니다.
  
- 친구 신청:
  - 요청 헤더 `X-user-Id`와 body의 `targetUserId`로 요청자/대상자를 구분합니다.
  - 현재는 두 사용자 사이에 관계가 이미 있으면 상태와 무관하게 오류를 반환합니다. 거절 후 재신청 허용은 리뷰 2번 단계의 개선 대상입니다.
  - 분산 락으로 동시 요청 중복 생성 방지, 요청당 Rate Limit(기본: 1초 10회)이 적용됩니다.
  - 자기 자신에게 요청한 경우 에러를 반환합니다.
  
- 요청 수락:
  - 요청 헤더 `X-user-Id`는 수신자여야 하며, 본인 수신 요청만 처리 가능합니다.
  - REQUESTED 상태에서만 수락 가능하며, 수락 시 상태가 ACCEPTED로 변경됩니다.
  - 수락 전 요청자/수신자 모두 친구 수 제한(max-friend) 검증을 수행합니다.
  
- 요청 거절:
  - 요청 헤더 `X-user-Id`는 수신자여야 하며, 본인 수신 요청만 처리 가능합니다.
  - REQUESTED 상태에서만 거절 가능하며, 거절 시 상태가 REJECTED로 변경됩니다.
  - 현재는 거절된 관계가 남아 있으면 동일 사용자 간 재신청도 오류를 반환합니다.

## 🛠 실행 방법

1. **PostgreSQL 실행** (프로젝트 루트):

   ```bash
   docker compose up -d --build --wait
   docker compose ps
   ```

   공식 PostgreSQL 이미지를 사용하므로 별도 Dockerfile 빌드는 필요하지 않습니다.
   기본 접속 정보는 `localhost:5432`, DB `apr`, 사용자 `apr`, 비밀번호 `apr_local_password`입니다.
   비밀번호는 로컬 개발용 기본값입니다. `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` 환경변수로 변경할 수 있습니다.
   변경한 환경변수는 Compose와 애플리케이션 실행 환경 모두에 동일하게 전달해야 합니다.
   Compose의 `.env` 파일은 애플리케이션에서 자동으로 읽지 않습니다.
   데이터는 프로젝트 전용 `postgres-data` 볼륨에 보관되며, `docker compose down`으로 컨테이너를 종료해도 유지됩니다.
   DB 이름과 계정 환경변수는 볼륨을 처음 초기화할 때 적용됩니다. 기존 볼륨의 계정은 환경변수만 바꿔도 변경되지 않습니다.

2. **Redis 준비**: 애플리케이션은 `localhost:6379`의 Redis를 사용합니다. 이번 Compose에는 PostgreSQL만 포함되어 있습니다.

3. **애플리케이션 실행**:

   ```bash
   ./gradlew bootRun
   ```

   Windows PowerShell에서는 `.\gradlew.bat bootRun`을 사용합니다.
   현재 `ddl-auto: update` 설정으로 엔티티에서 PostgreSQL 테이블을 생성·갱신합니다.
   `sql/schema.sql`은 기존 H2용 참고 스키마이며 이 Compose에서는 실행하지 않습니다. 기존 H2 데이터는 자동으로 이전되지 않습니다.

Swagger UI: http://localhost:8080/swagger-ui.html

## 테스트 실행

Docker가 실행 중인 상태에서 프로젝트 루트에서 실행합니다.

```powershell
.\gradlew.bat test
```

테스트는 Testcontainers로 PostgreSQL 16과 Redis 7.4.2를 임의의 호스트 포트에서 실행합니다.
개발용 Compose나 기존 사용자·친구 데이터는 필요하지 않습니다. 최초 실행에는 테스트 의존성과 Docker 이미지 다운로드가 필요합니다.
모든 통합 테스트는 `IntegrationTestSupport`를 상속하며, `test` 프로필의 접속 정보를 해당 컨테이너로 지정합니다.
컨테이너를 시작하지 못하면 테스트가 실패하며 개발 DB로 대체 접속하지 않습니다.

각 테스트 전후에 전용 DB의 테이블과 Redis DB를 초기화합니다. 전체 테스트를 감싸는 트랜잭션은 사용하지 않으므로
Facade의 실제 서비스 커밋과 작업 스레드의 결과를 별도 트랜잭션에서 확인합니다.
공유 테스트 환경의 정리를 위해 테스트는 순차 실행합니다. 컨테이너는 재사용하지 않으며 테스트 JVM 종료 시 Testcontainers가 정리합니다.
조회 API가 사용자 ID `1`을 사용하는 현재 규약은 테스트 DB의 identity 초기화 후 첫 사용자 fixture로 재현합니다.
변경 API 테스트는 fixture 생성 시 반환된 ID와 UUID를 사용합니다.

클래스나 메서드 하나만 실행할 수도 있습니다.

```powershell
.\gradlew.bat test --tests '*FriendServiceImplTest'
.\gradlew.bat test --tests '*FriendServiceImplTest.concurrentOppositeRequestsCreateExactlyOneRelationship'
```

요청 제한 HTTP 테스트는 MockMvc와 실제 Redis를 사용합니다. 시간 경과로 토큰이 다시 생기는 영향을 줄이기 위해
테스트 프로필의 제한 시간 창만 60초로 설정하고, 허용 횟수·초과 응답·사용자 간 격리를 검증합니다.
기본 1초 창의 만료 경계와 수락·거절 경쟁 및 친구 수 상한 동시성은 후속 검증 대상입니다.
결과 보고서는 `build/reports/tests/test/index.html`에 생성됩니다.

개발용 샘플 생성은 검증 테스트와 별도 source set으로 분리했습니다. 아래 명령은 **현재 설정된 개발 DB에 데이터를 추가**하며,
DB와 Redis를 준비한 뒤 필요한 경우에만 명시적으로 실행합니다.

```powershell
.\gradlew.bat seedSampleData --args='100'
```

기본값은 100건이며 1~10,000건을 지정할 수 있습니다. 새 수신자 1명과 요청자·요청을 만들고,
요청 시각을 0·6·29·89·120일 전으로 나눕니다. 생성된 수신자 ID는 로그에서 확인할 수 있습니다.
이 명령은 실행할 때마다 데이터를 추가하며 고정 사용자 ID나 기존 데이터에 의존하지 않습니다.

## 리팩토링 에이전트 실행

리팩토링 브랜치는 `refactor/backend-quality`입니다. [AGENTS.md](AGENTS.md)를 공통 진입점으로 사용하고,
[실행 체인](agent-context/WORKFLOW.md), [1~6단계 계획](agent-context/PLAN.md),
[진행 상태](agent-context/STATE.json)를 통해 다음 세션에 작업을 이어갑니다.
현재 체크포인트는 1번 테스트 기반과 에이전트 실행 환경 구성입니다. 2~6번은 단계별 검증과 리뷰를 거쳐 진행합니다.

프로젝트의 `.codex/agents/`에는 오케스트레이터, DDD 설계, 백엔드 구현, 테스트, 코드 리뷰, 보안 리뷰, 문서 관리 역할이 정의되어 있습니다.
모델과 권한은 부모 세션에서 상속합니다. 정의 형식은 [공식 Codex 서브에이전트 문서](https://learn.chatgpt.com/docs/agent-configuration/subagents)를 따릅니다.
현재 세션에 새 역할이 보이지 않으면 기본 에이전트에 해당 `agent-context/roles/` 문서와 소유 파일을 전달합니다.

다음 작업의 시작 프롬프트:

```text
AGENTS.md와 agent-context/WORKFLOW.md를 읽고 refactor_orchestrator를 중심으로
BACKEND_QUALITY_REVIEW.md 20절의 1~6단계를 현재 STATE.json에서 재개해.
완료한 1번은 회귀 검증하고, 단계별 설계→구현·테스트→검증→독립 리뷰를 진행해.
리뷰 지적은 담당 전문 에이전트에 재배정해 수정하고 다시 리뷰해.
모든 단계가 완료되고 최종 리뷰의 수정 필요가 0건이면 필수 린트·테스트를 거쳐
현재 refactor/backend-quality 브랜치에 자동으로 커밋하고 origin에 일반 push해.
```

새 체크아웃에서는 Git 훅을 한 번 설치합니다.

```powershell
powershell -NoProfile -File scripts/install-git-hooks.ps1
.\gradlew.bat lint
```

`pre-commit` 훅이 린트를 실제 실행하고 실패한 커밋을 차단합니다. 자동 게시 절차는
`scripts/publish-reviewed.ps1`에서 독립 리뷰 승인과 검토한 staged tree를 확인한 뒤
검증·커밋·푸시를 순서대로 수행합니다. 상세 실행법과 재검토 조건은 실행 체인을 따릅니다.

## 🔍 Troubleshooting
이슈: Service 레이어 내에서 @Transactional과 락을 함께 사용 시, 락 해제 후 커밋 전 찰나의 순간에 중복 요청이 통과되는 현상 발견.

현재 구현: 락 획득/해제 로직을 트랜잭션이 없는 Facade 레이어로 분리하여 신청 경로를 [락 획득 -> 서비스 트랜잭션 시작 -> 커밋 -> 락 해제] 순서로 처리합니다. 수락·거절 상태 충돌과 사용자별 친구 수 상한 경쟁은 리뷰 2번 단계의 검증·개선 대상입니다.
