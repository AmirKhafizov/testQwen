package com.construction.costmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Lemana lemana = new Lemana();
    private final Scheduler scheduler = new Scheduler();

    public Lemana getLemana() {
        return lemana;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public static class Lemana {
        private String baseUrl = "https://kazan.lemanapro.ru";
        private long requestDelayMs = 2000;
        private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        private boolean headless = true;
        private long navigationTimeoutMs = 45_000;

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

        public boolean isHeadless() {
            return headless;
        }

        public void setHeadless(boolean headless) {
            this.headless = headless;
        }

        public long getNavigationTimeoutMs() {
            return navigationTimeoutMs;
        }

        public void setNavigationTimeoutMs(long navigationTimeoutMs) {
            this.navigationTimeoutMs = navigationTimeoutMs;
        }
    }

    public static class Scheduler {
        private boolean priceUpdateEnabled = true;
        private String priceUpdateCron = "0 0 10 * * *";
        private String priceUpdateZone = "Europe/Moscow";

        public boolean isPriceUpdateEnabled() {
            return priceUpdateEnabled;
        }

        public void setPriceUpdateEnabled(boolean priceUpdateEnabled) {
            this.priceUpdateEnabled = priceUpdateEnabled;
        }

        public String getPriceUpdateCron() {
            return priceUpdateCron;
        }

        public void setPriceUpdateCron(String priceUpdateCron) {
            this.priceUpdateCron = priceUpdateCron;
        }

        public String getPriceUpdateZone() {
            return priceUpdateZone;
        }

        public void setPriceUpdateZone(String priceUpdateZone) {
            this.priceUpdateZone = priceUpdateZone;
        }
    }
}
