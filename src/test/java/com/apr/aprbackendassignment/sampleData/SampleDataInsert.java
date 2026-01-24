package com.apr.aprbackendassignment.sampleData;

import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.model.constant.WINDOW_SLIDING;
import com.apr.aprbackendassignment.model.entity.Friendship;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.repository.FriendshipJPARepository;
import com.apr.aprbackendassignment.repository.UsersJPARepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * =====================================================
 * Class Name   : SampleDataInsert
 * Description  :
 *  - 친구 관계 관리를 위한 서비스 구현 클래스 테스트

 * 주요 기능
 *  - 샘플 데이터 삽입
 *  - 친구 관계 생성
 * =====================================================
 */
@SpringBootTest(properties = "app.jpa.auditing.enabled=false")
@Slf4j
class SampleDataInsert {

    @Autowired
    private UsersJPARepository usersJPARepository;
    @Autowired
    private FriendshipJPARepository friendshipJPARepository;

    private static final String UserName = "user";
    private static final Long CURRENT_USER_ID = 1L;

    /*
       샘플 사용자 생성 테스트 코드
       만명 의 사용자를 생성한다
    */
    @Test
    @Transactional
    @Rollback(false)
    void makeMillionUsers() {
        for (long i = 10003; i <= 10003; i++) {
            Users user = Users.create(UserName + i);
            usersJPARepository.save(user);
        }

        log.info("Made milion users with base name: {}", UserName);

        int userCount = usersJPARepository.findAll().size();
        assertTrue(userCount >= 10000, "사용자 수는 10000 이상이어야 합니다");
    }

    /*
       샘플 친구관계 생성 테스트 코드
       사용자(1L)를 기준으로 10,000개의 사용자에 대해서 친구관계를 생성한다
        5개의 구간으로 나누어 각각 다른 날짜로 친구 신청일자를 설정한다
    */
    @Test
    @Transactional
    @Rollback(false)
    void makeSampleFriendShip() {

        Users currentUser = usersJPARepository.findByIdOrThrow(CURRENT_USER_ID);
        List<Users> allUsers = usersJPARepository.findAll();

        List<Friendship> friendshipList = new ArrayList<>();

        int total = allUsers.size() - 1; // 자기 자신 제외
        int chunk = total / 5;

        int index = 0;

        for (Users user : allUsers) {
            if (user.equals(currentUser)) continue;

            Friendship friendship =
                    Friendship.create(user, currentUser,
                            FRIENDSHIP_STATUS.REQUESTED);
            // 분기
            LocalDateTime baseTime;
            if (index < chunk) {
                baseTime = LocalDateTime.now().minusDays(WINDOW_SLIDING.DAY.getCodeDate()); // 1d
            } else if (index < chunk * 2) {
                baseTime = LocalDateTime.now().minusDays(WINDOW_SLIDING.WEEK.getCodeDate()); // 7d
            } else if (index < chunk * 3) {
                baseTime = LocalDateTime.now().minusDays(WINDOW_SLIDING.MONTH.getCodeDate()); // 30d
            } else if (index < chunk * 4) {
                baseTime = LocalDateTime.now().minusDays(WINDOW_SLIDING.NINETY_DAY.getCodeDate()); // 90d
            } else {
                baseTime = LocalDateTime.now().minusDays(WINDOW_SLIDING.OVER.getCodeDate()); // over
            }

            friendship.updateRequestedAt(baseTime);
            friendshipList.add(friendship);
            index++;
        }

        friendshipJPARepository.saveAll(friendshipList);

        int friendshipCount =
                friendshipJPARepository.findAll().size();

        assertTrue(friendshipCount >= 9999,
                "친구 관계 수는 9999 이상 이어야 합니다");
    }


    /*
       친구 관계 수락 테스트 코드
        사용자(1L)를 기준으로 모든 친구 신청(만개의 요청)을 수락한다
    */
    @Test
    @Transactional
    @Rollback(false)
    void acceptFriendshipAll() {
        Users currentUser = usersJPARepository.findByIdOrThrow(CURRENT_USER_ID);
        List<Friendship> friendshipList =
                friendshipJPARepository.findByAcceptorAndStatus(
                        currentUser.getId(), FRIENDSHIP_STATUS.REQUESTED);

        for (Friendship friendship : friendshipList) {
            friendship.updateStatus(FRIENDSHIP_STATUS.ACCEPTED);
        }

        friendshipJPARepository.saveAll(friendshipList);

        int acceptedCount =
                friendshipJPARepository.countUsersFriends(
                        currentUser.getId(), FRIENDSHIP_STATUS.ACCEPTED);

        assertTrue(acceptedCount >= 9999,
                "수락된 친구 관계 수는 9999 이상 이어야 합니다");
    }

}
