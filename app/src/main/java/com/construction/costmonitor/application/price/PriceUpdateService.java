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
     * Update prices for all active companies (used by scheduler).
     */
    public int updatePricesForAllCompanies() {
        List<Company> companies = companyRepository.findAll().stream()
                .filter(Company::isActive)
                .toList();

        int total = 0;
        for (Company company : companies) {
            try {
                total += updatePricesForCompany(company.getId());
            } catch (Exception e) {
                log.error("Failed price update for company id={} code={}", company.getId(), company.getCode(), e);
            }
        }
        log.info("Global price update finished, saved {} new price rows", total);
        return total;
    }

    @Transactional
    public int updatePricesForCompany(Long companyId) {
        List<MaterialMapping> mappings = mappingRepository.findByCompanyIdAndStatus(
                companyId, MaterialMapping.Status.CONFIRMED);

        int updated = 0;
        for (MaterialMapping mapping : mappings) {
            Optional<ExternalProduct> productOpt =
                    externalProductRepository.findById(mapping.getExternalProductId());
            if (productOpt.isEmpty()) {
                log.warn("External product {} missing for mapping {}", mapping.getExternalProductId(), mapping.getId());
                continue;
            }

            ExternalProduct product = productOpt.get();
            String sku = product.getExternalSku();

            Optional<PriceQuote> quoteOpt = priceProvider.fetchPrice(sku);
            if (quoteOpt.isEmpty()) {
                log.warn("No price for sku={} materialId={}", sku, mapping.getMaterialId());
                continue;
            }

            PriceQuote quote = quoteOpt.get();
            savePrice(companyId, mapping.getMaterialId(), product.getId(), quote);

            // keep external catalog name/url fresh
            if (quote.productName() != null && !quote.productName().isBlank()) {
                product.setName(quote.productName());
            }
            if (quote.productUrl() != null) {
                product.setProductUrl(quote.productUrl());
            }
            product.setLastSyncedAt(java.time.Instant.now());
            externalProductRepository.save(product);

            updated++;
            log.info("Saved price {} {} for materialId={} sku={}",
                    quote.price(), quote.currency(), mapping.getMaterialId(), sku);
        }

        log.info("Price update finished for companyId={}, saved={}/{}",
                companyId, updated, mappings.size());
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
    }
}
