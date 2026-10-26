# 독립 리뷰 결과

- scope / 단계:
- 리뷰어 식별자 및 독립성: 이 변경의 구현자가 아님
- 검토한 staged tree (`git write-tree`):
- 검토 파일과 근거:
- 실제 검증 명령 / exit code / 결과:
- 수정 필요 항목: 식별자, 심각도, 파일·위치, 재현·근거, 담당 역할
- 참고 의견: 수정 필요 항목과 명확히 구분
- 판정: changes-required / approved
- 완료 단계: setup-checkpoint는 `[1]`; step-2-checkpoint는 `[1,2]`;
  full-refactor는 `[1,2,3,4,5,6]`
- 결정 감사: `DECISIONS.md`의 사용자 답변과 구현 일치 여부, 현재 scope의 미해결 질문 0건
- 미완료 범위: setup에서는 2~6 pending을 명시

승인은 모든 수정 필요 항목이 0개이고 실제 lint/test가 성공한 동일 staged tree에만 부여한다.
수정·재stage로 tree가 달라지면 재리뷰한다. 승인 JSON은 게시 스크립트가 요구하는 형식으로
Git 내부 `refactor-review.json`에 기록하고 stage하지 않는다. 문서의 과거 승인이나
비어 있는 STATE 지적 목록은 최종 tree 승인 증거가 아니다.
