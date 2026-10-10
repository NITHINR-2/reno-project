package com.renault.fleet.catalog.repository;

import com.renault.fleet.catalog.entity.VehicleModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long>, JpaSpecificationExecutor<VehicleModel> {

    Optional<VehicleModel> findByCode(String code);
}
