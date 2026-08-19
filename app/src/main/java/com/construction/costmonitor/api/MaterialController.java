package com.construction.costmonitor.api;

import com.construction.costmonitor.api.dto.CatalogDtos.CreateMaterialRequest;
import com.construction.costmonitor.api.dto.CatalogDtos.MaterialResponse;
import com.construction.costmonitor.domain.company.CompanyRepository;
import com.construction.costmonitor.domain.material.Material;
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
@RequestMapping("/api/v1/companies/{companyId}/materials")
@Tag(name = "Materials", description = "Внутренние материалы компании (из 1С)")
public class MaterialController {

    private static final Logger log = LoggerFactory.getLogger(MaterialController.class);

    private final CompanyRepository companyRepository;
    private final MaterialRepository materialRepository;

    public MaterialController(CompanyRepository companyRepository, MaterialRepository materialRepository) {
        this.companyRepository = companyRepository;
        this.materialRepository = materialRepository;
    }

    @GetMapping
    @Operation(summary = "Материалы компании")
    public List<MaterialResponse> list(@PathVariable Long companyId) {
        ensureCompany(companyId);
        return materialRepository.findByCompanyIdAndActiveTrue(companyId).stream().map(this::toDto).toList();
    }

    @PostMapping
    @Operation(summary = "Добавить материал")
    public ResponseEntity<MaterialResponse> create(
            @PathVariable Long companyId,
            @Valid @RequestBody CreateMaterialRequest req) {
        ensureCompany(companyId);
        if (materialRepository.existsByCompanyIdAndCode(companyId, req.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Материал с кодом уже есть у компании: " + req.code());
        }
        Material m = new Material(companyId, req.code().trim(), req.name().trim(), req.unitOfMeasure());
        m.setCategory(req.category());
        m = materialRepository.save(m);
        log.info("Создан материал id={}, компания={}, код=«{}»", m.getId(), companyId, m.getCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(m));
    }

    private void ensureCompany(Long companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Компания не найдена: " + companyId);
        }
    }

    private MaterialResponse toDto(Material m) {
        return new MaterialResponse(
                m.getId(), m.getCompanyId(), m.getCode(), m.getName(),
                m.getUnitOfMeasure(), m.getCategory(), m.isActive(), m.getCreatedAt()
        );
    }
}
