package com.apr.aprbackendassignment.model.constant;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WindowSlidingTest {

    @Test
    void calculatesEachWindowFromTheProvidedTime() {
        LocalDateTime now = LocalDateTime.of(2025, 3, 1, 0, 0);

        assertThat(WINDOW_SLIDING.DAY.calculateTimeWindow(now)).isEqualTo(now.minusDays(1));
        assertThat(WINDOW_SLIDING.WEEK.calculateTimeWindow(now)).isEqualTo(now.minusDays(7));
        assertThat(WINDOW_SLIDING.MONTH.calculateTimeWindow(now)).isEqualTo(now.minusDays(30));
        assertThat(WINDOW_SLIDING.NINETY_DAY.calculateTimeWindow(now)).isEqualTo(now.minusDays(90));
        assertThat(WINDOW_SLIDING.OVER.calculateTimeWindow(now)).isNull();
    }
}
