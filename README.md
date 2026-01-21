# apr-backend-assignment
에이피알 백엔드 과제 전형


## 사용자 기준 처리

본 과제는 인증 기능이 없으므로  
테스트를 위해 userId = 1L을 로그인 사용자로 가정하여 구현했습니다.


## 레이어 엔티티 관리
- repository: DB와 직접적으로 상호작용하는 레이어로 엔티티에 대한 로직 수행이후 DTO를 반환 합니다.
- service: 비즈니스 로직을 처리하는 레이어로 repository에서 반환된 DTO를 가공하여 컨트롤러에 반환합니다.
- controller: 클라이언트 요청을 처리하는 레이어로 service에서 반환된 DTO를 응답으로 반환합니다.

## 기능요구 사항
1. 친구목록 조회 API
   - friendShip 테이블에서 requester/receiver가 현재 사용자인 목록을 조회 이후 fromEntityPage 함수를 통해 친구 아이디를 추출하여 응답으로 반환합니다.
   - 상태가 ACCEPTED인 친구 관계만 조회합니다.
   - 서비스 레이어에서 나(사용자) 아이디를 1L로 가정하여 처리합니다.
