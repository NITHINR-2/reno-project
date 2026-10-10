package com.renault.fleet.catalog.repository;

import com.renault.fleet.catalog.entity.VehicleVariant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleVariantRepository extends JpaRepository<VehicleVariant, Long> {

    @EntityGraph(attributePaths = "model")
    Optional<VehicleVariant> findWithModelById(Long id);

    List<VehicleVariant> findByModelIdInOrderByCode(Collection<Long> modelIds);

    Optional<VehicleVariant> findByCode(String code);
}
