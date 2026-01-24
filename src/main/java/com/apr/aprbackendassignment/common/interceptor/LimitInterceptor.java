package com.apr.aprbackendassignment.common.interceptor;

import com.apr.aprbackendassignment.common.response.CommResponse;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.util.InMemoryRateLimiter;
import com.apr.aprbackendassignment.util.LimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * =====================================================
 * Class Name   : RateLimitInterceptor
 * Description  :
 *  - 요청 속도 제한을 위한 인터셉터 클래스

 * 주요 기능
 *  - preHandle 메서드: 특정 API 경로에 대한 요청을 검사하고, 속도 제한을 적용
 * =====================================================
 */
@Component
@RequiredArgsConstructor
public class LimitInterceptor implements HandlerInterceptor {

    private final InMemoryRateLimiter limiter;
    private final ObjectMapper objectMapper;
    private final LimitProperties properties;
    private final static String X_USER_ID_HEADER = "X-user-Id";


    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        String uri = request.getRequestURI();
        if (properties.getPaths().stream().noneMatch(uri::startsWith)) {
            return true;
        }
        String userId = request.getHeader(X_USER_ID_HEADER);
        if (userId == null) return true;

        boolean allowed =
                limiter.allow(Long.valueOf(userId));

        if (!allowed) {
            response.setStatus(CommResponseStatus.TOO_MANY_REQUEST.getHttpStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            CommResponse<?> errorResponse = CommResponse.error(CommResponseStatus.TOO_MANY_REQUEST);
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));

            return false;
        }
        return true;
    }

}
