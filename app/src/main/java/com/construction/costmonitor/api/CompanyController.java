package com.construction.costmonitor.api;

import com.construction.costmonitor.api.dto.CatalogDtos.CompanyResponse;
import com.construction.costmonitor.api.dto.CatalogDtos.CreateCompanyRequest;
import com.construction.costmonitor.domain.company.Company;
import com.construction.costmonitor.domain.company.CompanyRepository;
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
@RequestMapping("/api/v1/companies")
@Tag(name = "Companies", description = "Строительные компании (тенанты)")
public class CompanyController {

    private static final Logger log = LoggerFactory.getLogger(CompanyController.class);

    private final CompanyRepository companyRepository;

    public CompanyController(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @GetMapping
    @Operation(summary = "Список компаний")
    public List<CompanyResponse> list() {
        return companyRepository.findAll().stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Компания по id")
    public CompanyResponse get(@PathVariable Long id) {
        return toDto(find(id));
    }

    @PostMapping
    @Operation(summary = "Создать компанию")
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CreateCompanyRequest req) {
        if (companyRepository.existsByCode(req.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Компания с кодом уже существует: " + req.code());
        }
        Company company = new Company(req.code().trim(), req.name().trim());
        company = companyRepository.save(company);
        log.info("Создана компания id={}, код=«{}»", company.getId(), company.getCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(company));
    }

    private Company find(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Компания не найдена: " + id));
    }

    private CompanyResponse toDto(Company c) {
        return new CompanyResponse(c.getId(), c.getCode(), c.getName(), c.isActive(), c.getCreatedAt());
    }
}
