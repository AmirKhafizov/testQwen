package com.construction.costmonitor.domain.material;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Internal material catalog of a construction company (from 1C or manual).
 */
@Entity
@Table(name = "materials", uniqueConstraints = {
        @UniqueConstraint(name = "uk_materials_company_code", columnNames = {"company_id", "code"})
})
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "unit_of_measure", length = 50)
    private String unitOfMeasure;

    @Column(length = 100)
    private String category;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Material() {
    }

    public Material(Long companyId, String code, String name, String unitOfMeasure) {
        this.companyId = companyId;
        this.code = code;
        this.name = name;
        this.unitOfMeasure = unitOfMeasure;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
