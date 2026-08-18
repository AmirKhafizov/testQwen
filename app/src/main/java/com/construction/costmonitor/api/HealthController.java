package com.construction.costmonitor.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Health", description = "Проверка доступности сервиса")
public class HealthController {

    @GetMapping("/api/v1/ping")
    @Operation(summary = "Ping", description = "Простой health-check")
    public Map<String, String> ping() {
        return Map.of("status", "ok", "service", "cost-monitor");
    }
}
