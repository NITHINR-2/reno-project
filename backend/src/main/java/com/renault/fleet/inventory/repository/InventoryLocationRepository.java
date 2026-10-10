package com.renault.fleet.inventory.repository;

import com.renault.fleet.inventory.entity.InventoryLocation;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InventoryLocationRepository extends JpaRepository<InventoryLocation, Long>, JpaSpecificationExecutor<InventoryLocation> {

    @Override
    @EntityGraph(attributePaths = {"region", "plant"})
    Page<InventoryLocation> findAll(Specification<InventoryLocation> spec, Pageable pageable);

    Optional<InventoryLocation> findByCode(String code);
}
