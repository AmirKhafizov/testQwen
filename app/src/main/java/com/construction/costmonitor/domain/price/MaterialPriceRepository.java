package com.construction.costmonitor.domain.price;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MaterialPriceRepository extends JpaRepository<MaterialPrice, Long> {

    @Query("""
            select p from MaterialPrice p
            where p.materialId = :materialId
            order by p.fetchedAt desc
            """)
    java.util.List<MaterialPrice> findAllByMaterialIdOrderByFetchedAtDesc(@Param("materialId") Long materialId);

    default Optional<MaterialPrice> findLatestByMaterialId(Long materialId) {
        return findAllByMaterialIdOrderByFetchedAtDesc(materialId).stream().findFirst();
    }
}
