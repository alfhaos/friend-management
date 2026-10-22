# 오케스트레이터 실행 절차

이 문서는 `BACKEND_QUALITY_REVIEW.md` 20절 1~6번을 수행할 재사용 가능한 컨텍스트다.
이미 확정된 commit/push 권한을 매번 재질문하지 않는다. 게시 시점처럼 아직
답변을 받지 않은 선택은 별도 결정으로 다룬다. 브랜치는 `refactor/backend-quality`이며
단계는 의존성 순서로 진행한다.

## 사용자 결정 게이트

1. DDD·테스트 에이전트는 확인된 요구사항과 미확정 선택을 분리한다. 신청 재처리 정책,
   요청 ID 수명, 잠금 실패 정책, HTTP 오류 변경, 스키마 이전, 작업·게시 범위처럼
   제품 의미나 사용자 자산에 영향을 주는 선택은 오케스트레이터에게 질문 후보로 반환한다.
2. 오케스트레이터는 저장소·요구사항·이전 사용자 답변에서 결론이 명시됐는지 확인한다.
   없으면 선택지와 영향을 짧게 정리해 사용자에게 질문하고 `DECISIONS.md`와
   `STATE.json.pendingDecisions`에 남긴다. 임의의 기본값을 선택하지 않는다.
3. 답변 전에는 해당 선택에 의존하는 계약 확정·구현·최종 승인·게시를 진행하지 않는다.
   다른 단계의 읽기·기존 동작 검증처럼 독립적인 작업은 계속한다. 답변이 늦어져도
   승인으로 간주하지 않는다.
4. 답변이 오면 원문 취지·결정·영향받는 단계·날짜를 기록하고, 관련 전문 에이전트에
   새 계약을 전달한다. 답이 충돌하거나 불명확하면 구체적으로 다시 질문한다.
5. 독립 리뷰어는 미해결 결정이 있거나 답변과 구현이 다르면 `changes-required`로 판정한다.
   게시 스크립트도 현재 scope를 막는 미해결 결정을 확인해 중단한다.

## 0. 재개와 범위 확정

1. `AGENTS.md`, `PLAN.md`, `STATE.json`, 현재 역할 문서를 읽는다.
2. 현재 브랜치, `git status --short`, staged/unstaged diff, 사용자 소유 파일을 확인한다.
   기존 변경을 버리지 않는다. 사용자가 Git 제외를 지정한 `기능요구사항.pdf`는 stage하지 않는다.
3. `powershell -NoProfile -File scripts/install-git-hooks.ps1`를 실행한다.
   `git config --get core.hooksPath`가 `.githooks`인지 확인한다.
4. 이번 범위를 `setup-checkpoint` 또는 `full-refactor`로 정하고 상태에 기록한다.
   게시 시점은 사용자 답변으로 확정한다. 설정 체크포인트와 후속 2~6번의 완료 상태는
   각각 별도로 기록한다.

## 1. 단계별 체인과 병렬 경계

```text
refactor_orchestrator
  ├─ ddd_architect: 불변조건·상태·트랜잭션·계약 초안 (읽기 위주)
  └─ test_engineer: 실패 사례·경계·경쟁 시나리오 초안 (읽기 위주)
       ↓ 오케스트레이터가 계약·담당 파일 확정
  ├─ backend_developer: 프로덕션 소유 파일 구현
  └─ test_engineer: 별도 테스트 소유 파일 구현
       ↓ 결과 통합 → 실제 lint/test
  ├─ code_reviewer: 독립 리뷰
  └─ security_reviewer: 권한·입력·외부 경계 변경 시 보안 리뷰
       ↓ 지적 있음: 유형별 담당 재배정 → 수정 → 검증 → 재리뷰
       ↓ 수정 필요 0건: 단계 증거와 상태 갱신 → 다음 단계
  docs_maintainer: 확정된 동작·설계·검증 문서 반영
       ↓ 최종 staged tree 독립 리뷰 → 검증 → commit → push
```

실무 동시 실행은 최대 2개다. 런타임 슬롯이 부족하면 순차 실행한다. 리뷰는 구현이
안정된 이후 진행하며, 리뷰어에게 전체 diff와 이전 지적을 제공한다. 각 단계에서
계획→계약→구현→검증→리뷰를 생략하지 않는다. 단계 2와 3은 계약상 필요한 작은 단위만
함께 진행할 수 있지만, 패키지 전체 이동은 단계 6까지 미룬다.

호출 시 `templates/HANDOFF.md`를 사용한다. 역할이 런타임에 없으면 `default`/`worker`를
생성하고 `agent-context/roles/<역할>.md`를 읽도록 명시한다. 모델·추론 수준을 고정하지
않고 부모 설정을 상속한다. 새 역할·재배정·파일 소유권 변경은 상태에 기록한다.

## 2. 리뷰에 따른 재구성

