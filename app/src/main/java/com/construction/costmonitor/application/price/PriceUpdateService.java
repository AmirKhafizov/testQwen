package com.construction.costmonitor.application.price;

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

@Service
public class PriceUpdateService {

    private static final Logger log = LoggerFactory.getLogger(PriceUpdateService.class);

    private final MaterialMappingRepository mappingRepository;
    private final MaterialPriceRepository priceRepository;
    private final PriceProvider priceProvider;

    public PriceUpdateService(
            MaterialMappingRepository mappingRepository,
            MaterialPriceRepository priceRepository,
            PriceProvider priceProvider) {
        this.mappingRepository = mappingRepository;
        this.priceRepository = priceRepository;
        this.priceProvider = priceProvider;
    }

    @Transactional
    public int updatePricesForCompany(Long companyId) {
        List<MaterialMapping> mappings = mappingRepository.findByCompanyIdAndStatus(
                companyId, MaterialMapping.Status.CONFIRMED);

        int updated = 0;
        for (MaterialMapping mapping : mappings) {
            log.debug("Would update price for materialId={} (mappingId={})", mapping.getMaterialId(), mapping.getId());
            updated++;
        }
        log.info("Price update finished for companyId={}, processed mappings={}", companyId, updated);
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
