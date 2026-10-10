package com.renault.fleet.catalog.repository;

import com.renault.fleet.catalog.entity.VehicleVariant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleVariantRepository extends JpaRepository<VehicleVariant, Long> {
}
