package com.apr.aprbackendassignment.friendship.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RequestWindowTest {

    @Test
    void calculatesEachWindowFromTheProvidedTime() {
        LocalDateTime now = LocalDateTime.of(2025, 3, 1, 0, 0);

        assertThat(RequestWindow.DAY.calculateTimeWindow(now)).isEqualTo(now.minusDays(1));
        assertThat(RequestWindow.WEEK.calculateTimeWindow(now)).isEqualTo(now.minusDays(7));
        assertThat(RequestWindow.MONTH.calculateTimeWindow(now)).isEqualTo(now.minusDays(30));
        assertThat(RequestWindow.NINETY_DAY.calculateTimeWindow(now)).isEqualTo(now.minusDays(90));
        assertThat(RequestWindow.OVER.calculateTimeWindow(now)).isNull();
    }
}
