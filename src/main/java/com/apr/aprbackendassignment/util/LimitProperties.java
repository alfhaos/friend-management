package com.apr.aprbackendassignment.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
/**
 * =====================================================
 * Class Name   : LimitProperties
 * Description  :
 *  - 애플리케이션의 제한 관련 설정을 외부 구성 파일에서 로드하는 클래스

 * 주요 기능
 *  - 친구 요청 및 속도 제한 관련 설정 보관
 * =====================================================
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "limit")
public class LimitProperties {

    private int maxFriend;
    private int maxRequests;
    private int windowMilliSeconds;
    private List<String> paths;
    private List<String> excludePaths;
}
