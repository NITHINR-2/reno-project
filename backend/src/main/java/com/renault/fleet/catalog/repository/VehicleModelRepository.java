package com.renault.fleet.catalog.repository;

import com.renault.fleet.catalog.entity.VehicleModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {
}
