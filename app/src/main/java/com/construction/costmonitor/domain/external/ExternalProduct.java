package com.construction.costmonitor.domain.external;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Product from external source (LemanaPro).
 */
@Entity
@Table(name = "external_products", uniqueConstraints = {
        @UniqueConstraint(name = "uk_external_products_source_sku", columnNames = {"source", "external_sku"})
})
public class ExternalProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String source; // LEMANA_PRO

    @Column(name = "external_sku", nullable = false, length = 100)
    private String externalSku; // productItem / article

    @Column(nullable = false, length = 1000)
    private String name;

    @Column(name = "unit_of_measure", length = 50)
    private String unitOfMeasure;

    @Column(name = "product_url", length = 1000)
    private String productUrl;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ExternalProduct() {
    }

    public ExternalProduct(String source, String externalSku, String name) {
        this.source = source;
        this.externalSku = externalSku;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getExternalSku() {
        return externalSku;
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

    public String getProductUrl() {
        return productUrl;
    }

    public void setProductUrl(String productUrl) {
        this.productUrl = productUrl;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
