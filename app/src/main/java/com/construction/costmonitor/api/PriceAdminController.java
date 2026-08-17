package com.construction.costmonitor.api;

import com.construction.costmonitor.application.price.PriceUpdateService;
import com.construction.costmonitor.infrastructure.price.PriceProvider;
import com.construction.costmonitor.infrastructure.price.PriceProvider.PriceQuote;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Manual triggers for development / ops.
 * In production protect with auth.
 */
@RestController
@RequestMapping("/api/v1/admin/prices")
public class PriceAdminController {

    private final PriceUpdateService priceUpdateService;
    private final PriceProvider priceProvider;

    public PriceAdminController(PriceUpdateService priceUpdateService, PriceProvider priceProvider) {
        this.priceUpdateService = priceUpdateService;
        this.priceProvider = priceProvider;
    }

    /** Run full update for all companies (same as nightly job). */
    @PostMapping("/refresh-all")
    public Map<String, Object> refreshAll() {
        int saved = priceUpdateService.updatePricesForAllCompanies();
        return Map.of("saved", saved, "source", priceProvider.getSourceName());
    }

    /** Run update for one company. */
    @PostMapping("/refresh/{companyId}")
    public Map<String, Object> refreshCompany(@PathVariable Long companyId) {
        int saved = priceUpdateService.updatePricesForCompany(companyId);
        return Map.of("companyId", companyId, "saved", saved);
    }

    /** Probe single SKU against LemanaPro (does not persist). */
    @GetMapping("/probe/{sku}")
    public ResponseEntity<?> probe(@PathVariable String sku) {
        Optional<PriceQuote> quote = priceProvider.fetchPrice(sku);
        if (quote.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(quote.get());
    }
}
