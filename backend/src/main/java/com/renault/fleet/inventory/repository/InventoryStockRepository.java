package com.renault.fleet.inventory.repository;

import com.renault.fleet.inventory.entity.InventoryStock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long>, JpaSpecificationExecutor<InventoryStock> {

    /** {@code SELECT ... FOR UPDATE}: must be called inside the allocation transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InventoryStock s where s.id = :id")
    Optional<InventoryStock> findByIdForUpdate(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"location", "variant", "variant.model"})
    Page<InventoryStock> findAll(Specification<InventoryStock> spec, Pageable pageable);

    @Query("""
            select coalesce(sum(s.quantityAvailable), 0) from InventoryStock s
            where s.variant.id = :variantId and s.location.active = true
            """)
    long sumEligibleAvailable(@Param("variantId") Long variantId);

    Optional<InventoryStock> findByLocationIdAndVariantId(Long locationId, Long variantId);
}
