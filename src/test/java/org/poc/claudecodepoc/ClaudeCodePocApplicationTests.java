package org.poc.claudecodepoc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest
@Import(ClaudeCodePocApplicationTests.SecurityTestConfig.class)
class ClaudeCodePocApplicationTests {

    @TestConfiguration
    static class SecurityTestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> { throw new UnsupportedOperationException("Mock decoder — not for real tokens"); };
        }
    }

    @Test
    void contextLoads() {
    }
}