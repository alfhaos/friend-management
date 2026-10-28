package com.apr.aprbackendassignment.ratelimit;

import com.apr.aprbackendassignment.friendship.application.FriendLimitProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void existingLimitKeysBindToTheirSeparatedOwners() {
        contextRunner.withPropertyValues(validProperties()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FriendLimitProperties.class).getMaxFriend()).isEqualTo(100);
            RateLimitProperties requestLimits = context.getBean(RateLimitProperties.class);
            assertThat(requestLimits.getMaxRequests()).isEqualTo(10);
            assertThat(requestLimits.getWindowMilliSeconds()).isEqualTo(1_000);
            assertThat(requestLimits.getPaths()).containsExactly("/api/friends/request");
            assertThat(requestLimits.getExcludePaths()).containsExactly("/swagger-ui/**", "/v3/api-docs/**");
        });
    }

    @ParameterizedTest
    @MethodSource("nonPositiveProperties")
    void nonPositiveLimitsPreventApplicationStartup(String property) {
        contextRunner.withPropertyValues(validProperties())
                .withPropertyValues(property)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void emptyRateLimitedPathListPreventsApplicationStartup() {
        contextRunner.withPropertyValues(
                        "limit.max-friend=100",
                        "limit.max-requests=10",
                        "limit.window-milli-seconds=1000",
                        "limit.paths=")
                .run(context -> assertThat(context).hasFailed());
    }

    private static Stream<Arguments> nonPositiveProperties() {
        return Stream.of(
                Arguments.of("limit.max-friend=0"),
                Arguments.of("limit.max-friend=-1"),
                Arguments.of("limit.max-requests=0"),
                Arguments.of("limit.max-requests=-1"),
                Arguments.of("limit.window-milli-seconds=0"),
                Arguments.of("limit.window-milli-seconds=-1")
        );
    }

    private String[] validProperties() {
        return new String[] {
                "limit.max-friend=100",
                "limit.max-requests=10",
                "limit.window-milli-seconds=1000",
                "limit.paths[0]=/api/friends/request",
                "limit.exclude-paths[0]=/swagger-ui/**",
                "limit.exclude-paths[1]=/v3/api-docs/**"
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({RateLimitProperties.class, FriendLimitProperties.class})
    static class PropertiesConfiguration {
    }
}
