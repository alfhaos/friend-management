# 품질 게이트

`./gradlew.bat lint`는 모든 Java source set(main/test/sampleData)에 Checkstyle을 실행한다. 콘솔 출력·직접 stack trace 출력, 빈 문장, 내부 JDK import, 타입/파일명 불일치, 개행 누락, 충돌 표식을 검사한다. 기존 코드를 전면 포맷 변경하지 않는 초기 기준이며 `check`에도 Checkstyle이 포함된다.

훅 자체는 `powershell -NoProfile -File scripts/test-git-hook.ps1`로 작업트리의 `build/` 아래 임시 Git 저장소에서 검증한다. 린트 명령이 실패하면 커밋이 생성되지 않고 성공하면 생성되는지, 미해결 사용자 결정이 있으면 게시가 차단되는지 검사한다.

처음 clone한 작업자는 `powershell -NoProfile -File scripts/install-git-hooks.ps1`로 로컬 hook을 설치한다. 설치기는 기존 다른 hook 경로를 덮어쓰지 않는다. 커밋은 hook에서 `lint --rerun-tasks --no-build-cache`를 실행하며 실패하면 중단된다. `--no-verify` 또는 hook 해제는 금지한다. Git의 로컬 hook 자체는 사용자에 의해 우회될 수 있으므로 서버 차원의 강제 정책이 필요하면 보호 브랜치와 CI 필수 검사도 설정해야 한다.

자동 게시 절차:

1. 오케스트레이터가 해당 범위 파일을 명시적으로 stage한다. `git add .`로 사용자 파일을 임의로 포함하지 않는다.
2. 독립 리뷰어가 `git diff --cached`와 테스트 증거를 검토한다. 수정이 필요하면 담당 구현/테스트 에이전트로 반환하고 반복한다.
3. 리뷰어는 `git write-tree` 결과와 실제 범위로 JSON 증적을 작성한다. `review-attestation.example.json`은 형식 예시이며 승인 증거로 사용하면 안 된다. 증적은 stage되지 않는 Git 내부 경로 `.git/refactor-review.json`에 둔다. staging된 증적 파일 안에 자신의 tree hash를 넣는 순환 참조를 만들지 않는다.
4. `powershell -NoProfile -File scripts/publish-reviewed.ps1 -Scope setup-checkpoint -AttestationPath <실제 JSON 경로> -Message 'chore: configure refactoring agents and quality gates'`를 실행한다.

`setup-checkpoint`는 구성 및 1번 완료만 의미한다. `step-2-checkpoint`는 1~2번 완료 후에만 사용하고, `full-refactor`는 실제 1~6 전체 완료 후에만 사용한다. `completedSteps`는 각각 `[1]`, `[1,2]`, `[1,2,3,4,5,6]`과 정확히 일치해야 한다. `findings`는 빈 배열이어야 한다. 리뷰어 이름/시간은 독립 리뷰 결과에서 가져오며 오케스트레이터가 승인 결과를 만들어 내면 안 된다.

게이트는 unstaged tracked 변경과 untracked 빌드/게이트 입력을 거절한다. lint/test가 읽는 파일과 Git index의 차이로 검사를 우회하지 않게 한다. 최종 게시기는 lint·test·sampleData 컴파일을 다시 실행하고, tree가 바뀌면 중단한다. 커밋에 성공하고 tree가 일치할 때만 `origin/refactor/backend-quality`로 일반 push한다. push 실패 시 강제 push·main 병합은 하지 않는다. Docker가 실행 중이어야 통합 테스트가 통과한다.
