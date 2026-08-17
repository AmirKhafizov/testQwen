package com.construction.costmonitor.application.price;

import com.construction.costmonitor.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily price refresh from LemanaPro.
 * Cron default: 10:00 Europe/Moscow every day.
 */
@Component
public class PriceUpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceUpdateScheduler.class);

    private final PriceUpdateService priceUpdateService;
    private final AppProperties appProperties;

    public PriceUpdateScheduler(PriceUpdateService priceUpdateService, AppProperties appProperties) {
        this.priceUpdateService = priceUpdateService;
        this.appProperties = appProperties;
    }

    @Scheduled(
            cron = "${app.scheduler.price-update-cron:0 0 10 * * *}",
            zone = "${app.scheduler.price-update-zone:Europe/Moscow}"
    )
    public void runDailyPriceUpdate() {
        if (!appProperties.getScheduler().isPriceUpdateEnabled()) {
            log.debug("Price update scheduler is disabled");
            return;
        }

        log.info("Starting scheduled LemanaPro price update (zone={})",
                appProperties.getScheduler().getPriceUpdateZone());
        long started = System.currentTimeMillis();
        try {
            int saved = priceUpdateService.updatePricesForAllCompanies();
            log.info("Scheduled price update done in {} ms, rows={}",
                    System.currentTimeMillis() - started, saved);
        } catch (Exception e) {
            log.error("Scheduled price update failed", e);
        }
    }
}
