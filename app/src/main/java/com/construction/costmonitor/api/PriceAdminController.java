package com.construction.costmonitor.api;

import com.construction.costmonitor.application.price.PriceUpdateService;
import com.construction.costmonitor.infrastructure.price.PriceProvider;
import com.construction.costmonitor.infrastructure.price.PriceProvider.PriceQuote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Ручные операции для разработки и сопровождения.
 * В проде закрыть авторизацией.
 */
@RestController
@RequestMapping("/api/v1/admin/prices")
@Tag(name = "Prices (Admin)", description = "Ручной запуск обновления цен и probe парсера LemanaPro")
public class PriceAdminController {

    private static final Logger log = LoggerFactory.getLogger(PriceAdminController.class);

    private final PriceUpdateService priceUpdateService;
    private final PriceProvider priceProvider;

    public PriceAdminController(PriceUpdateService priceUpdateService, PriceProvider priceProvider) {
        this.priceUpdateService = priceUpdateService;
        this.priceProvider = priceProvider;
    }

    @PostMapping("/refresh-all")
    @Operation(
            summary = "Обновить цены по всем компаниям",
            description = "То же, что ночной job в 10:00 МСК. Обрабатывает только CONFIRMED маппинги."
    )
    @ApiResponse(responseCode = "200", description = "Количество сохранённых строк цен")
    public Map<String, Object> refreshAll() {
        log.info("Admin API: запрос полного обновления цен по всем компаниям");
        int saved = priceUpdateService.updatePricesForAllCompanies();
        log.info("Admin API: полное обновление завершено, сохранено записей — {}", saved);
        return Map.of("saved", saved, "source", priceProvider.getSourceName());
    }

    @PostMapping("/refresh/{companyId}")
    @Operation(summary = "Обновить цены одной компании")
    public Map<String, Object> refreshCompany(
            @Parameter(description = "ID компании (companies.id)", example = "1")
            @PathVariable Long companyId) {
        log.info("Admin API: запрос обновления цен для компании id={}", companyId);
        int saved = priceUpdateService.updatePricesForCompany(companyId);
        log.info("Admin API: обновление компании id={} завершено, сохранено — {}", companyId, saved);
        return Map.of("companyId", companyId, "saved", saved);
    }

    @GetMapping("/probe/{sku}")
    @Operation(
            summary = "Probe цены по артикулу LemanaPro",
            description = "Ходит на kazan.lemanapro.ru через Playwright. В БД ничего не пишет."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Цена найдена",
            content = @Content(schema = @Schema(implementation = PriceQuote.class))
    )
    @ApiResponse(responseCode = "404", description = "Товар не найден или парсер не смог извлечь цену")
    public ResponseEntity<?> probe(
            @Parameter(description = "Артикул / productItem на сайте LemanaPro", example = "81976749")
            @PathVariable String sku) {
        log.info("Admin API: probe цены по артикулу [{}]", sku);
        Optional<PriceQuote> quote = priceProvider.fetchPrice(sku);
        if (quote.isEmpty()) {
            log.warn("Admin API: probe — цена для артикула [{}] не найдена", sku);
            return ResponseEntity.notFound().build();
        }
        log.info("Admin API: probe успешен — артикул [{}], цена {} {}",
                sku, quote.get().price(), quote.get().currency());
        return ResponseEntity.ok(quote.get());
    }
}
