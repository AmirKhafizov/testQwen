package com.construction.costmonitor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CompanyResponse(Long id, String code, String name, boolean active, Instant createdAt) {
    }

    public record CreateCompanyRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 255) String name
    ) {
    }

    public record MaterialResponse(
            Long id,
            Long companyId,
            String code,
            String name,
            String unitOfMeasure,
            String category,
            boolean active,
            Instant createdAt
    ) {
    }

    public record CreateMaterialRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 500) String name,
            @Size(max = 50) String unitOfMeasure,
            @Size(max = 100) String category
    ) {
    }

    public record ExternalProductResponse(
            Long id,
            String source,
            String externalSku,
            String name,
            String unitOfMeasure,
            String productUrl,
            Instant lastSyncedAt
    ) {
    }

    public record CreateExternalProductRequest(
            @NotBlank @Size(max = 100) String externalSku,
            @NotBlank @Size(max = 1000) String name,
            @Size(max = 50) String unitOfMeasure,
            @Size(max = 1000) String productUrl
    ) {
    }

    public record MappingResponse(
            Long id,
            Long companyId,
            Long materialId,
            Long externalProductId,
            String status,
            BigDecimal confidence,
            String matchedBy,
            Instant createdAt
    ) {
    }

    public record CreateMappingRequest(
            @NotNull Long materialId,
            @NotNull Long externalProductId,
            /** PENDING | CONFIRMED | REJECTED */
            String status
    ) {
    }

    public record ConfirmMappingRequest(
            /** PENDING | CONFIRMED | REJECTED */
            @NotBlank String status
    ) {
    }

    public record PriceResponse(
            Long id,
            Long companyId,
            Long materialId,
            Long externalProductId,
            BigDecimal price,
            String currency,
            String source,
            Instant fetchedAt
    ) {
    }
}
