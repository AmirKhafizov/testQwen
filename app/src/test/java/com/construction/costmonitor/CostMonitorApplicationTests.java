package com.construction.costmonitor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CostMonitorApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that Spring context starts with Testcontainers PostgreSQL
    }
}
