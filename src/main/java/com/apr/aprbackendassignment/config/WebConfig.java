package com.apr.aprbackendassignment.config;

import com.apr.aprbackendassignment.common.interceptor.LimitInterceptor;
import com.apr.aprbackendassignment.util.LimitProperties;
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
 *  - 인터셉터 등록: RateLimitInterceptor를 등록하여 모든 경로에 대해 요청 속도 제한을 적용
 *  - SWAGGER 등 특정 경로는 속도 제한에서 제외
 * =====================================================
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LimitInterceptor rateLimitInterceptor;
    private final LimitProperties rateLimitProperties;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")        // 적용할 URL
                .excludePathPatterns(          // 제외할 URL
                        rateLimitProperties.getExcludePaths()
                                .toArray(String[]::new)
                );
    }
}