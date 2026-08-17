package com.construction.costmonitor.domain.mapping;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mapping between internal Material (company) and ExternalProduct (LemanaPro).
 */
@Entity
@Table(name = "material_mappings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_material_mappings_material", columnNames = {"material_id"})
})
public class MaterialMapping {

    public enum Status {
        PENDING,
        CONFIRMED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "external_product_id", nullable = false)
    private Long externalProductId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    /** 0.0 - 1.0 confidence of automatic match */
    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "matched_by", length = 50)
    private String matchedBy; // MANUAL, FUZZY, EXACT

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected MaterialMapping() {
    }

    public MaterialMapping(Long companyId, Long materialId, Long externalProductId) {
        this.companyId = companyId;
        this.materialId = materialId;
        this.externalProductId = externalProductId;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public Long getExternalProductId() {
        return externalProductId;
    }

    public void setExternalProductId(Long externalProductId) {
        this.externalProductId = externalProductId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getMatchedBy() {
        return matchedBy;
    }

    public void setMatchedBy(String matchedBy) {
        this.matchedBy = matchedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
