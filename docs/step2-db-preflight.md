# 2번 친구 관계 제약 적용 전 데이터 확인

`src/main/resources/schema.sql`은 애플리케이션 기동 시 기존 `friendship` 테이블에 NOT NULL 제약과 활성 양방향 유니크 인덱스를 적용한다. 배포 대상 PostgreSQL에서 아래 읽기 전용 쿼리를 먼저 실행한다. 결과가 나오면 데이터를 삭제·합치지 말고 기존 이력과 처리 정책을 확인한다.

```sql
SELECT id, requester_id, receiver_id, status
FROM friendship
WHERE requester_id IS NULL OR receiver_id IS NULL OR status IS NULL;

SELECT LEAST(requester_id, receiver_id) AS user_a,
       GREATEST(requester_id, receiver_id) AS user_b,
       COUNT(*) AS active_count,
       ARRAY_AGG(id ORDER BY id) AS request_ids
FROM friendship
WHERE status IN ('REQUESTED', 'ACCEPTED')
GROUP BY 1, 2
HAVING COUNT(*) > 1;

SELECT requester_id, receiver_id, COUNT(*) AS rejected_count,
       ARRAY_AGG(id ORDER BY id) AS request_ids
FROM friendship
WHERE status = 'REJECTED'
GROUP BY requester_id, receiver_id
HAVING COUNT(*) > 1;

SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'friendship'
ORDER BY indexname;
```

2026-10-22 로컬 개발 PostgreSQL 읽기 전용 확인: `friendship`은 0행이며 위 세 유형의 행·그룹도 각각 0건이다. 방향별 `(requester_id, receiver_id)` 유니크 인덱스는 설치돼 있다. `ux_friendship_active_pair`는 아직 설치되지 않았으므로 새 애플리케이션 기동 뒤 확인해야 한다. 이 결과는 다른 배포 DB의 상태를 증명하지 않는다.
