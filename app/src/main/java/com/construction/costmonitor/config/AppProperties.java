package com.construction.costmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Lemana lemana = new Lemana();

    public Lemana getLemana() {
        return lemana;
    }

    public static class Lemana {
        private String baseUrl = "https://kazan.lemanapro.ru";
        private long requestDelayMs = 1500;
        private String userAgent = "Mozilla/5.0 (compatible; CostMonitor/0.1)";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public long getRequestDelayMs() {
            return requestDelayMs;
        }

        public void setRequestDelayMs(long requestDelayMs) {
            this.requestDelayMs = requestDelayMs;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }
}
