package com.construction.costmonitor.api;

import com.construction.costmonitor.api.dto.CatalogDtos.PriceResponse;
import com.construction.costmonitor.domain.material.MaterialRepository;
import com.construction.costmonitor.domain.price.MaterialPrice;
import com.construction.costmonitor.domain.price.MaterialPriceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Prices", description = "История и актуальные цены материалов")
public class PriceQueryController {

    private final MaterialRepository materialRepository;
    private final MaterialPriceRepository priceRepository;

    public PriceQueryController(MaterialRepository materialRepository, MaterialPriceRepository priceRepository) {
        this.materialRepository = materialRepository;
        this.priceRepository = priceRepository;
    }

    @GetMapping("/materials/{materialId}/prices/latest")
    @Operation(summary = "Последняя цена материала")
    public PriceResponse latest(@PathVariable Long materialId) {
        ensureMaterial(materialId);
        return priceRepository.findLatestByMaterialId(materialId)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Цен для материала ещё нет: " + materialId));
    }

    @GetMapping("/materials/{materialId}/prices")
    @Operation(summary = "История цен материала (новые сверху)")
    public List<PriceResponse> history(@PathVariable Long materialId) {
        ensureMaterial(materialId);
        return priceRepository.findAllByMaterialIdOrderByFetchedAtDesc(materialId).stream()
                .map(this::toDto)
                .toList();
    }

    private void ensureMaterial(Long materialId) {
        if (!materialRepository.existsById(materialId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Материал не найден: " + materialId);
        }
    }

    private PriceResponse toDto(MaterialPrice p) {
        return new PriceResponse(
                p.getId(), p.getCompanyId(), p.getMaterialId(), p.getExternalProductId(),
                p.getPrice(), p.getCurrency(), p.getSource(), p.getFetchedAt()
        );
    }
}
