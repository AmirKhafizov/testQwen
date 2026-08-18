package com.construction.costmonitor.application.price;

import com.construction.costmonitor.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ежедневное обновление цен с LemanaPro.
 * По умолчанию: каждый день в 10:00 по Москве (Europe/Moscow).
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
            log.debug("Планировщик цен: отключён (app.scheduler.price-update-enabled=false)");
            return;
        }

        String zone = appProperties.getScheduler().getPriceUpdateZone();
        log.info("Планировщик цен: старт ежедневного обновления (часовой пояс={})", zone);
        long started = System.currentTimeMillis();
        try {
            int saved = priceUpdateService.updatePricesForAllCompanies();
            long tookMs = System.currentTimeMillis() - started;
            log.info("Планировщик цен: готово за {} мс, сохранено записей цен — {}", tookMs, saved);
        } catch (Exception e) {
            log.error("Планировщик цен: сбой при обновлении — {}", e.getMessage(), e);
        }
    }
}
