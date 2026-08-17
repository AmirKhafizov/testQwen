package com.construction.costmonitor.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/v1/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok", "service", "cost-monitor");
    }
}
