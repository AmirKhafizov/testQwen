package com.construction.costmonitor.application.price;

import com.construction.costmonitor.domain.company.Company;
import com.construction.costmonitor.domain.company.CompanyRepository;
import com.construction.costmonitor.domain.external.ExternalProduct;
import com.construction.costmonitor.domain.external.ExternalProductRepository;
import com.construction.costmonitor.domain.mapping.MaterialMapping;
import com.construction.costmonitor.domain.mapping.MaterialMappingRepository;
import com.construction.costmonitor.domain.price.MaterialPrice;
import com.construction.costmonitor.domain.price.MaterialPriceRepository;
import com.construction.costmonitor.infrastructure.price.PriceProvider;
import com.construction.costmonitor.infrastructure.price.PriceProvider.PriceQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PriceUpdateService {

    private static final Logger log = LoggerFactory.getLogger(PriceUpdateService.class);

    private final CompanyRepository companyRepository;
    private final MaterialMappingRepository mappingRepository;
    private final ExternalProductRepository externalProductRepository;
    private final MaterialPriceRepository priceRepository;
    private final PriceProvider priceProvider;

    public PriceUpdateService(
            CompanyRepository companyRepository,
            MaterialMappingRepository mappingRepository,
            ExternalProductRepository externalProductRepository,
            MaterialPriceRepository priceRepository,
            PriceProvider priceProvider) {
        this.companyRepository = companyRepository;
        this.mappingRepository = mappingRepository;
        this.externalProductRepository = externalProductRepository;
        this.priceRepository = priceRepository;
        this.priceProvider = priceProvider;
    }

    /**
     * Обновление цен по всем активным компаниям (вызов из scheduler / admin API).
     */
    public int updatePricesForAllCompanies() {
        List<Company> companies = companyRepository.findAll().stream()
                .filter(Company::isActive)
                .toList();

        log.info("Обновление цен: найдено активных компаний — {}", companies.size());
        if (companies.isEmpty()) {
            log.warn("Обновление цен: активных компаний нет, нечего обновлять");
            return 0;
        }

        int total = 0;
        for (Company company : companies) {
            try {
                log.info("Обновление цен: компания id={}, код=«{}», название=«{}»",
                        company.getId(), company.getCode(), company.getName());
                total += updatePricesForCompany(company.getId());
            } catch (Exception e) {
                log.error("Обновление цен: ошибка по компании id={}, код=«{}»: {}",
                        company.getId(), company.getCode(), e.getMessage(), e);
            }
        }
        log.info("Обновление цен завершено по всем компаниям: сохранено новых записей — {}", total);
        return total;
    }

    @Transactional
    public int updatePricesForCompany(Long companyId) {
        List<MaterialMapping> mappings = mappingRepository.findByCompanyIdAndStatus(
                companyId, MaterialMapping.Status.CONFIRMED);

        log.info("Обновление цен: компания id={}, подтверждённых маппингов — {}",
                companyId, mappings.size());

        if (mappings.isEmpty()) {
            log.warn("Обновление цен: у компании id={} нет маппингов со статусом CONFIRMED — пропускаем",
                    companyId);
            return 0;
        }

        int updated = 0;
        int failed = 0;
        for (MaterialMapping mapping : mappings) {
            Optional<ExternalProduct> productOpt =
                    externalProductRepository.findById(mapping.getExternalProductId());
            if (productOpt.isEmpty()) {
                log.warn("Обновление цен: внешний товар id={} не найден (маппинг id={}, материал id={})",
                        mapping.getExternalProductId(), mapping.getId(), mapping.getMaterialId());
                failed++;
                continue;
            }

            ExternalProduct product = productOpt.get();
            String sku = product.getExternalSku();

            log.info("Обновление цен: запрос цены — материал id={}, артикул [{}], маппинг id={}",
                    mapping.getMaterialId(), sku, mapping.getId());

            Optional<PriceQuote> quoteOpt = priceProvider.fetchPrice(sku);
            if (quoteOpt.isEmpty()) {
                log.warn("Обновление цен: цена не получена — артикул [{}], материал id={}",
                        sku, mapping.getMaterialId());
                failed++;
                continue;
            }

            PriceQuote quote = quoteOpt.get();
            savePrice(companyId, mapping.getMaterialId(), product.getId(), quote);

            if (quote.productName() != null && !quote.productName().isBlank()) {
                product.setName(quote.productName());
            }
            if (quote.productUrl() != null) {
                product.setProductUrl(quote.productUrl());
            }
            product.setLastSyncedAt(java.time.Instant.now());
            externalProductRepository.save(product);

            updated++;
            log.info("Обновление цен: сохранено — материал id={}, артикул [{}], цена {} {}, название «{}»",
                    mapping.getMaterialId(), sku, quote.price(), quote.currency(), quote.productName());
        }

        log.info("Обновление цен по компании id={}: успешно {}, ошибок {}, всего маппингов {}",
                companyId, updated, failed, mappings.size());
        return updated;
    }

    @Transactional
    public void savePrice(Long companyId, Long materialId, Long externalProductId, PriceQuote quote) {
        MaterialPrice price = new MaterialPrice(
                companyId,
                materialId,
                quote.price(),
                priceProvider.getSourceName()
        );
        price.setExternalProductId(externalProductId);
        price.setCurrency(quote.currency() != null ? quote.currency() : "RUB");
        priceRepository.save(price);
        log.debug("Обновление цен: запись в material_prices — компания={}, материал={}, цена={}",
                companyId, materialId, quote.price());
    }
}
