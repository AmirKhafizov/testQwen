package com.construction.costmonitor.api;

import com.construction.costmonitor.api.dto.CatalogDtos.ConfirmMappingRequest;
import com.construction.costmonitor.api.dto.CatalogDtos.CreateExternalProductRequest;
import com.construction.costmonitor.api.dto.CatalogDtos.CreateMappingRequest;
import com.construction.costmonitor.api.dto.CatalogDtos.ExternalProductResponse;
import com.construction.costmonitor.api.dto.CatalogDtos.MappingResponse;
import com.construction.costmonitor.domain.company.CompanyRepository;
import com.construction.costmonitor.domain.external.ExternalProduct;
import com.construction.costmonitor.domain.external.ExternalProductRepository;
import com.construction.costmonitor.domain.mapping.MaterialMapping;
import com.construction.costmonitor.domain.mapping.MaterialMappingRepository;
import com.construction.costmonitor.domain.material.MaterialRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Mappings", description = "Связка материал 1С ↔ товар LemanaPro")
public class MappingController {

    private static final Logger log = LoggerFactory.getLogger(MappingController.class);
    private static final String LEMANA = "LEMANA_PRO";

    private final CompanyRepository companyRepository;
    private final MaterialRepository materialRepository;
    private final ExternalProductRepository externalProductRepository;
    private final MaterialMappingRepository mappingRepository;

    public MappingController(
            CompanyRepository companyRepository,
            MaterialRepository materialRepository,
            ExternalProductRepository externalProductRepository,
            MaterialMappingRepository mappingRepository) {
        this.companyRepository = companyRepository;
        this.materialRepository = materialRepository;
        this.externalProductRepository = externalProductRepository;
        this.mappingRepository = mappingRepository;
    }

    @GetMapping("/companies/{companyId}/mappings")
    @Operation(summary = "Маппинги компании")
    public List<MappingResponse> list(@PathVariable Long companyId) {
        ensureCompany(companyId);
        return mappingRepository.findByCompanyId(companyId).stream().map(this::toDto).toList();
    }

    @PostMapping("/companies/{companyId}/mappings")
    @Operation(summary = "Создать маппинг (по умолчанию CONFIRMED)")
    public ResponseEntity<MappingResponse> create(
            @PathVariable Long companyId,
            @Valid @RequestBody CreateMappingRequest req) {
        ensureCompany(companyId);
        var material = materialRepository.findById(req.materialId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Материал не найден"));
        if (!material.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Материал принадлежит другой компании");
        }
        if (!externalProductRepository.existsById(req.externalProductId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Внешний товар не найден");
        }
        if (mappingRepository.findByMaterialId(req.materialId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "У материала уже есть маппинг");
        }

        MaterialMapping mapping = new MaterialMapping(companyId, req.materialId(), req.externalProductId());
        MaterialMapping.Status status = parseStatus(req.status() != null ? req.status() : "CONFIRMED");
        mapping.setStatus(status);
        mapping.setMatchedBy("MANUAL");
        mapping = mappingRepository.save(mapping);
        log.info("Создан маппинг id={}, материал={}, external={}, статус={}",
                mapping.getId(), req.materialId(), req.externalProductId(), status);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(mapping));
    }

    @PatchMapping("/mappings/{mappingId}/status")
    @Operation(summary = "Подтвердить / отклонить маппинг")
    public MappingResponse updateStatus(
            @PathVariable Long mappingId,
            @Valid @RequestBody ConfirmMappingRequest req) {
        MaterialMapping mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Маппинг не найден"));
        MaterialMapping.Status status = parseStatus(req.status());
        mapping.setStatus(status);
        mapping = mappingRepository.save(mapping);
        log.info("Статус маппинга id={} → {}", mappingId, status);
        return toDto(mapping);
    }

    @PostMapping("/external-products")
    @Operation(summary = "Добавить товар LemanaPro (по артикулу)")
    public ResponseEntity<ExternalProductResponse> createExternal(
            @Valid @RequestBody CreateExternalProductRequest req) {
        String sku = req.externalSku().trim();
        var existing = externalProductRepository.findBySourceAndExternalSku(LEMANA, sku);
        if (existing.isPresent()) {
            return ResponseEntity.ok(toDto(existing.get()));
        }
        ExternalProduct ep = new ExternalProduct(LEMANA, sku, req.name().trim());
        ep.setUnitOfMeasure(req.unitOfMeasure());
        ep.setProductUrl(req.productUrl());
        ep = externalProductRepository.save(ep);
        log.info("Создан внешний товар id={}, sku=[{}]", ep.getId(), sku);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(ep));
    }

    @GetMapping("/external-products")
    @Operation(summary = "Список внешних товаров")
    public List<ExternalProductResponse> listExternal() {
        return externalProductRepository.findAll().stream().map(this::toDto).toList();
    }

    private MaterialMapping.Status parseStatus(String raw) {
        try {
            return MaterialMapping.Status.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Статус должен быть PENDING, CONFIRMED или REJECTED");
        }
    }

    private void ensureCompany(Long companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Компания не найдена: " + companyId);
        }
    }

    private MappingResponse toDto(MaterialMapping m) {
        return new MappingResponse(
                m.getId(), m.getCompanyId(), m.getMaterialId(), m.getExternalProductId(),
                m.getStatus().name(), m.getConfidence(), m.getMatchedBy(), m.getCreatedAt()
        );
    }

    private ExternalProductResponse toDto(ExternalProduct e) {
        return new ExternalProductResponse(
                e.getId(), e.getSource(), e.getExternalSku(), e.getName(),
                e.getUnitOfMeasure(), e.getProductUrl(), e.getLastSyncedAt()
        );
    }
}