| 지적 유형 | 다시 호출할 역할 | 필수 후속 증거 |
|---|---|---|
| 상태·불변조건·일관성 경계 | ddd_architect → backend_developer + test_engineer | 확정 계약과 실패 재현·통과 |
| 구현·쿼리·동시성 | backend_developer + test_engineer | 관련 통합 테스트와 전체 회귀 |
| 검증 공백·불안정 fixture | test_engineer | 단독/전체 실행과 실패 검출 근거 |
| 권한·입력·예외·비밀 처리 | security_reviewer → backend_developer/test_engineer | 경계 테스트 및 독립 재리뷰 |
| 문서·네이밍·책임 설명 | docs_maintainer | 구현과 문서 대조 |
| 린트·hook·게시 도구 | backend_developer 또는 도구 담당 worker | lint 실제 실패 차단·스크립트 검증 |

리뷰어는 지적을 파일·근거·수정 필요 여부·심각도와 함께 기록한다. 모든 수정 필요
항목을 해소해야 통과다. 미해결 항목을 단순히 낮은 심각도로 옮겨 통과시키지 않는다.
동일 원인으로 진전 없는 3회 반복 시 증거와 필요한 외부 조치를 보고하고 자동 게시를
중단한다. 단순히 어렵거나 시간이 걸린다는 이유로 이 규칙을 조기 발동하지 않는다.

## 3. 검증·상태 갱신

- 기본 검사: `.\gradlew.bat lint --rerun-tasks`, `.\gradlew.bat test sampleDataClasses`.
- 단계 1 재검증은 개발 DB 없이 테스트 전용 PostgreSQL/Redis 컨테이너로 실행한다.
  시작 준비 신호·제한 시간·결과 수집과 정리를 포함한 동시성 테스트를 유지한다.
- 실패 원인과 실제 실행 결과를 기록한다. 실행하지 않은 검사를 통과로 기록하지 않는다.
- 승인 전 `STATE.json`의 단계 상태, 남은 지적, 검증 증거를 갱신한다. 상태와 문서 변경도
  최종 리뷰 대상에 포함한다. 리뷰한 뒤에는 문서를 추가 수정하지 않는다.
- 검증 과정에서 산출된 `build/`, `.gradle/`, 로그·비밀·개발 DB 데이터는 stage하지 않는다.

## 4. 최종 staged tree와 자동 게시

1. 작업자가 멈춘 상태에서 의도한 변경만 stage한다. 사용자가 제외한 PDF는 보존하되 stage하지 않는다.
2. `git diff --cached --check`와 staged/worktree 일치 검사를 통과시킨다.
3. `git write-tree` 식별값, scope, 완료 단계, 전체 staged diff를 독립 리뷰어에게 전달한다.
   최초 설치의 scope는 `setup-checkpoint`, `completedSteps`는 `[1]`이다.
4. 리뷰어는 `templates/REVIEW.md` 형식으로 최종 결과를 작성한다. 승인 JSON은
   `git rev-parse --git-path refactor-review.json` 경로에 쓴다. 저장소 파일 안에 자기 tree
   hash를 넣으면 tree가 다시 바뀌므로 승인 JSON은 stage하지 않는다.
5. 실제 CLI가 요구하는 필드는 게시 스크립트를 기준으로 한다. 기본 승인은 다음 정보다.

```json
{
  "scope": "setup-checkpoint",
  "stagedTree": "<git write-tree 결과>",
  "verdict": "approved",
  "reviewer": "<구현과 독립된 리뷰어 식별자>",
  "reviewedAt": "<ISO8601 시각>",
  "completedSteps": [1],
  "findings": []
}
```

6. 최종 staged 변경을 기준으로 `codeConvention.md`의 타입 접두어와 한글 한 문장
   제목을 작성한다. 빈 줄 뒤에는 실제 작업 내용을 한글 항목으로 정리한다. 다음처럼
   여러 줄 메시지를 전달해 게시한다. 스크립트는 tree·브랜치·승인·실제 lint/test를
   다시 확인하며, Git pre-commit hook도 commit 직전에 실제 lint를 실행한다.

```powershell
$commitMessage = @'
chore: 리팩토링 실행 체계와 1번 단계의 검증 기반을 정리한다

- 리팩토링 단계별 역할과 독립 리뷰 절차를 문서화한다.
- 1번 단계의 구현 상태와 검증 결과를 기록한다.
'@

powershell -NoProfile -File scripts/publish-reviewed.ps1 `
  -Scope setup-checkpoint `
  -AttestationPath .git/refactor-review.json `
  -Message $commitMessage
```

7. commit 성공 후에만 `origin`의 `refactor/backend-quality`로 일반 push한다. 원격 충돌은
   강제로 덮지 않고 중단한다. 통합/충돌 해결로 tree가 바뀌면 검증과 독립 리뷰를 반복한다.
8. 최종 응답에 실제 완료 scope, commit, push 결과, 남은 단계를 보고한다.
   setup 체크포인트 승인으로 2~6번 완료나 프로덕션 리팩토링 완료를 주장하지 않는다.
