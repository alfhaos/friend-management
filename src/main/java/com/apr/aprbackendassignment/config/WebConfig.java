package com.apr.aprbackendassignment.config;

import com.apr.aprbackendassignment.ratelimit.RateLimitInterceptor;
import com.apr.aprbackendassignment.ratelimit.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
/**
 * =====================================================
 * Class Name   : WebConfig
 * Description  :
 *  - 웹 애플리케이션의 설정을 담당하는 클래스
 *  - 인터셉터를 등록하여 특정 경로에 대한 요청을 가로채고 처리

 * 주요 기능
 *  - 인터셉터 등록: 설정된 신청 경로에 요청 속도 제한을 적용
 *  - 설정된 제외 경로는 요청 속도 제한에서 제외
 * =====================================================
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final RateLimitProperties rateLimitProperties;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(rateLimitProperties.getPaths().toArray(String[]::new))
                .excludePathPatterns(
                        rateLimitProperties.getExcludePaths()
                                .toArray(String[]::new)
                );
    }
}
