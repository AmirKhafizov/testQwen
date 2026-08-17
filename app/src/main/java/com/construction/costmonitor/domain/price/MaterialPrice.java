package com.construction.costmonitor.domain.price;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "material_prices")
public class MaterialPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "external_product_id")
    private Long externalProductId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, length = 10)
    private String currency = "RUB";

    @Column(nullable = false, length = 50)
    private String source; // LEMANA_PRO_PARSER, MANUAL

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected MaterialPrice() {
    }

    public MaterialPrice(Long companyId, Long materialId, BigDecimal price, String source) {
        this.companyId = companyId;
        this.materialId = materialId;
        this.price = price;
        this.source = source;
        this.fetchedAt = Instant.now();
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

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getSource() {
        return source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
