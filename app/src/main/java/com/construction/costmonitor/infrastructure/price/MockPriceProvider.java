package com.construction.costmonitor.infrastructure.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Заглушка цен для локальной разработки, когда Qrator блокирует парсер (HTTP 403).
 * Включается: app.lemana.provider=mock
 */
@Component
@ConditionalOnProperty(name = "app.lemana.provider", havingValue = "mock")
public class MockPriceProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPriceProvider.class);

    public static final String SOURCE = "MOCK";

    /** Известные артикулы → цена (можно расширять). Остальные — детерминированная «цена» от SKU. */
    private static final Map<String, BigDecimal> KNOWN = Map.of(
            "81976749", new BigDecimal("598.00"),
            "85999876", new BigDecimal("821.00"),
            "85342295", new BigDecimal("900.00"),
            "82405577", new BigDecimal("1596.00"),
            "89399646", new BigDecimal("1250.50")
    );

    @Override
    public String getSourceName() {
        return SOURCE;
    }

    @Override
    public Optional<PriceQuote> fetchPrice(String externalSku) {
        if (externalSku == null || externalSku.isBlank()) {
            return Optional.empty();
        }
        String sku = externalSku.trim();
        BigDecimal price = KNOWN.getOrDefault(sku, syntheticPrice(sku));
        String name = "Мок-товар " + sku;

        log.info("Mock-провайдер: артикул [{}], цена {} RUB (Qrator-обход для разработки)", sku, price);

        return Optional.of(new PriceQuote(
                sku,
                name,
                price,
                "RUB",
                "https://kazan.lemanapro.ru/search/?q=" + sku
        ));
    }

    private static BigDecimal syntheticPrice(String sku) {
        int hash = Math.abs(sku.hashCode() % 5000) + 100;
        return BigDecimal.valueOf(hash);
    }
}
