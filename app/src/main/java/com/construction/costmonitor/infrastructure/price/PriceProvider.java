package com.construction.costmonitor.infrastructure.price;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Abstraction for fetching current prices from external sources (LemanaPro parser, future B2B API, etc.).
 */
public interface PriceProvider {

    String getSourceName();

    /**
     * Fetch current price by external SKU / product id.
     */
    Optional<PriceQuote> fetchPrice(String externalSku);

    record PriceQuote(
            String externalSku,
            String productName,
            BigDecimal price,
            String currency,
            String productUrl
    ) {}
}
