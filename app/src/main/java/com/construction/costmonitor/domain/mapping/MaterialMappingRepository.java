package com.construction.costmonitor.domain.mapping;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaterialMappingRepository extends JpaRepository<MaterialMapping, Long> {

    Optional<MaterialMapping> findByMaterialId(Long materialId);

    List<MaterialMapping> findByCompanyIdAndStatus(Long companyId, MaterialMapping.Status status);

    List<MaterialMapping> findByCompanyId(Long companyId);
}
