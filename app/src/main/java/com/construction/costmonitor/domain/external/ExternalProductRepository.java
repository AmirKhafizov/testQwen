package com.construction.costmonitor.domain.external;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExternalProductRepository extends JpaRepository<ExternalProduct, Long> {

    Optional<ExternalProduct> findBySourceAndExternalSku(String source, String externalSku);
}
