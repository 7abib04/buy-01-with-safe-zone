package com.buy01.gatewayservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

class CorsConfigTest {

    @Test
    void allowsPatchRequestsUsedByOrderCancelAndStatusUpdates() {
        CorsConfiguration configuration = new CorsConfig().buildCorsConfiguration("http://localhost:4200");

        assertThat(configuration.getAllowedMethods()).contains("PATCH");
    }
}
