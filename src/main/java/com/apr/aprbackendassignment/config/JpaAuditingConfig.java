package com.apr.aprbackendassignment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
/**
 * =====================================================
 * Class Name   : JpaAuditingConfig
 * Description  :
 *  - JPA 감사(Auditing) 기능을 활성화하는 설정 클래스
 *  - app.jpa.auditing.enabled 프로퍼티가 true로 설정된 경우에만 활성화
 *  - 테스트 코드에서 샘플 데이터 INSERT 시 JPA 감사 기능을 비활성화 하기 위해 사용
 * =====================================================
 */
@Configuration
@EnableJpaAuditing
@ConditionalOnProperty(name = "app.jpa.auditing.enabled", havingValue = "true", matchIfMissing = true)
public class JpaAuditingConfig {
}
